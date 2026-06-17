/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.verification.signatures

import com.google.common.collect.ImmutableList
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.gradle.cache.FileLockManager
import org.gradle.cache.IndexedCache
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.internal.ProducerGuard
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.ListSerializer
import org.gradle.security.internal.Fingerprint
import org.gradle.security.internal.Fingerprint.Companion.of
import org.gradle.security.internal.Fingerprint.Companion.wrap
import org.gradle.security.internal.PublicKeyResultBuilder
import org.gradle.security.internal.PublicKeyService
import org.gradle.security.internal.SecuritySupport.toLongIdHexString
import org.gradle.util.internal.BuildCommencedTimeProvider
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.EOFException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

class CrossBuildCachingKeyService(
    cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
    decoratorFactory: InMemoryCacheDecoratorFactory,
    private val buildOperationRunner: BuildOperationRunner,
    private val delegate: PublicKeyService,
    private val timeProvider: BuildCommencedTimeProvider,
    private val refreshKeys: Boolean
) : PublicKeyService, Closeable {
    private val cache: PersistentCache
    private val publicKeyRings: IndexedCache<Fingerprint?, CacheEntry<PGPPublicKeyRing?>?>

    // Some long key Id may have collisions. This is extremely unlikely but if it happens, we know how to workaround
    private val longIdToFingerprint: IndexedCache<Long?, CacheEntry<MutableList<Fingerprint>?>?>
    private val fingerPrintguard: ProducerGuard<Fingerprint?> = ProducerGuard.adaptive<Fingerprint?>()
    private val longIdGuard: ProducerGuard<Long?> = ProducerGuard.adaptive<Long?>()

    init {
        cache = cacheBuilderFactory
            .createCrossVersionCacheBuilder("keyrings")
            .withInitialLockMode(FileLockManager.LockMode.OnDemand)
            .open()
        val fingerprintSerializer = FingerprintSerializer()
        val keyringParams: IndexedCacheParameters<Fingerprint?, CacheEntry<PGPPublicKeyRing?>?> = IndexedCacheParameters.of<Fingerprint?, CacheEntry<PGPPublicKeyRing?>?>(
            "publickeyrings",
            fingerprintSerializer,
            PublicKeyRingCacheEntrySerializer()
        ).withCacheDecorator(
            decoratorFactory.decorator(2000, true)
        )
        publicKeyRings = cache.createIndexedCache<Fingerprint?, CacheEntry<PGPPublicKeyRing?>?>(keyringParams)

        val mappingParameters: IndexedCacheParameters<Long?, CacheEntry<MutableList<Fingerprint?>?>?> = IndexedCacheParameters.of<Long?, CacheEntry<MutableList<Fingerprint?>?>?>(
            "keymappings",
            BaseSerializerFactory.LONG_SERIALIZER,
            FingerprintListCacheEntrySerializer(ListSerializer<Fingerprint?>(fingerprintSerializer))
        ).withCacheDecorator(
            decoratorFactory.decorator(2000, true)
        )
        longIdToFingerprint = cache.createIndexedCache<Long?, CacheEntry<MutableList<Fingerprint?>?>?>(mappingParameters)
    }

    override fun close() {
        cache.close()
    }

    private fun hasExpired(key: CacheEntry<*>): Boolean {
        if (key.value != null) {
            // if a key was found in the cache, it's permanent
            return false
        }
        val elapsed = timeProvider.getCurrentTime() - key.timestamp
        return refreshKeys || elapsed > MISSING_KEY_TIMEOUT
    }

    override fun findByLongId(keyId: Long, builder: PublicKeyResultBuilder) {
        longIdGuard.guardByKey<Any?>(keyId, Supplier {
            val fingerprints = longIdToFingerprint.getIfPresent(keyId)
            if (fingerprints == null || hasExpired(fingerprints)) {
                buildOperationRunner.run(object : RunnableBuildOperation {
                    override fun run(context: BuildOperationContext) {
                        val currentTime = timeProvider.getCurrentTime()
                        val missing = AtomicBoolean(true)
                        delegate.findByLongId(keyId, object : PublicKeyResultBuilder {
                            override fun keyRing(keyring: PGPPublicKeyRing) {
                                missing.set(false)
                                builder.keyRing(keyring)
                                val pkIt = keyring.getPublicKeys()
                                while (pkIt.hasNext()) {
                                    val publicKey = pkIt.next()
                                    val fingerprint = of(publicKey)
                                    publicKeyRings.put(fingerprint, CacheEntry<PGPPublicKeyRing?>(currentTime, keyring))
                                    updateLongKeyIndex(fingerprint, keyId)
                                }
                            }

                            override fun publicKey(publicKey: PGPPublicKey) {
                                missing.set(false)
                                if (publicKey.getKeyID() == keyId) {
                                    builder.publicKey(publicKey)
                                }
                            }
                        })
                        if (missing.get()) {
                            longIdToFingerprint.put(keyId, CacheEntry<MutableList<Fingerprint?>?>(currentTime, null))
                        }
                    }

                    override fun description(): BuildOperationDescriptor.Builder {
                        return@guardByKey BuildOperationDescriptor.displayName("Fetching public key")
                            .progressDisplayName("Downloading public key " + toLongIdHexString(keyId))
                    }
                })
            } else {
                if (fingerprints.value != null) {
                    for (fingerprint in fingerprints.value) {
                        findByFingerprint(fingerprint.bytes, object : PublicKeyResultBuilder {
                            override fun keyRing(keyring: PGPPublicKeyRing?) {
                                builder.keyRing(keyring)
                            }

                            override fun publicKey(publicKey: PGPPublicKey) {
                                if (publicKey.getKeyID() == keyId) {
                                    builder.publicKey(publicKey)
                                }
                            }
                        })
                    }
                }
            }
            null
        })
    }

    private fun updateLongKeyIndex(fingerprint: Fingerprint, keyId: Long) {
        val fprints = longIdToFingerprint.getIfPresent(keyId)
        val currentTime = timeProvider.getCurrentTime()
        if (fprints == null) {
            longIdToFingerprint.put(keyId, CacheEntry<MutableList<Fingerprint?>?>(currentTime, mutableListOf<Fingerprint?>(fingerprint)))
        } else {
            longIdToFingerprint.remove(keyId)
            val list = ImmutableList.builderWithExpectedSize<Fingerprint?>(1 + fprints.value!!.size)
            list.addAll(fprints.value)
            list.add(fingerprint)
            longIdToFingerprint.put(keyId, CacheEntry<MutableList<Fingerprint?>?>(currentTime, list.build()))
        }
    }

    override fun findByFingerprint(bytes: ByteArray, builder: PublicKeyResultBuilder) {
        val fingerprint = wrap(bytes)
        fingerPrintguard.guardByKey<Any?>(fingerprint, Supplier {
            var cacheEntry = publicKeyRings.getIfPresent(fingerprint)
            if (cacheEntry == null || hasExpired(cacheEntry)) {
                val keyResultBuilder: LookupPublicKeyResultBuilder = CrossBuildCachingKeyService.LookupPublicKeyResultBuilder()
                delegate.findByFingerprint(bytes, keyResultBuilder)
                cacheEntry = keyResultBuilder.entry
            }
            if (cacheEntry != null) {
                builder.keyRing(cacheEntry.value)
                val pkIt = cacheEntry.value!!.getPublicKeys()
                while (pkIt.hasNext()) {
                    val publicKey = pkIt.next()
                    if (publicKey.getFingerprint().contentEquals(bytes)) {
                        builder.publicKey(publicKey)
                    }
                }
            }
            null
        })
    }

    private class PublicKeyRingCacheEntrySerializer : AbstractSerializer<CacheEntry<PGPPublicKeyRing?>?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): CacheEntry<PGPPublicKeyRing?> {
            val timestamp = decoder.readLong()
            val present = decoder.readBoolean()
            if (present) {
                val encoded = decoder.readBinary()
                val objectFactory = PGPObjectFactory(
                    PGPUtil.getDecoderStream(ByteArrayInputStream(encoded)), BcKeyFingerprintCalculator()
                )
                val `object` = objectFactory.nextObject()
                if (`object` is PGPPublicKeyRing) {
                    return CacheEntry<PGPPublicKeyRing?>(timestamp, `object`)
                }
                throw IllegalStateException("Unexpected key in cache: " + `object`.javaClass)
            }
            return CacheEntry<PGPPublicKeyRing?>(timestamp, null)
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: CacheEntry<PGPPublicKeyRing?>) {
            encoder.writeLong(value.timestamp)
            val key = value.value
            if (key != null) {
                encoder.writeBoolean(true)
                encoder.writeBinary(key.getEncoded())
            } else {
                encoder.writeBoolean(false)
            }
        }
    }

    private class CacheEntry<T>(private val timestamp: Long, private val value: T?)

    private class FingerprintSerializer : AbstractSerializer<Fingerprint?>() {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): Fingerprint {
            return Fingerprint.wrap(decoder.readBinary()!!)
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: Fingerprint) {
            encoder.writeBinary(value.bytes)
        }
    }

    private inner class LookupPublicKeyResultBuilder : PublicKeyResultBuilder {
        var entry: CacheEntry<PGPPublicKeyRing?>? = null

        override fun keyRing(keyring: PGPPublicKeyRing) {
            entry = CacheEntry<PGPPublicKeyRing?>(timeProvider.getCurrentTime(), keyring)
            val pkIt = keyring.getPublicKeys()
            while (pkIt.hasNext()) {
                val publicKey = pkIt.next()
                val fingerprint = of(publicKey)
                val keyID = publicKey.getKeyID()
                updateLongKeyIndex(fingerprint, keyID)
            }
        }

        override fun publicKey(publicKey: PGPPublicKey?) {
        }
    }

    private class FingerprintListCacheEntrySerializer(private val listSerializer: ListSerializer<Fingerprint?>) : AbstractSerializer<CacheEntry<MutableList<Fingerprint?>?>?>() {
        @Throws(EOFException::class, Exception::class)
        override fun read(decoder: Decoder): CacheEntry<MutableList<Fingerprint?>?> {
            val timestamp = decoder.readLong()
            val fingerprints: MutableList<Fingerprint?>? = if (decoder.readBoolean()) listSerializer.read(decoder) else null
            return CacheEntry<MutableList<Fingerprint?>?>(timestamp, fingerprints)
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: CacheEntry<MutableList<Fingerprint?>?>) {
            encoder.writeLong(value.timestamp)
            val fingerprints = value.value
            if (fingerprints == null) {
                encoder.writeBoolean(false)
            } else {
                encoder.writeBoolean(true)
                listSerializer.write(encoder, fingerprints)
            }
        }
    }

    companion object {
        val MISSING_KEY_TIMEOUT: Long = TimeUnit.MILLISECONDS.convert(24, TimeUnit.HOURS)
    }
}
