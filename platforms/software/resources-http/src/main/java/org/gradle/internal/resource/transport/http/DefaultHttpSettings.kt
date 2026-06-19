/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.base.Preconditions
import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import org.apache.http.conn.ssl.DefaultHostnameVerifier
import org.gradle.authentication.Authentication
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.verifier.HttpRedirectVerifier
import java.security.GeneralSecurityException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


class DefaultHttpSettings private constructor(
    authenticationSettings: MutableCollection<Authentication>,
    sslContextFactory: SslContextFactory,
    hostnameVerifier: HostnameVerifier,
    redirectVerifier: HttpRedirectVerifier,
    redirectMethodHandlingStrategy: HttpSettings.RedirectMethodHandlingStrategy,
    maxRedirects: Int,
    maxConnTotal: Int,
    maxConnPerRoute: Int
) : HttpSettings {
    override val authenticationSettings: MutableCollection<Authentication>
    override val sslContextFactory: SslContextFactory
    override val hostnameVerifier: HostnameVerifier
    override val redirectVerifier: HttpRedirectVerifier
    override val maxRedirects: Int
    override val maxConnTotal: Int
    override val maxConnPerRoute: Int
    override val redirectMethodHandlingStrategy: HttpSettings.RedirectMethodHandlingStrategy

    private var proxySettingsValue: HttpProxySettings? = null
    override val proxySettings: HttpProxySettings
        get() {
            if (proxySettingsValue == null) {
                proxySettingsValue = JavaSystemPropertiesHttpProxySettings()
            }
            return proxySettingsValue!!
        }
    private var secureProxySettingsValue: HttpProxySettings? = null
    override val secureProxySettings: HttpProxySettings
        get() {
            if (secureProxySettingsValue == null) {
                secureProxySettingsValue = JavaSystemPropertiesSecureHttpProxySettings()
            }
            return secureProxySettingsValue!!
        }
    private var timeoutSettingsValue: HttpTimeoutSettings? = null
    override val timeoutSettings: HttpTimeoutSettings
        get() {
            if (timeoutSettingsValue == null) {
                timeoutSettingsValue = JavaSystemPropertiesHttpTimeoutSettings()
            }
            return timeoutSettingsValue!!
        }

    class Builder {
        private var authenticationSettings: MutableCollection<Authentication>? = null
        private var sslContextFactory: SslContextFactory? = null
        private var hostnameVerifier: HostnameVerifier? = null
        private var redirectVerifier: HttpRedirectVerifier? = null
        private var maxRedirects: Int = DEFAULT_MAX_REDIRECTS
        private var maxConnTotal: Int = DEFAULT_MAX_CONNECTIONS
        private var maxConnPerRoute: Int = DEFAULT_MAX_PER_ROUTE
        private var redirectMethodHandlingStrategy = HttpSettings.RedirectMethodHandlingStrategy.ALWAYS_FOLLOW_AND_PRESERVE

        fun withAuthenticationSettings(authenticationSettings: MutableCollection<Authentication>): Builder {
            this.authenticationSettings = authenticationSettings
            return this
        }

        fun withSslContextFactory(sslContextFactory: SslContextFactory): Builder {
            this.sslContextFactory = sslContextFactory
            this.hostnameVerifier = DefaultHostnameVerifier(null)
            return this
        }

        fun withRedirectVerifier(redirectVerifier: HttpRedirectVerifier): Builder {
            this.redirectVerifier = redirectVerifier
            return this
        }

        fun allowUntrustedConnections(): Builder {
            this.sslContextFactory = ALL_TRUSTING_SSL_CONTEXT_FACTORY
            this.hostnameVerifier = ALL_TRUSTING_HOSTNAME_VERIFIER
            return this
        }

        fun maxRedirects(maxRedirects: Int): Builder {
            Preconditions.checkArgument(maxRedirects >= 0)
            this.maxRedirects = maxRedirects
            return this
        }

        fun maxConnTotal(maxConnTotal: Int): Builder {
            Preconditions.checkArgument(maxConnTotal > 0)
            this.maxConnTotal = maxConnTotal
            return this
        }

        fun maxConnPerRoute(maxConnPerRoute: Int): Builder {
            Preconditions.checkArgument(maxConnPerRoute > 0)
            this.maxConnPerRoute = maxConnPerRoute
            return this
        }

        fun withRedirectMethodHandlingStrategy(redirectMethodHandlingStrategy: HttpSettings.RedirectMethodHandlingStrategy): Builder {
            this.redirectMethodHandlingStrategy = redirectMethodHandlingStrategy
            return this
        }

        fun build(): HttpSettings {
            return DefaultHttpSettings(
                authenticationSettings!!,
                sslContextFactory!!,
                hostnameVerifier!!,
                redirectVerifier!!,
                redirectMethodHandlingStrategy,
                maxRedirects,
                maxConnTotal,
                maxConnPerRoute
            )
        }
    }

    init {
        Preconditions.checkArgument(maxRedirects >= 0, "maxRedirects must be positive")
        Preconditions.checkArgument(maxConnTotal > 0, "maxConnTotal must be positive")
        Preconditions.checkArgument(maxConnPerRoute > 0, "maxConnPerRoute must be positive")
        Preconditions.checkNotNull(authenticationSettings, "authenticationSettings")
        Preconditions.checkNotNull(sslContextFactory, "sslContextFactory")
        Preconditions.checkNotNull(hostnameVerifier, "hostnameVerifier")
        Preconditions.checkNotNull(redirectVerifier, "redirectVerifier")
        Preconditions.checkNotNull(redirectMethodHandlingStrategy, "redirectMethodHandlingStrategy")

        this.maxRedirects = maxRedirects
        this.maxConnTotal = maxConnTotal
        this.maxConnPerRoute = maxConnPerRoute
        this.authenticationSettings = authenticationSettings
        this.sslContextFactory = sslContextFactory
        this.hostnameVerifier = hostnameVerifier
        this.redirectVerifier = redirectVerifier
        this.redirectMethodHandlingStrategy = redirectMethodHandlingStrategy
    }

    companion object {
        private const val DEFAULT_MAX_REDIRECTS = 10

        /**
         * The maximum number of connections we will make to a single server.
         */
        const val DEFAULT_MAX_PER_ROUTE: Int = 20

        /**
         * The maximum number of connections we will make to any number of servers.
         * This is greater than [.DEFAULT_MAX_PER_ROUTE], so a single repository
         * does not saturate all available connections we can make.
         */
        private val DEFAULT_MAX_CONNECTIONS: Int = DEFAULT_MAX_PER_ROUTE * 4

        @JvmStatic
        fun builder(): Builder {
            return Builder()
        }

        private val ALL_TRUSTING_HOSTNAME_VERIFIER: HostnameVerifier = object : HostnameVerifier {
            override fun verify(hostname: String?, session: SSLSession?): Boolean {
                return true
            }
        }

        private val ALL_TRUSTING_SSL_CONTEXT_FACTORY: SslContextFactory = object : SslContextFactory {
            private val sslContextSupplier = Suppliers.memoize<SSLContext?>(object : Supplier<SSLContext?> {
                override fun get(): SSLContext {
                    try {
                        val sslcontext = SSLContext.getInstance("TLS")
                        sslcontext.init(null, allTrustingTrustManager, null)
                        return sslcontext
                    } catch (e: GeneralSecurityException) {
                        throw throwAsUncheckedException(e)
                    }
                }
            })

            override fun createSslContext(): SSLContext? {
                return sslContextSupplier.get()
            }

            private val allTrustingTrustManager: Array<TrustManager?> = arrayOf<TrustManager?>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate?>? {
                    return null
                }

                override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {
                }

                override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {
                }
            }
            )
        }
    }
}
