/*
 * Copyright 2018 the original author or authors.
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

import org.apache.http.Header
import org.apache.http.HttpRequest
import org.apache.http.auth.AuthenticationException
import org.apache.http.auth.ContextAwareAuthScheme
import org.apache.http.auth.Credentials
import org.apache.http.auth.MalformedChallengeException
import org.apache.http.protocol.BasicHttpContext
import org.apache.http.protocol.HttpContext
import org.apache.http.util.Args

class HttpHeaderAuthScheme : ContextAwareAuthScheme {
    @Throws(MalformedChallengeException::class)
    override fun processChallenge(header: Header?) {
    }

    override fun getSchemeName(): String {
        return AUTH_SCHEME_NAME
    }

    override fun getParameter(name: String?): String? {
        return null
    }

    override fun getRealm(): String? {
        return null
    }

    override fun isConnectionBased(): Boolean {
        return false
    }

    override fun isComplete(): Boolean {
        return true
    }

    @Suppress("deprecation")
    @Throws(AuthenticationException::class)
    override fun authenticate(credentials: Credentials, request: HttpRequest?): Header? {
        return this.authenticate(credentials, request, BasicHttpContext())
    }

    @Throws(AuthenticationException::class)
    override fun authenticate(credentials: Credentials, request: HttpRequest?, context: HttpContext?): Header? {
        Args.check(
            credentials is HttpClientHttpHeaderCredentials,
            "Only " + HttpClientHttpHeaderCredentials::class.java.getCanonicalName() + " supported for AuthScheme " + this.javaClass.getCanonicalName() + ", got " + credentials.javaClass.getName()
        )
        val httpClientHttpHeaderCredentials = credentials as HttpClientHttpHeaderCredentials
        return httpClientHttpHeaderCredentials.header
    }

    companion object {
        const val AUTH_SCHEME_NAME: String = "header"
    }
}
