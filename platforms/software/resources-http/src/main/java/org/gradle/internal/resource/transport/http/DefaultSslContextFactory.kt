/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.collect.ImmutableSet
import org.apache.http.ssl.SSLInitializationException
import org.gradle.internal.SystemProperties
import org.gradle.internal.resource.transport.http.SystemDefaultSSLContextFactory.create
import org.jspecify.annotations.NullMarked
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.TreeMap
import javax.net.ssl.SSLContext

@NullMarked
class DefaultSslContextFactory : SslContextFactory {
    private val cache = CacheBuilder.newBuilder().softValues().build<MutableMap<String, String>, SSLContext>(
        SynchronizedSystemPropertiesCacheLoader()
    )

    override fun createSslContext(): SSLContext {
        return cache.getUnchecked(currentProperties)
    }

    private class SynchronizedSystemPropertiesCacheLoader : CacheLoader<MutableMap<String, String>, SSLContext>() {
        override fun load(props: MutableMap<String, String>): SSLContext {
            /*
             * NOTE! The JDK code to create SSLContexts relies on the values of the given system properties.
             *
             * To prevent concurrent changes to system properties from interfering with this, we need to synchronize access/modifications
             * to system properties.  This is best effort since we can't prevent user code from modifying system properties willy-nilly.
             *
             * The most critical system property is java.home. Changing this property while trying to create a SSLContext can cause many strange
             * problems:
             * https://github.com/gradle/gradle/issues/8830
             * https://github.com/gradle/gradle/issues/8039
             * https://github.com/gradle/gradle/issues/7842
             * https://github.com/gradle/gradle/issues/2588
             */
            return SystemProperties.getInstance().withSystemProperties<SSLContext>(props, org.gradle.internal.Factory { SslContextLoader.load(props) })
        }
    }

    private object SslContextLoader {
        private val LOGGER: Logger = LoggerFactory.getLogger(SslContextLoader::class.java)

        fun load(props: MutableMap<String, String>): SSLContext {
            try {
                return create()
            } catch (e: Exception) {
                LOGGER.error("Could not initialize SSL context. Used properties: {}", props)
                throw SSLInitializationException(e.message, e)
            }
        }
    }

    companion object {
        private val SSL_SYSTEM_PROPERTIES: MutableSet<String> = ImmutableSet.of<String>(
            "ssl.TrustManagerFactory.algorithm",
            "javax.net.ssl.trustStoreType",
            "javax.net.ssl.trustStore",
            "javax.net.ssl.trustStoreProvider",
            "javax.net.ssl.trustStorePassword",
            "ssl.KeyManagerFactory.algorithm",
            "javax.net.ssl.keyStoreType",
            "javax.net.ssl.keyStore",
            "javax.net.ssl.keyStoreProvider",
            "javax.net.ssl.keyStorePassword",
            "java.home"
        )

        private val currentProperties: MutableMap<String, String>
            get() = SystemProperties.getInstance().withSystemProperties<MutableMap<String, String>>(org.gradle.internal.Factory {
                val currentProperties: MutableMap<String, String> = TreeMap<String, String>()
                for (prop in SSL_SYSTEM_PROPERTIES) {
                    currentProperties.put(prop, System.getProperty(prop))
                }
                currentProperties
            })
    }
}
