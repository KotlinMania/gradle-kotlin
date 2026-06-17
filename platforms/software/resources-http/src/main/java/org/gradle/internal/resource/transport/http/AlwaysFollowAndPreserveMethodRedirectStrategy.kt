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

import org.apache.http.HttpEntityEnclosingRequest
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.ProtocolException
import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.client.methods.HttpOptions
import org.apache.http.client.methods.HttpPatch
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpTrace
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.impl.client.DefaultRedirectStrategy
import org.apache.http.protocol.HttpContext

/**
 * A class which makes httpclient follow redirects for all http methods.
 * This has been introduced to overcome a regression caused by switching to apache httpclient as the transport mechanism for publishing (https://issues.gradle.org/browse/GRADLE-3312)
 * The rational for httpclient not following redirects, by default, can be found here: https://issues.apache.org/jira/browse/HTTPCLIENT-860
 */
class AlwaysFollowAndPreserveMethodRedirectStrategy : DefaultRedirectStrategy() {
    override fun isRedirectable(method: String?): Boolean {
        return true
    }

    @Throws(ProtocolException::class)
    override fun getRedirect(request: HttpRequest, response: HttpResponse?, context: HttpContext?): HttpUriRequest? {
        val uri = this.getLocationURI(request, response, context)
        val method = request.getRequestLine().getMethod()
        if (method.equals(HttpHead.METHOD_NAME, ignoreCase = true)) {
            return HttpHead(uri)
        } else if (method.equals(HttpPost.METHOD_NAME, ignoreCase = true)) {
            return this.copyEntity(HttpPost(uri), request)
        } else if (method.equals(HttpPut.METHOD_NAME, ignoreCase = true)) {
            return this.copyEntity(HttpPut(uri), request)
        } else if (method.equals(HttpDelete.METHOD_NAME, ignoreCase = true)) {
            return HttpDelete(uri)
        } else if (method.equals(HttpTrace.METHOD_NAME, ignoreCase = true)) {
            return HttpTrace(uri)
        } else if (method.equals(HttpOptions.METHOD_NAME, ignoreCase = true)) {
            return HttpOptions(uri)
        } else if (method.equals(HttpPatch.METHOD_NAME, ignoreCase = true)) {
            return this.copyEntity(HttpPatch(uri), request)
        } else {
            return HttpGet(uri)
        }
    }

    private fun copyEntity(redirect: HttpEntityEnclosingRequestBase, original: HttpRequest?): HttpUriRequest {
        if (original is HttpEntityEnclosingRequest) {
            redirect.setEntity(original.getEntity())
        }
        return redirect
    }
}
