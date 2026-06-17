/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.resource.transport.gcp.gcs

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.http.HttpBackOffIOExceptionHandler
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpResponse
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.HttpUnsuccessfulResponseHandler
import com.google.api.client.util.ExponentialBackOff
import com.google.api.client.util.Sleeper
import com.google.common.base.Preconditions
import com.google.common.base.Supplier
import org.gradle.api.logging.Logging.getLogger
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * RetryHttpInitializerWrapper will automatically retry upon RPC failures, preserving the
 * auto-refresh behavior of the Google Credentials.
 *
 * Adapted from https://github.com/GoogleCloudPlatform/java-docs-samples,
 * available under [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) license.
 */
internal class RetryHttpInitializerWrapper @JvmOverloads constructor(credentialSupplier: Supplier<Credential?>?, private val sleeper: Sleeper? = Sleeper.DEFAULT) : HttpRequestInitializer {
    private val credentialSupplier: Supplier<Credential?>

    init {
        this.credentialSupplier = Preconditions.checkNotNull<Supplier<Credential?>>(credentialSupplier)
    }

    override fun initialize(request: HttpRequest) {
        // Turn off request logging, this can end up logging OAUTH
        // tokens which should not ever be in a build log
        val loggingEnabled = false
        request.setLoggingEnabled(loggingEnabled)
        request.setCurlLoggingEnabled(loggingEnabled)
        disableHttpTransportLogging()

        request.setReadTimeout(DEFAULT_READ_TIMEOUT_MILLIS.toInt())
        val backoffHandler: HttpUnsuccessfulResponseHandler =
            HttpBackOffUnsuccessfulResponseHandler(
                ExponentialBackOff()
            ).setSleeper(sleeper)
        val credential = credentialSupplier.get()
        request.setInterceptor(credential)
        request.setUnsuccessfulResponseHandler(object : HttpUnsuccessfulResponseHandler {
            @Throws(IOException::class)
            override fun handleResponse(request: HttpRequest, response: HttpResponse?, supportsRetry: Boolean): Boolean {
                // Turn off request logging unless debug mode is enabled
                request.setLoggingEnabled(loggingEnabled)
                request.setCurlLoggingEnabled(loggingEnabled)
                if (credential!!.handleResponse(request, response, supportsRetry)) {
                    // If credential decides it can handle it, the return code or message indicated
                    // something specific to authentication, and no backoff is desired.
                    return true
                } else if (backoffHandler.handleResponse(request, response, supportsRetry)) {
                    // Otherwise, we defer to the judgement of our internal backoff handler.
                    LOG!!.info("Retrying {}", request.getUrl())
                    return true
                } else {
                    return false
                }
            }
        })
        request.setIOExceptionHandler(
            HttpBackOffIOExceptionHandler(ExponentialBackOff())
                .setSleeper(sleeper)
        )
    }

    companion object {
        private val LOG = getLogger(RetryHttpInitializerWrapper::class.java)
        private val DEFAULT_READ_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2)

        private fun disableHttpTransportLogging() {
            java.util.logging.Logger.getLogger(HttpTransport::class.java.getName()).setLevel(Level.OFF)
        }
    }
}
