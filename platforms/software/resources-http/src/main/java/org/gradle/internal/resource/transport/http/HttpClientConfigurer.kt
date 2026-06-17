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

import org.apache.http.HttpException
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.HttpRequestInterceptor
import org.apache.http.auth.AuthScheme
import org.apache.http.auth.AuthSchemeProvider
import org.apache.http.auth.AuthScope
import org.apache.http.auth.AuthState
import org.apache.http.auth.NTCredentials
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.config.AuthSchemes
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.client.utils.DateUtils
import org.apache.http.config.RegistryBuilder
import org.apache.http.config.SocketConfig
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.util.PublicSuffixMatcherLoader
import org.apache.http.cookie.CookieSpecProvider
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.auth.BasicSchemeFactory
import org.apache.http.impl.auth.DigestSchemeFactory
import org.apache.http.impl.auth.KerberosSchemeFactory
import org.apache.http.impl.auth.SPNegoSchemeFactory
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.SystemDefaultCredentialsProvider
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.apache.http.impl.cookie.DefaultCookieSpecProvider
import org.apache.http.impl.cookie.IgnoreSpecProvider
import org.apache.http.impl.cookie.NetscapeDraftSpecProvider
import org.apache.http.impl.cookie.RFC6265CookieSpecProvider
import org.apache.http.protocol.HttpContext
import org.apache.http.protocol.HttpCoreContext
import org.gradle.api.JavaVersion.Companion.current
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.authentication.Authentication
import org.gradle.authentication.http.BasicAuthentication
import org.gradle.authentication.http.DigestAuthentication
import org.gradle.authentication.http.HttpHeaderAuthentication
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.authentication.AllSchemesAuthentication
import org.gradle.internal.authentication.AuthenticationInternal
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.resource.UriTextResource
import org.gradle.internal.resource.transport.http.ntlm.NTLMCredentials
import org.gradle.internal.resource.transport.http.ntlm.NTLMSchemeFactory
import org.gradle.util.internal.CollectionUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ProxySelector
import java.util.Arrays
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier

class HttpClientConfigurer(private val httpSettings: HttpSettings) {
    /**
     * Determines the HTTPS protocols to support for the client.
     *
     * @implNote To support the Gradle embedded test runner, this method's return value should not be cached in a static field.
     */
    private fun determineHttpsProtocols(): Array<String?> {
        /*
         * System property retrieval is executed within the constructor to support the Gradle embedded test runner.
         */
        val httpsProtocols = System.getProperty(HTTPS_PROTOCOLS)
        if (httpsProtocols != null) {
            return httpsProtocols.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        } else if (current()!!.isJava8 && Jvm.current().isIbmJvm()) {
            return arrayOf<String>("TLSv1.2")
        } else if (jdkSupportsTLSProtocol("TLSv1.3")) {
            return arrayOf<String>("TLSv1.2", "TLSv1.3")
        } else {
            return arrayOf<String>("TLSv1.2")
        }
    }

    private fun jdkSupportsTLSProtocol(protocol: String): Boolean {
        try {
            for (supportedProtocol in httpSettings.sslContextFactory!!.createSslContext()!!.getSupportedSSLParameters().getProtocols()) {
                if (protocol == supportedProtocol) {
                    return true
                }
            }
            return false
        } catch (e: Exception) {
            throw throwAsUncheckedException(e)
        }
    }

    fun supportedTlsVersions(): MutableCollection<String?> {
        return Arrays.asList<String?>(*sslProtocols)
    }

    private val sslProtocols: Array<String?>

    init {
        this.sslProtocols = determineHttpsProtocols()
    }

    fun configure(builder: HttpClientBuilder) {
        val credentialsProvider = SystemDefaultCredentialsProvider()
        configureSslSocketConnectionFactory(builder, httpSettings.sslContextFactory!!, httpSettings.hostnameVerifier)
        configureAuthSchemeRegistry(builder)
        configureCredentials(builder, credentialsProvider, httpSettings.authenticationSettings)
        configureProxy(builder, credentialsProvider, httpSettings)
        configureUserAgent(builder)
        configureCookieSpecRegistry(builder)
        configureRequestConfig(builder)
        configureSocketConfig(builder)
        configureRedirectStrategy(builder)
        builder.setDefaultCredentialsProvider(credentialsProvider)
        builder.setMaxConnTotal(httpSettings.maxConnTotal)
        builder.setMaxConnPerRoute(httpSettings.maxConnPerRoute)
        builder.setConnectionTimeToLive(httpSettings.timeoutSettings!!.idleConnectionTimeoutMs, TimeUnit.MILLISECONDS)
    }

    private fun configureSslSocketConnectionFactory(builder: HttpClientBuilder, sslContextFactory: SslContextFactory, hostnameVerifier: HostnameVerifier?) {
        builder.setSSLSocketFactory(SSLConnectionSocketFactory(sslContextFactory.createSslContext(), sslProtocols, null, hostnameVerifier))
    }

    private fun configureAuthSchemeRegistry(builder: HttpClientBuilder) {
        builder.setDefaultAuthSchemeRegistry(
            RegistryBuilder.create<AuthSchemeProvider?>()
                .register(AuthSchemes.BASIC, BasicSchemeFactory())
                .register(AuthSchemes.DIGEST, DigestSchemeFactory())
                .register(AuthSchemes.NTLM, NTLMSchemeFactory())
                .register(AuthSchemes.SPNEGO, SPNegoSchemeFactory())
                .register(AuthSchemes.KERBEROS, KerberosSchemeFactory())
                .register(HttpHeaderAuthScheme.AUTH_SCHEME_NAME, HttpHeaderSchemeFactory())
                .build()
        )
    }

    private fun configureCredentials(builder: HttpClientBuilder, credentialsProvider: CredentialsProvider, authentications: MutableCollection<Authentication>) {
        if (authentications.size > 0) {
            useCredentials(credentialsProvider, authentications)

            // Use preemptive authorisation if no other authorisation has been established
            builder.addInterceptorFirst(PreemptiveAuth(getAuthScheme(authentications), isPreemptiveEnabled(authentications)))
        }
    }

    private fun getAuthScheme(authentications: MutableCollection<Authentication>): AuthScheme {
        if (authentications.size == 1) {
            if (authentications.iterator().next() is HttpHeaderAuthentication) {
                return HttpHeaderAuthScheme()
            }
        }
        return BasicScheme()
    }

    private fun configureProxy(builder: HttpClientBuilder, credentialsProvider: CredentialsProvider, httpSettings: HttpSettings) {
        useCredentialsForProxy(credentialsProvider, httpSettings.proxySettings!!.proxy)
        useCredentialsForProxy(credentialsProvider, httpSettings.secureProxySettings!!.proxy)

        builder.setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
    }

    private fun useCredentialsForProxy(credentialsProvider: CredentialsProvider, httpsProxy: HttpProxySettings.HttpProxy?) {
        if (httpsProxy != null && httpsProxy.credentials != null) {
            val authentication1 = AllSchemesAuthentication(httpsProxy.credentials)
            authentication1.addHost(httpsProxy.host, httpsProxy.port)
            useCredentials(credentialsProvider, mutableSetOf<AllSchemesAuthentication?>(authentication1))
        }
    }

    private fun useCredentials(credentialsProvider: CredentialsProvider, authentications: MutableCollection<out Authentication>) {
        for (authentication in authentications) {
            val authenticationInternal = authentication as AuthenticationInternal?

            val scheme = getAuthScheme(authentication)
            val credentials = authenticationInternal!!.credentials

            val hostsForAuthentication: MutableCollection<AuthenticationInternal.HostAndPort> = authenticationInternal.hostsForAuthentication
            assert(!hostsForAuthentication.isEmpty()) { "Credentials and authentication required for a HTTP repository, but no hosts were defined for the authentication?" }

            for (hostAndPort in hostsForAuthentication) {
                val host: String = hostAndPort.host!!
                val port = hostAndPort.port

                checkNotNull(host) { "HTTP credentials and authentication require a host scope to be defined as well" }

                if (credentials is HttpHeaderCredentials) {
                    val httpHeaderCredentials = credentials
                    val httpCredentials: org.apache.http.auth.Credentials = HttpClientHttpHeaderCredentials(httpHeaderCredentials.name, httpHeaderCredentials.value)
                    credentialsProvider.setCredentials(AuthScope(host, port, AuthScope.ANY_REALM, scheme), httpCredentials)

                    LOGGER.debug("Using {} for authenticating against '{}:{}' using {}", httpHeaderCredentials, host, port, scheme)
                } else if (credentials is PasswordCredentials || credentials is HttpProxySettings.HttpProxyCredentials) {
                    val username: String
                    val password: String?
                    if (credentials is PasswordCredentials) {
                        val passwordCredentials = credentials
                        username = passwordCredentials.username!!
                        password = passwordCredentials.password
                    } else {
                        val proxyCredentials = credentials as HttpProxySettings.HttpProxyCredentials
                        username = proxyCredentials.username
                        password = proxyCredentials.password
                    }

                    if (authentication is AllSchemesAuthentication) {
                        val ntlmCredentials = NTLMCredentials(username, password)
                        val httpCredentials: org.apache.http.auth.Credentials =
                            NTCredentials(ntlmCredentials.username, ntlmCredentials.password, ntlmCredentials.workstation, ntlmCredentials.domain)
                        credentialsProvider.setCredentials(AuthScope(host, port, AuthScope.ANY_REALM, AuthSchemes.NTLM), httpCredentials)

                        LOGGER.debug("Using {} and {} for authenticating against '{}:{}' using {}", credentials, ntlmCredentials, host, port, AuthSchemes.NTLM)
                    }

                    val httpCredentials: org.apache.http.auth.Credentials = UsernamePasswordCredentials(username, password)
                    credentialsProvider.setCredentials(AuthScope(host, port, AuthScope.ANY_REALM, scheme), httpCredentials)
                    LOGGER.debug("Using {} for authenticating against '{}:{}' using {}", credentials, host, port, scheme)
                } else {
                    throw IllegalArgumentException(
                        String.format(
                            "Credentials must be an instance of: %s or %s",
                            PasswordCredentials::class.java.getCanonicalName(),
                            HttpHeaderCredentials::class.java.getCanonicalName()
                        )
                    )
                }
            }
        }
    }

    private fun isPreemptiveEnabled(authentications: MutableCollection<Authentication>): Boolean {
        return CollectionUtils.any<Authentication?>(authentications, org.gradle.api.specs.Spec { element: Authentication? -> element is BasicAuthentication || element is HttpHeaderAuthentication })
    }

    fun configureUserAgent(builder: HttpClientBuilder) {
        builder.setUserAgent(UriTextResource.getUserAgentString())
    }

    private fun configureCookieSpecRegistry(builder: HttpClientBuilder) {
        val publicSuffixMatcher = PublicSuffixMatcherLoader.getDefault()
        builder.setPublicSuffixMatcher(publicSuffixMatcher)
        // Add more data patterns to the default configuration to work around https://github.com/gradle/gradle/issues/1596
        val defaultProvider: CookieSpecProvider = DefaultCookieSpecProvider(
            DefaultCookieSpecProvider.CompatibilityLevel.DEFAULT, publicSuffixMatcher, arrayOf<String>(
                "EEE, dd-MMM-yy HH:mm:ss z",  // Netscape expires pattern
                DateUtils.PATTERN_RFC1036,
                DateUtils.PATTERN_ASCTIME,
                DateUtils.PATTERN_RFC1123
            ), false
        )
        val laxStandardProvider: CookieSpecProvider = RFC6265CookieSpecProvider(
            RFC6265CookieSpecProvider.CompatibilityLevel.RELAXED, publicSuffixMatcher
        )
        val strictStandardProvider: CookieSpecProvider = RFC6265CookieSpecProvider(
            RFC6265CookieSpecProvider.CompatibilityLevel.STRICT, publicSuffixMatcher
        )
        builder.setDefaultCookieSpecRegistry(
            RegistryBuilder.create<CookieSpecProvider?>()
                .register(CookieSpecs.DEFAULT, defaultProvider)
                .register("best-match", defaultProvider)
                .register("compatibility", defaultProvider)
                .register(CookieSpecs.STANDARD, laxStandardProvider)
                .register(CookieSpecs.STANDARD_STRICT, strictStandardProvider)
                .register(CookieSpecs.NETSCAPE, NetscapeDraftSpecProvider())
                .register(CookieSpecs.IGNORE_COOKIES, IgnoreSpecProvider())
                .build()
        )
    }

    private fun configureRequestConfig(builder: HttpClientBuilder) {
        val timeoutSettings: HttpTimeoutSettings = httpSettings.timeoutSettings!!
        val config = RequestConfig.custom()
            .setConnectTimeout(timeoutSettings.connectionTimeoutMs)
            .setSocketTimeout(timeoutSettings.socketTimeoutMs)
            .setMaxRedirects(httpSettings.maxRedirects)
            .setExpectContinueEnabled(hasProxyCredentials(httpSettings))
            .build()
        builder.setDefaultRequestConfig(config)
    }

    private fun configureSocketConfig(builder: HttpClientBuilder) {
        val timeoutSettings: HttpTimeoutSettings = httpSettings.timeoutSettings!!
        builder.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(timeoutSettings.socketTimeoutMs).setSoKeepAlive(true).build())
    }

    private fun configureRedirectStrategy(builder: HttpClientBuilder) {
        if (httpSettings.maxRedirects > 0) {
            builder.setRedirectStrategy(RedirectVerifyingStrategyDecorator(this.baseRedirectStrategy, httpSettings.redirectVerifier))
        } else {
            builder.disableRedirectHandling()
        }
    }

    private val baseRedirectStrategy: RedirectStrategy
        get() {
            when (httpSettings.redirectMethodHandlingStrategy) {
                ALLOW_FOLLOW_FOR_MUTATIONS -> return AllowFollowForMutatingMethodRedirectStrategy()
                ALWAYS_FOLLOW_AND_PRESERVE -> return AlwaysFollowAndPreserveMethodRedirectStrategy()
                else -> throw IllegalArgumentException(httpSettings.redirectMethodHandlingStrategy.name())
            }
        }

    private fun getAuthScheme(authentication: Authentication): String? {
        if (authentication is BasicAuthentication) {
            return AuthSchemes.BASIC
        } else if (authentication is DigestAuthentication) {
            return AuthSchemes.DIGEST
        } else if (authentication is HttpHeaderAuthentication) {
            return HttpHeaderAuthScheme.AUTH_SCHEME_NAME
        } else if (authentication is AllSchemesAuthentication) {
            return AuthScope.ANY_SCHEME
        } else {
            throw IllegalArgumentException(String.format("Authentication scheme of '%s' is not supported.", authentication.javaClass.getSimpleName()))
        }
    }

    internal class PreemptiveAuth(private val authScheme: AuthScheme?, private val alwaysSendAuth: Boolean) : HttpRequestInterceptor {
        @Throws(HttpException::class)
        override fun process(request: HttpRequest, context: HttpContext) {
            val authState = context.getAttribute(HttpClientContext.TARGET_AUTH_STATE) as AuthState

            if (authState.getAuthScheme() != null || authState.hasAuthOptions()) {
                return
            }

            // If no authState has been established and this is a PUT or POST request, add preemptive authorisation
            val requestMethod = request.getRequestLine().getMethod()
            if (alwaysSendAuth || requestMethod == HttpPut.METHOD_NAME || requestMethod == HttpPost.METHOD_NAME) {
                val credentialsProvider = context.getAttribute(HttpClientContext.CREDS_PROVIDER) as CredentialsProvider
                val targetHost = context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST) as HttpHost
                val credentials = credentialsProvider.getCredentials(AuthScope(targetHost.getHostName(), targetHost.getPort()))
                if (credentials != null) {
                    authState.update(authScheme, credentials)
                }
            }
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(HttpClientConfigurer::class.java)
        private const val HTTPS_PROTOCOLS = "https.protocols"

        private fun hasProxyCredentials(httpSettings: HttpSettings): Boolean {
            val httpProxy = httpSettings.proxySettings!!.proxy
            val httpsProxy = httpSettings.secureProxySettings!!.proxy
            return (httpProxy != null && httpProxy.credentials != null)
                    || (httpsProxy != null && httpsProxy.credentials != null)
        }
    }
}
