/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.client.utils.URIBuilder
import org.apache.http.entity.AbstractHttpEntity
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.protocol.HttpContext
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.resource.ReadableContent
import org.jspecify.annotations.NullMarked
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketException
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Supplier
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import kotlin.Boolean
import kotlin.Exception
import kotlin.Long
import kotlin.Throwable
import kotlin.Throws
import kotlin.UnsupportedOperationException
import kotlin.text.contains

/**
 * Implementation of [HttpClient] backed by Apache Commons HttpClient.
 */
@NullMarked
class ApacheCommonsHttpClient @VisibleForTesting internal constructor(
    private val documentationRegistry: DocumentationRegistry,
    private val settings: HttpSettings,
    private val clientBuilderFactory: Supplier<HttpClientBuilder>
) : HttpClient {
    private var supportedTlsVersions: MutableCollection<String>? = null
    private var client: CloseableHttpClient? = null

    /**
     * Maintains a queue of contexts which are shared between threads when authentication
     * is activated. When a request is performed, it will pick a context from the queue,
     * and create a new one whenever it's not available (which either means it's the first request
     * or that other requests are being performed concurrently). The queue will grow as big as
     * the max number of concurrent requests executed.
     */
    private val sharedContext: ConcurrentLinkedQueue<HttpContext>?

    /**
     * Use [ApacheCommonsHttpClientFactory] to instantiate instances.
     */
    internal constructor(documentationRegistry: DocumentationRegistry, settings: HttpSettings) : this(documentationRegistry, settings, Supplier { HttpClientBuilder.create() })

    /**
     * Overload intended specifically for unit testing, allowing injection of mocked HttpClientBuilder.
     */
    init {
        if (!settings.authenticationSettings.isEmpty()) {
            sharedContext = ConcurrentLinkedQueue<HttpContext>()
        } else {
            sharedContext = null
        }
    }

    override fun performHead(uri: URI, headers: ImmutableMap<String, String>): HttpClient.Response {
        val request: HttpRequestBase = HttpHead(uri)
        addHeaders(request, headers)
        return processResponse(performRequest(request))
    }

    override fun performGet(uri: URI, headers: ImmutableMap<String, String>): HttpClient.Response {
        val request: HttpRequestBase = HttpGet(uri)
        addHeaders(request, headers)
        return processResponse(performRequest(request))
    }

    @Throws(IOException::class)
    override fun performRawGet(uri: URI, headers: ImmutableMap<String, String>): HttpClient.Response {
        val httpGet = HttpGet(uri)
        addHeaders(httpGet, headers)
        return performRawRequest(httpGet)
    }

    @Throws(IOException::class)
    override fun performRawPut(uri: URI, resource: ReadableContent): HttpClient.Response {
        val method = HttpPut(uri)
        val entity = RepeatableInputStreamEntity(resource, ContentType.APPLICATION_OCTET_STREAM)
        method.setEntity(entity)
        return performRawRequest(method)
    }

    @Throws(IOException::class)
    override fun performRawPut(uri: URI, headers: ImmutableMap<String, String>, resource: HttpClient.WritableContent): HttpClient.Response {
        val httpPut = HttpPut(uri)
        addHeaders(httpPut, headers)
        httpPut.setEntity(object : AbstractHttpEntity() {
            override fun isRepeatable(): Boolean {
                return true
            }

            override fun getContentLength(): Long {
                return resource.size
            }

            override fun getContent(): InputStream? {
                throw UnsupportedOperationException()
            }

            @Throws(IOException::class)
            override fun writeTo(outstream: OutputStream) {
                resource.writeTo(outstream)
            }

            override fun isStreaming(): Boolean {
                return false
            }
        })

        return performRawRequest(httpPut)
    }

    private fun performRequest(request: HttpRequestBase): HttpClient.Response {
        try {
            return performRawRequest(request)
        } catch (e: FailureFromRedirectLocation) {
            throw Companion.createHttpRequestException(request.getMethod(), e.cause!!, e.lastRedirectLocation)
        } catch (e: IOException) {
            throw createHttpRequestException(request.getMethod(), wrapWithExplanation(e), request.getURI())
        }
    }

    private fun wrapWithExplanation(e: IOException): Exception {
        if (e is SocketException || (e is SSLException && e.message!!.contains("readHandshakeRecord"))) {
            return HttpRequestException("Got socket exception during request. It might be caused by SSL misconfiguration", e)
        }

        if (e !is SSLHandshakeException) {
            return e
        }

        val sslException = e
        val message: String?

        if (e.message!!.contains("PKIX path building failed") || e.message!!.contains("certificate_unknown")) {
            message = "Got SSL handshake exception during request. It might be caused by SSL misconfiguration"
        } else {
            message = String.format(
                "The server %s not support the client's requested TLS protocol versions: (%s). " +
                        "You may need to configure the client to allow other protocols to be used. " +
                        "%s",
                getConfidenceNote(sslException),
                supportedTlsVersions!!.joinToString(", "),
                documentationRegistry.getDocumentationRecommendationFor("on this", "build_environment", "sec:gradle_system_properties")
            )
        }
        return HttpRequestException(message, e)
    }

    @Throws(IOException::class)
    private fun performRawRequest(request: HttpRequestBase): HttpClient.Response {
        if (sharedContext == null) {
            // There's no authentication involved, requests can be done concurrently
            return performHttpRequest(request, BasicHttpContext())
        }
        val httpContext = nextAvailableSharedContext()
        try {
            return performHttpRequest(request, httpContext)
        } finally {
            sharedContext.add(httpContext)
        }
    }

    private fun nextAvailableSharedContext(): HttpContext {
        val context = sharedContext!!.poll()
        if (context == null) {
            return BasicHttpContext()
        }
        return context
    }

    @Throws(IOException::class)
    private fun performHttpRequest(request: HttpRequestBase, httpContext: HttpContext): HttpClient.Response {
        // Without this, HTTP Client prohibits multiple redirects to the same location within the same context
        httpContext.removeAttribute(HttpClientContext.REDIRECT_LOCATIONS)
        LOGGER.debug("Performing HTTP {}: {}", request.getMethod(), stripUserCredentials(request.getURI()))

        try {
            val response = getClient().execute(request, httpContext)
            return toHttpClientResponse(request, httpContext, response)
        } catch (e: IOException) {
            validateRedirectChain(httpContext)
            val lastRedirectLocation: URI? = stripUserCredentials(getLastRedirectLocation(httpContext))
            if (lastRedirectLocation == null) {
                throw e
            }
            throw FailureFromRedirectLocation(lastRedirectLocation, e)
        }
    }

    private fun toHttpClientResponse(request: HttpRequestBase, httpContext: HttpContext, response: CloseableHttpResponse): HttpClient.Response {
        validateRedirectChain(httpContext)
        val lastRedirectLocation: URI? = getLastRedirectLocation(httpContext)
        val effectiveUri = if (lastRedirectLocation == null) request.getURI() else lastRedirectLocation
        return ApacheHttpResponse(request.getMethod(), effectiveUri, response)
    }

    /**
     * Validates that no redirect used an insecure protocol.
     * Redirecting through an insecure protocol can allow for a MITM redirect to an attacker controlled HTTPS server.
     */
    private fun validateRedirectChain(httpContext: HttpContext) {
        settings.redirectVerifier.validateRedirects(getRedirectLocations(httpContext))
    }

    @Synchronized
    private fun getClient(): CloseableHttpClient {
        if (client == null) {
            val builder = clientBuilderFactory.get()
            val configurer = HttpClientConfigurer(settings)
            configurer.configure(builder)
            this.supportedTlsVersions = configurer.supportedTlsVersions()
            this.client = builder.build()
        }
        return client!!
    }

    @Synchronized
    @Throws(IOException::class)
    override fun close() {
        if (client != null) {
            client!!.close()
            if (sharedContext != null) {
                sharedContext.clear()
            }
        }
    }

    private class FailureFromRedirectLocation(val lastRedirectLocation: URI, cause: Throwable) : IOException(cause)

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(ApacheCommonsHttpClient::class.java)

        private fun addHeaders(request: HttpRequestBase, headers: ImmutableMap<String, String>) {
            for (entry in headers.entries) {
                request.addHeader(entry.key, entry.value)
            }
        }

        private fun createHttpRequestException(method: String, cause: Throwable, uri: URI): HttpRequestException {
            return HttpRequestException(String.format("Could not %s '%s'.", method, stripUserCredentials(uri)), cause)
        }

        private fun getConfidenceNote(sslException: SSLHandshakeException): String {
            if (sslException.message != null && sslException.message!!.contains("protocol_version")) {
                // If we're handling an SSLHandshakeException with the error of 'protocol_version' we know that the server doesn't support this protocol.
                return "does"
            }
            // Sometimes the SSLHandshakeException doesn't include the 'protocol_version', even though this is the cause of the error.
            // Tell the user this but with less confidence.
            return "may"
        }

        private fun getRedirectLocations(httpContext: HttpContext): MutableList<URI> {
            val redirects = httpContext.getAttribute(HttpClientContext.REDIRECT_LOCATIONS) as MutableList<URI>?
            return if (redirects == null) mutableListOf<URI>() else redirects
        }

        private fun getLastRedirectLocation(httpContext: HttpContext): URI? {
            val redirectLocations: MutableList<URI> = getRedirectLocations(httpContext)
            return if (redirectLocations.isEmpty()) null else Iterables.getLast<URI>(redirectLocations)
        }

        private fun processResponse(response: HttpClient.Response): HttpClient.Response {
            if (response.isSuccessful) {
                return response
            }

            // Consume content for non-successful responses. This avoids the connection being left open.
            response.close()

            if (response.isMissing) {
                LOGGER.info("Resource missing. [HTTP {}: {}]", response.method, stripUserCredentials(response.effectiveUri))
                return response
            }

            val effectiveUri: URI? = stripUserCredentials(response.effectiveUri)
            LOGGER.info("Failed to get resource: {}. [HTTP {}: {})]", response.method, response.statusCode, effectiveUri)
            throw HttpErrorStatusCodeException(response.method, effectiveUri.toString(), response.statusCode, response.statusReason)
        }

        /**
         * Strips the [user info][URI.getUserInfo] from the [URI] making it
         * safe to appear in log messages.
         */
        @VisibleForTesting
        fun stripUserCredentials(uri: URI?): URI? {
            if (uri == null) {
                return null
            }
            try {
                return URIBuilder(uri).setUserInfo(null).build()
            } catch (e: URISyntaxException) {
                throw throwAsUncheckedException(e, true)
            }
        }
    }
}
