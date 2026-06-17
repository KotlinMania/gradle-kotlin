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
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSignatureList
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory
import org.gradle.authentication.Authentication
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.BuildScopedCacheBuilderFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.Hashing
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.verifier.HttpRedirectVerifier
import org.gradle.security.internal.EmptyPublicKeyService
import org.gradle.security.internal.Fingerprint.Companion.of
import org.gradle.security.internal.InvalidSignatureFileException
import org.gradle.security.internal.PublicKeyDownloadService
import org.gradle.security.internal.PublicKeyResultBuilder
import org.gradle.security.internal.PublicKeyService
import org.gradle.security.internal.SecuritySupport.readSignatures
import org.gradle.security.internal.SecuritySupport.toLongIdHexString
import org.gradle.security.internal.SecuritySupport.verify
import org.gradle.util.internal.BuildCommencedTimeProvider
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

@ServiceScope(Scope.Build::class)
class DefaultSignatureVerificationServiceFactory(
    private val transportFactory: RepositoryTransportFactory,
    private val globalScopedCacheBuilderFactory: GlobalScopedCacheBuilderFactory,
    private val decoratorFactory: InMemoryCacheDecoratorFactory,
    private val buildOperationRunner: BuildOperationRunner?,
    private val fileHasher: FileHasher,
    private val buildScopedCacheBuilderFactory: BuildScopedCacheBuilderFactory,
    private val timeProvider: BuildCommencedTimeProvider?,
    private val refreshKeys: Boolean,
    private val fileResourceListener: FileResourceListener
) : SignatureVerificationServiceFactory {
    override fun create(keyrings: BuildTreeDefinedKeys, keyServers: MutableList<URI?>, useKeyServers: Boolean): SignatureVerificationService {
        val refreshKeys = this.refreshKeys || !useKeyServers
        val repository = transportFactory.createTransport("https", "https", mutableListOf<Authentication?>(), HttpRedirectVerifier { redirectLocations: MutableCollection<URI?>? -> }).repository
        var keyService: PublicKeyService
        if (useKeyServers) {
            val keyDownloadService = PublicKeyDownloadService(ImmutableList.copyOf<URI?>(keyServers), repository)
            keyService = CrossBuildCachingKeyService(globalScopedCacheBuilderFactory, decoratorFactory, buildOperationRunner, keyDownloadService, timeProvider, refreshKeys)
        } else {
            keyService = EmptyPublicKeyService.getInstance()
        }
        keyService = keyrings.applyTo(keyService)
        val effectiveKeyringsFile = keyrings.getEffectiveKeyringsFile()
        val keyringFileHash = if (observed(effectiveKeyringsFile)!!.exists())
            fileHasher.hash(effectiveKeyringsFile!!)
        else
            NO_KEYRING_FILE_HASH
        val delegate = DefaultSignatureVerificationService(keyService)
        return CrossBuildSignatureVerificationService(
            delegate,
            fileHasher,
            buildScopedCacheBuilderFactory,
            decoratorFactory,
            timeProvider,
            refreshKeys,
            useKeyServers,
            keyringFileHash
        )
    }

    private fun observed(file: File?): File? {
        fileResourceListener.fileObserved(file)
        return file
    }

    private class DefaultSignatureVerificationService(private val keyService: PublicKeyService) : SignatureVerificationService {
        override fun verify(origin: File, signature: File, trustedKeys: MutableSet<String?>, ignoredKeys: MutableSet<String?>, result: SignatureVerificationResultBuilder) {
            val pgpSignatures: PGPSignatureList?
            try {
                pgpSignatures = readSignatures(signature)
            } catch (e: InvalidSignatureFileException) {
                val cause: Throwable = (if (e.cause != null) e.cause else e)!!
                result.failedToReadSignatureFile(cause.javaClass.getSimpleName() + ": " + cause.message)
                return
            }
            if (pgpSignatures == null) {
                result.noSignatures()
                return
            }
            for (pgpSignature in pgpSignatures) {
                val longIdKey = toLongIdHexString(pgpSignature.getKeyID())
                if (ignoredKeys.contains(longIdKey)) {
                    result.ignored(longIdKey)
                    continue
                }
                val missing = AtomicBoolean(true)
                keyService.findByLongId(pgpSignature.getKeyID(), object : PublicKeyResultBuilder {
                    override fun keyRing(keyring: PGPPublicKeyRing?) {
                    }

                    override fun publicKey(pgpPublicKey: PGPPublicKey) {
                        missing.set(false)
                        val fingerprint = of(pgpPublicKey).toString()
                        if (ignoredKeys.contains(fingerprint)) {
                            result.ignored(fingerprint)
                            return
                        }
                        try {
                            val verified = verify(origin, pgpSignature, pgpPublicKey)
                            if (!verified) {
                                result.failed(pgpPublicKey)
                            } else {
                                val trusted = trustedKeys.contains(fingerprint) || trustedKeys.contains(toLongIdHexString(pgpPublicKey.getKeyID()))
                                result.verified(pgpPublicKey, trusted)
                            }
                        } catch (e: PGPException) {
                            throw throwAsUncheckedException(e)
                        }
                    }
                })
                if (missing.get()) {
                    result.missingKey(longIdKey)
                }
            }
        }

        override fun getPublicKeyService(): PublicKeyService {
            return keyService
        }

        override fun stop() {
            try {
                keyService.close()
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
        }
    }

    companion object {
        private val NO_KEYRING_FILE_HASH = Hashing.signature(DefaultSignatureVerificationServiceFactory::class.java)
    }
}
