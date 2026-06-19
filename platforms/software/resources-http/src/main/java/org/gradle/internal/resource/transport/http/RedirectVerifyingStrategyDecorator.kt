/*
 * Copyright 2021 the original author or authors.
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

import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.ProtocolException
import org.apache.http.client.RedirectStrategy
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.protocol.HttpContext
import org.gradle.internal.verifier.HttpRedirectVerifier
import java.net.URI

internal class RedirectVerifyingStrategyDecorator(private val delegate: RedirectStrategy, private val verifier: HttpRedirectVerifier) : RedirectStrategy {
    @Throws(ProtocolException::class)
    override fun isRedirected(request: HttpRequest?, response: HttpResponse?, context: HttpContext?): Boolean {
        return delegate.isRedirected(request, response, context)
    }

    @Throws(ProtocolException::class)
    override fun getRedirect(request: HttpRequest?, response: HttpResponse?, context: HttpContext?): HttpUriRequest {
        val redirectRequest = delegate.getRedirect(request, response, context)
        verifier.validateRedirects(mutableListOf<URI>(redirectRequest.getURI()))
        return redirectRequest
    }
}
