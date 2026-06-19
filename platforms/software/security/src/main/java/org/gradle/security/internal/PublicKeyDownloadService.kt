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
package org.gradle.security.internal

import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.KeyFingerPrintCalculator
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.time.ExponentialBackoff
import org.gradle.internal.time.ExponentialBackoff.Companion.of
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException
import java.util.ArrayDeque
import java.util.Collections
import java.util.Deque
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class PublicKeyDownloadService(private val keyServers: MutableList<URI>, private val client: ExternalResourceRepository) : PublicKeyService {
    override fun findByLongId(keyId: Long, builder: PublicKeyResultBuilder) {
        val servers: MutableList<URI> = ArrayList(keyServers)
        Collections.shuffle(servers)
        tryDownloadKeyFromServer(SecuritySupport.toLongIdHexString(keyId), servers, builder, Consumer { keyring: PGPPublicKeyRing -> findMatchingKey(keyId, keyring, builder) })
    }

    override fun findByFingerprint(fingerprint: ByteArray, builder: PublicKeyResultBuilder) {
        val servers: MutableList<URI> = ArrayList(keyServers)
        Collections.shuffle(servers)
        tryDownloadKeyFromServer(Fingerprint.wrap(fingerprint).toString(), servers, builder, Consumer { keyring: PGPPublicKeyRing -> findMatchingKey(fingerprint, keyring, builder) })
    }

    private fun tryDownloadKeyFromServer(fingerprint: String, baseUris: MutableList<URI>, builder: PublicKeyResultBuilder, onKeyring: Consumer<in PGPPublicKeyRing>) {
        val serversLeft: Deque<URI> = ArrayDeque(baseUris)
        try {
            val backoff: ExponentialBackoff<ExponentialBackoff.Signal> = of(5, TimeUnit.SECONDS, 50, TimeUnit.MILLISECONDS)
            backoff.retryUntil(object : ExponentialBackoff.Query<Boolean?> {
                override fun run(): ExponentialBackoff.Result<Boolean?> {
                    val baseUri = serversLeft.poll()
                    if (baseUri == null) {
                        // no more servers left despite retries
                        return ExponentialBackoff.Result.successful(false)
                    }
                    try {
                        val query = toQuery(baseUri, fingerprint)
                        val response = client.resource(query).withContentIfPresent(ExternalResource.ContentAction { inputStream: InputStream ->
                            extractKeyRing(inputStream, builder, onKeyring)
                            ExponentialBackoff.Result.successful(true)
                        })
                        if (response != null) {
                            return response.result!!
                        } else {
                            logKeyDownloadAttempt(fingerprint, baseUri)
                            // null means the resource is missing from this repo
                        }
                    } catch (e: Exception) {
                        logKeyDownloadAttempt(fingerprint, baseUri)
                        // add for retry
                        serversLeft.add(baseUri)
                    }
                    return ExponentialBackoff.Result.notSuccessful(false)
                }
            })
        } catch (e: InterruptedException) {
            throw throwAsUncheckedException(e)
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    /**
     * A response was sent from the server. This is a keyring, we need to find
     * within the keyring the matching key.
     */
    private fun findMatchingKey(id: Long, keyRing: PGPPublicKeyRing, builder: PublicKeyResultBuilder) {
        for (publicKey in keyRing) {
            if (publicKey.getKeyID() == id) {
                builder.publicKey(publicKey)
                return
            }
        }
    }

    /**
     * A response was sent from the server. This is a keyring, we need to find
     * within the keyring the matching key.
     */
    private fun findMatchingKey(fingerprint: ByteArray, keyRing: PGPPublicKeyRing, builder: PublicKeyResultBuilder) {
        for (publicKey in keyRing) {
            if (publicKey.getFingerprint().contentEquals(fingerprint)) {
                builder.publicKey(publicKey)
                return
            }
        }
    }

    @Throws(IOException::class)
    private fun extractKeyRing(stream: InputStream, builder: PublicKeyResultBuilder, onKeyring: Consumer<in PGPPublicKeyRing>) {
        PGPUtil.getDecoderStream(stream).use { decoderStream ->
            val fingerprintCalculator: KeyFingerPrintCalculator = BcKeyFingerprintCalculator()
            val objectFactory = PGPObjectFactory(decoderStream, fingerprintCalculator)
            val keyring = objectFactory.nextObject() as PGPPublicKeyRing

            val strippedKeyRing = KeyringStripper.strip(keyring, fingerprintCalculator)

            onKeyring.accept(strippedKeyRing)
            builder.keyRing(strippedKeyRing)
        }
    }

    @Throws(URISyntaxException::class)
    private fun toQuery(baseUri: URI, fingerprint: String): ExternalResourceName {
        var scheme = baseUri.getScheme()
        var port = baseUri.getPort()
        if ("hkp" == scheme) {
            scheme = "http"
            port = 11371
        }
        return ExternalResourceName(URI(scheme, null, baseUri.getHost(), port, "/pks/lookup", "op=get&options=mr&search=0x" + fingerprint, null))
    }

    override fun close() {
    }

    companion object {
        private val LOGGER = getLogger(PublicKeyDownloadService::class.java)

        private fun logKeyDownloadAttempt(fingerprint: String, baseUri: URI) {
            LOGGER!!.debug("Cannot download public key " + fingerprint + " from " + baseUri.getHost())
        }
    }
}
