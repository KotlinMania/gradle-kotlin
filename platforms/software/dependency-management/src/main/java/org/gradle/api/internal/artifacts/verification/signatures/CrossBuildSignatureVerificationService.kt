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

import org.bouncycastle.openpgp.PGPPublicKey
import org.gradle.api.internal.cache.StringInterner
import org.gradle.cache.FileLockManager
import org.gradle.cache.IndexedCache
import org.gradle.cache.IndexedCacheParameters
import org.gradle.cache.PersistentCache
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.BuildScopedCacheBuilderFactory
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.HashCodeSerializer
import org.gradle.internal.serialize.InterningStringSerializer
import org.gradle.internal.serialize.SetSerializer
import org.gradle.security.internal.PublicKeyService
import org.gradle.util.internal.BuildCommencedTimeProvider
import java.io.File
import java.lang.Boolean
import kotlin.Any
import kotlin.Exception
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Throws

class CrossBuildSignatureVerificationService(
    private val delegate: SignatureVerificationService,
    private val fileHasher: FileHasher,
    cacheBuilderFactory: BuildScopedCacheBuilderFactory,
    inMemoryCacheDecoratorFactory: InMemoryCacheDecoratorFactory,
    private val timeProvider: BuildCommencedTimeProvider,
    private val refreshKeys: Boolean,
    private val useKeyServers: Boolean,
    private val keyringFileHash: HashCode
) : SignatureVerificationService {
    private val store: PersistentCache
    private val cache: IndexedCache<CacheKey?, CacheEntry?>

    init {
        store = cacheBuilderFactory.createCacheBuilder("signature-verification")
            .withDisplayName("Signature verification cache")
            .withInitialLockMode(FileLockManager.LockMode.OnDemand)
            .open()
        val stringSerializer = InterningStringSerializer(StringInterner())
        cache = store.createIndexedCache<CacheKey?, CacheEntry?>(
            IndexedCacheParameters.of<CacheKey?, CacheEntry?>(
                "signature-verification",
                CacheKeySerializer(stringSerializer, SetSerializer<String?>(stringSerializer)),
                CacheEntrySerializer(stringSerializer)
            ).withCacheDecorator(inMemoryCacheDecoratorFactory.decorator(500, true))
        )
    }

    override fun verify(origin: File, signature: File, trustedKeys: MutableSet<String?>, ignoredKeys: MutableSet<String?>, builder: SignatureVerificationResultBuilder) {
        val cacheKey = CacheKey(origin.getAbsolutePath(), signature.getAbsolutePath(), trustedKeys, ignoredKeys, useKeyServers, keyringFileHash)
        val originHash = fileHasher.hash(origin)
        val signatureHash = fileHasher.hash(signature)
        var entry = cache.getIfPresent(cacheKey)
        if (entry == null || entry.updated(originHash, signatureHash) || hasExpired(entry)) {
            entry = performActualVerification(origin, signature, trustedKeys, ignoredKeys, originHash, signatureHash)
            cache.put(cacheKey, entry)
        }
        entry.applyTo(builder)
    }

    private fun hasExpired(entry: CacheEntry): Boolean {
        val missingKeys = entry.missingKeys
        if (missingKeys == null || missingKeys.isEmpty()) {
            return false
        }
        val elapsed = timeProvider.getCurrentTime() - entry.timestamp
        return refreshKeys || elapsed > CrossBuildCachingKeyService.Companion.MISSING_KEY_TIMEOUT
    }

    override fun getPublicKeyService(): PublicKeyService? {
        return delegate.getPublicKeyService()
    }

    private fun performActualVerification(
        origin: File?,
        signature: File?,
        trustedKeys: MutableSet<String?>?,
        ignoredKeys: MutableSet<String?>?,
        originHash: HashCode,
        signatureHash: HashCode
    ): CacheEntry {
        val result = CacheEntryBuilder(timeProvider.getCurrentTime(), originHash, signatureHash)
        delegate.verify(origin, signature, trustedKeys, ignoredKeys, result)
        return result.build()
    }

    override fun stop() {
        delegate.stop()
        store.close()
    }

    private class CacheKey(
        private val filePath: String,
        private val signaturePath: String,
        private val trustedKeys: MutableSet<String?>,
        private val ignoredKeys: MutableSet<String?>,
        private val useKeyServers: Boolean,
        private val keyringFileHash: HashCode
    ) {
        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val cacheKey = o as CacheKey

            if (filePath != cacheKey.filePath) {
                return false
            }
            if (signaturePath != cacheKey.signaturePath) {
                return false
            }
            if (trustedKeys != cacheKey.trustedKeys) {
                return false
            }
            if (ignoredKeys != cacheKey.ignoredKeys) {
                return false
            }
            if (useKeyServers != cacheKey.useKeyServers) {
                return false
            }
            return keyringFileHash == cacheKey.keyringFileHash
        }

        override fun hashCode(): Int {
            var result = filePath.hashCode()
            result = 31 * result + signaturePath.hashCode()
            result = 31 * result + trustedKeys.hashCode()
            result = 31 * result + ignoredKeys.hashCode()
            result = 31 * result + Boolean.hashCode(useKeyServers)
            result = 31 * result + keyringFileHash.hashCode()
            return result
        }
    }

    private class CacheKeySerializer(private val delegate: InterningStringSerializer, private val setSerializer: SetSerializer<String?>) : AbstractSerializer<CacheKey?>() {
        private val hashCodeSerializer: HashCodeSerializer

        init {
            this.hashCodeSerializer = HashCodeSerializer()
        }

        @Throws(Exception::class)
        override fun read(decoder: Decoder): CacheKey {
            return CrossBuildSignatureVerificationService.CacheKey(
                delegate.read(decoder),
                delegate.read(decoder),
                setSerializer.read(decoder)!!,
                setSerializer.read(decoder)!!,
                decoder.readBoolean(),
                hashCodeSerializer.read(decoder)
            )
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: CacheKey) {
            delegate.write(encoder, value.filePath)
            delegate.write(encoder, value.signaturePath)
            setSerializer.write(encoder, value.trustedKeys)
            setSerializer.write(encoder, value.ignoredKeys)
            encoder.writeBoolean(value.useKeyServers)
            hashCodeSerializer.write(encoder, value.keyringFileHash)
        }
    }

    private class CacheEntryBuilder(private val timestamp: Long, private val originHash: HashCode, private val signatureHash: HashCode) : SignatureVerificationResultBuilder {
        private var missingKeys: MutableList<String?>? = null
        private var trustedKeys: MutableList<PGPPublicKey>? = null
        private var validKeys: MutableList<PGPPublicKey>? = null
        private var failedKeys: MutableList<PGPPublicKey>? = null
        private var ignoredKeys: MutableList<String?>? = null
        private var hasNoSignatures = false
        private var corruptionError: String? = null

        override fun missingKey(keyId: String?) {
            if (missingKeys == null) {
                missingKeys = ArrayList<String?>()
            }
            missingKeys!!.add(keyId)
        }

        override fun verified(key: PGPPublicKey?, trusted: kotlin.Boolean) {
            if (trusted) {
                if (trustedKeys == null) {
                    trustedKeys = ArrayList<PGPPublicKey>()
                }
                trustedKeys!!.add(key!!)
            } else {
                if (validKeys == null) {
                    validKeys = ArrayList<PGPPublicKey>()
                }
                validKeys!!.add(key!!)
            }
        }

        override fun failed(pgpPublicKey: PGPPublicKey?) {
            if (failedKeys == null) {
                failedKeys = ArrayList<PGPPublicKey>()
            }
            failedKeys!!.add(pgpPublicKey!!)
        }

        override fun ignored(keyId: String?) {
            if (ignoredKeys == null) {
                ignoredKeys = ArrayList<String?>()
            }
            ignoredKeys!!.add(keyId)
        }

        override fun noSignatures() {
            hasNoSignatures = true
        }

        override fun failedToReadSignatureFile(causeDescription: String?) {
            corruptionError = causeDescription
        }

        fun build(): CacheEntry {
            return CacheEntry(timestamp, originHash, signatureHash, missingKeys, trustedKeys, validKeys, failedKeys, ignoredKeys, hasNoSignatures, corruptionError)
        }
    }

    private class CacheEntry(
        private val timestamp: Long,
        private val originHash: HashCode,
        private val signatureHash: HashCode,
        private val missingKeys: MutableList<String?>?,
        private val trustedKeys: MutableList<PGPPublicKey>?,
        private val validKeys: MutableList<PGPPublicKey>?,
        private val failedKeys: MutableList<PGPPublicKey>?,
        private val ignoredKeys: MutableList<String?>?,
        private val hasNoSignatures: kotlin.Boolean,
        private val corruptionError: String?
    ) {
        fun applyTo(builder: SignatureVerificationResultBuilder) {
            if (missingKeys != null) {
                for (missingKey in missingKeys) {
                    builder.missingKey(missingKey)
                }
            }
            if (trustedKeys != null) {
                for (trustedKey in trustedKeys) {
                    builder.verified(trustedKey, true)
                }
            }
            if (validKeys != null) {
                for (validKey in validKeys) {
                    builder.verified(validKey, false)
                }
            }
            if (failedKeys != null) {
                for (failedKey in failedKeys) {
                    builder.failed(failedKey)
                }
            }
            if (ignoredKeys != null) {
                for (ignoredKey in ignoredKeys) {
                    builder.ignored(ignoredKey)
                }
            }
            if (hasNoSignatures) {
                builder.noSignatures()
            }
            if (corruptionError != null) {
                builder.failedToReadSignatureFile(corruptionError)
            }
        }

        fun updated(originHash: HashCode?, signatureHash: HashCode?): kotlin.Boolean {
            return this.originHash != originHash || this.signatureHash != signatureHash
        }
    }

    private class CacheEntrySerializer(private val stringSerializer: InterningStringSerializer) : AbstractSerializer<CacheEntry?>() {
        private val publicKeySerializer = PublicKeySerializer()

        @Throws(Exception::class)
        override fun read(decoder: Decoder): CacheEntry {
            val timestamp = decoder.readLong()
            val originHash = HashCode.fromBytes(decoder.readBinary()!!)
            val signatureHash = HashCode.fromBytes(decoder.readBinary()!!)
            val missingKeys = readStringKeys(decoder)
            val trustedKeys = readKeys(decoder)
            val validKeys = readKeys(decoder)
            val failedKeys = readKeys(decoder)
            val ignoredKeys = readStringKeys(decoder)
            val hasNoSignatures = decoder.readBoolean()
            val corruptionError = decoder.readNullableString()
            return CacheEntry(timestamp, originHash, signatureHash, missingKeys, trustedKeys, validKeys, failedKeys, ignoredKeys, hasNoSignatures, corruptionError)
        }

        @Throws(Exception::class)
        fun readStringKeys(decoder: Decoder): MutableList<String?>? {
            val missingKeysLen = decoder.readSmallInt()
            var missingKeys: MutableList<String?>? = null
            if (missingKeysLen > 0) {
                missingKeys = ArrayList<String?>(missingKeysLen)
                for (i in 0..<missingKeysLen) {
                    missingKeys.add(stringSerializer.read(decoder))
                }
            }
            return missingKeys
        }

        @Throws(Exception::class)
        fun readKeys(decoder: Decoder): MutableList<PGPPublicKey>? {
            val len = decoder.readSmallInt()
            var keys: MutableList<PGPPublicKey>? = null
            if (len > 0) {
                keys = ArrayList<PGPPublicKey>(len)
                for (i in 0..<len) {
                    keys.add(publicKeySerializer.read(decoder)!!)
                }
            }
            return keys
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: CacheEntry) {
            encoder.writeLong(value.timestamp)
            encoder.writeBinary(value.originHash.toByteArray())
            encoder.writeBinary(value.signatureHash.toByteArray())
            writeStringKeys(encoder, value.missingKeys)
            writeKeys(encoder, value.trustedKeys)
            writeKeys(encoder, value.validKeys)
            writeKeys(encoder, value.failedKeys)
            writeStringKeys(encoder, value.ignoredKeys)
            encoder.writeBoolean(value.hasNoSignatures)
            encoder.writeNullableString(value.corruptionError)
        }

        @Throws(Exception::class)
        fun writeStringKeys(encoder: Encoder, keys: MutableList<String?>?) {
            if (keys == null) {
                encoder.writeSmallInt(0)
            } else {
                encoder.writeSmallInt(keys.size)
                for (key in keys) {
                    stringSerializer.write(encoder, key)
                }
            }
        }

        @Throws(Exception::class)
        fun writeKeys(encoder: Encoder, keys: MutableList<PGPPublicKey>?) {
            if (keys == null) {
                encoder.writeSmallInt(0)
            } else {
                encoder.writeSmallInt(keys.size)
                for (key in keys) {
                    publicKeySerializer.write(encoder, key)
                }
            }
        }
    }
}
