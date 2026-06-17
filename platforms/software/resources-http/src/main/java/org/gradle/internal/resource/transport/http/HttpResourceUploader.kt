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

import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ReadableContent
import org.gradle.internal.resource.transfer.ExternalResourceUploader
import java.io.IOException

class HttpResourceUploader(private val http: HttpClient) : ExternalResourceUploader {
    @Throws(IOException::class)
    override fun upload(resource: ReadableContent, destination: ExternalResourceName) {
        http.performRawPut(destination.getUri(), resource).use { response ->
            if (!response.isSuccessful()) {
                val effectiveUri = response.effectiveUri
                throw HttpErrorStatusCodeException(response.method, effectiveUri.toString(), response.statusCode, response.statusReason)
            }
        }
    }
}
