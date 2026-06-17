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
package org.gradle.api.internal.artifacts.repositories.transport

import org.apache.http.ConnectionClosedException
import org.apache.http.HttpStatus
import org.apache.http.NoHttpResponseException
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.resource.transport.http.HttpErrorStatusCodeException
import java.net.SocketException
import java.net.SocketTimeoutException

object NetworkingIssueVerifier {
    // Too many requests (not available through HttpStatus.XXX)
    private const val SC_TOO_MANY_REQUESTS = 429

    /**
     * Determines if an error should cause a retry. We will currently retry:
     *
     *  * on a network timeout
     *  * on a server error (return code 5xx)
     *  * on rate limiting
     *
     */
    fun <E : Throwable?> isLikelyTransientNetworkingIssue(failure: E?): Boolean {
        if (failure is SocketException || failure is SocketTimeoutException || failure is NoHttpResponseException || failure is ConnectionClosedException) {
            return true
        }
        if (failure is DefaultMultiCauseException) {
            val causes: MutableList<out Throwable?> = (failure as DefaultMultiCauseException).getCauses()
            for (cause in causes) {
                if (isLikelyTransientNetworkingIssue<Throwable?>(cause)) {
                    return true
                }
            }
        }
        if (failure is HttpErrorStatusCodeException) {
            val httpError = failure as HttpErrorStatusCodeException
            return httpError.isServerError() || isTransientClientError(httpError.statusCode)
        }
        val cause = failure!!.cause
        if (cause != null && cause !== failure) {
            return isLikelyTransientNetworkingIssue<Throwable?>(cause)
        }
        return false
    }

    private fun isTransientClientError(statusCode: Int): Boolean {
        return statusCode == HttpStatus.SC_REQUEST_TIMEOUT || statusCode == SC_TOO_MANY_REQUESTS
    }

    fun <E : Throwable?> isLikelyPermanentNetworkIssue(failure: E?): Boolean {
        if (failure is HttpErrorStatusCodeException) {
            return isClientAuthenticationError((failure as HttpErrorStatusCodeException).statusCode)
        }
        if (failure is DefaultMultiCauseException) {
            val causes: MutableList<out Throwable?> = (failure as DefaultMultiCauseException).getCauses()
            for (cause in causes) {
                if (isLikelyPermanentNetworkIssue<Throwable?>(cause)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isClientAuthenticationError(statusCode: Int): Boolean {
        return statusCode == HttpStatus.SC_UNAUTHORIZED || statusCode == HttpStatus.SC_FORBIDDEN
    }
}
