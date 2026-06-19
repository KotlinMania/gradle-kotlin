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
package org.gradle.internal.resource.transfer

import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ResourceExceptions
import java.io.IOException

abstract class AbstractExternalResourceAccessor : ExternalResourceAccessor {
    @Throws(ResourceException::class)
    override fun <T> withContent(location: ExternalResourceName, revalidate: Boolean, action: ExternalResource.ContentAndMetadataAction<T?>): T? {
        val response = openResource(location, revalidate)
        if (response == null) {
            return null
        }

        try {
            response.openStream().use { inputStream ->
                response.use { responseCloser ->
                    return action.execute(inputStream, responseCloser.getMetaData())
                }
            }
        } catch (e: IOException) {
            throw ResourceExceptions.getFailed(location.uri, e)
        }
    }

    @Throws(ResourceException::class)
    protected abstract fun openResource(location: ExternalResourceName, revalidate: Boolean): ExternalResourceReadResponse?
}
