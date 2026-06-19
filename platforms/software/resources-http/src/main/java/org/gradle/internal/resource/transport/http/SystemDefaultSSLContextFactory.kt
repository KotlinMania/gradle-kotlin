/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.internal.resource.transport.http

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.jspecify.annotations.NullMarked
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

/**
 * This class contains SSLContext initialization similar to what SSLContext.getDefault() does.
 * Unfortunately, SSLContext.getDefault() can not be used because it is heavily cached by the JVM,
 * including keyStore and trustStore managers.
 * There is no way to reset these caches because they are private and are initialized in static sections.
 */
@NullMarked
object SystemDefaultSSLContextFactory {
    private const val NONE = "NONE"
    private const val P11KEYSTORE = "PKCS11"

    private val LOGGER: Logger = getLogger(SystemDefaultSSLContextFactory::class.java)!!

    @JvmStatic
    @Throws(Exception::class)
    fun create(): SSLContext {
        val context = SSLContext.getInstance("TLS")
        context.init(
            keyManagers,
            trustManagers, null
        )
        return context
    }

    @get:Throws(Exception::class)
    private val keyManagers: Array<KeyManager>
        get() {
            val keyStorePath = System.getProperty("javax.net.ssl.keyStore", "")
            val keyStoreType = System.getProperty("javax.net.ssl.keyStoreType", KeyStore.getDefaultType())
            val keyStoreProvider = System.getProperty("javax.net.ssl.keyStoreProvider", "")
            val keyStorePasswordString = System.getProperty("javax.net.ssl.keyStorePassword", "")

            require(!(P11KEYSTORE == keyStoreType && NONE != keyStorePath)) { "if keyStoreType is " + P11KEYSTORE + ", then keyStore must be " + NONE }

            var keystorePassword: CharArray? = null
            if (!keyStorePasswordString.isEmpty()) {
                keystorePassword = keyStorePasswordString.toCharArray()
            }

            val keyStore = loadKeyStore(
                keyStorePath,
                keyStoreType,
                keyStoreProvider,
                keystorePassword,
                true
            )

            val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            if (P11KEYSTORE == keyStoreType) {
                keyManagerFactory.init(keyStore, null)
            } else {
                keyManagerFactory.init(keyStore, keystorePassword)
            }
            return keyManagerFactory.getKeyManagers()
        }

    @Throws(Exception::class)
    private fun loadKeyStore(
        keyStorePath: String,
        keyStoreType: String,
        keyStoreProvider: String,
        keyStorePassword: CharArray?,
        errorOnMissingFile: Boolean
    ): KeyStore? {
        if (keyStoreType.isEmpty()) {
            return null
        }

        val keyStore: KeyStore?
        if (keyStoreProvider.isEmpty()) {
            keyStore = KeyStore.getInstance(keyStoreType)
        } else {
            keyStore = KeyStore.getInstance(keyStoreType, keyStoreProvider)
        }

        if (!keyStorePath.isEmpty() && NONE != keyStorePath) {
            try {
                FileInputStream(keyStorePath).use { fis ->
                    keyStore.load(fis, keyStorePassword)
                }
            } catch (e: FileNotFoundException) {
                if (errorOnMissingFile) {
                    throw e
                } else {
                    return null
                }
            }
        } else {
            keyStore.load(null, keyStorePassword)
        }
        return keyStore
    }

    private val defaultSecurityPath: String
        get() = System.getProperty("java.home") + File.separator + "lib" + File.separator + "security"

    private val defaultTrustStore: String
        get() = defaultSecurityPath + File.separator + "cacerts"

    private val defaultJsseTrustStore: String
        get() = defaultSecurityPath + File.separator + "jssecacerts"

    @get:Throws(Exception::class)
    private val trustManagers: Array<TrustManager>
        get() {
            var storePath = System.getProperty("javax.net.ssl.trustStore", defaultJsseTrustStore)
            val storeType = System.getProperty("javax.net.ssl.trustStoreType", KeyStore.getDefaultType())
            val storeProvider = System.getProperty("javax.net.ssl.trustStoreProvider", "")
            val storePasswordString = System.getProperty("javax.net.ssl.trustStorePassword", "")

            var keyStore: KeyStore? = null
            if (NONE != storePath) {
                val fileNames = arrayOf<String>(storePath, defaultTrustStore)
                for (fileName in fileNames) {
                    val candidate = File(fileName)
                    if (candidate.isFile() && candidate.canRead()) {
                        storePath = fileName
                        break
                    } else if (fileName != defaultJsseTrustStore) {
                        LOGGER.warn(
                            "Trust store file {} does not exist or is not readable. This may lead to SSL connection failures.",
                            fileName
                        )
                    }
                }

                var storePassword: CharArray? = null
                if (!storePasswordString.isEmpty()) {
                    storePassword = storePasswordString.toCharArray()
                }

                keyStore = loadKeyStore(
                    storePath,
                    storeType,
                    storeProvider,
                    storePassword,
                    false
                )
            }

            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(keyStore)

            return trustManagerFactory.getTrustManagers()
        }
}
