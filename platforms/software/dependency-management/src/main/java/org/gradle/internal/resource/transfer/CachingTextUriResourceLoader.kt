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
package org.gradle.internal.resource.transfer

import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceAccessor
import org.gradle.internal.file.RelativeFilePathResolver
import org.gradle.internal.resource.DownloadedUriTextResource
import org.gradle.internal.resource.ResourceExceptions
import org.gradle.internal.resource.TextResource
import org.gradle.internal.resource.TextUriResourceLoader
import org.gradle.internal.resource.UriTextResource
import java.net.URI

class CachingTextUriResourceLoader(private val externalResourceAccessor: ExternalResourceAccessor, private val cachedSchemes: MutableSet<String>, private val resolver: RelativeFilePathResolver) :
    TextUriResourceLoader {
    override fun loadUri(description: String, source: URI): TextResource {
        if (isCacheable(source)) {
            val resource = externalResourceAccessor.resolveUri(source)
            if (resource == null) {
                throw ResourceExceptions.getMissing(source)
            }
            val metaData = resource.getMetaData()
            val contentType = if (metaData == null) null else metaData.getContentType()
            return DownloadedUriTextResource(description, source, contentType, resource.getFile(), resolver)
        }

        // fallback to old behavior of always loading the resource
        return UriTextResource(description, source, resolver)
    }

    private fun isCacheable(source: URI): Boolean {
        return isCacheableScheme(source) && isCacheableResource(source)
    }

    /**
     * Is `source` a cacheable transport scheme (e.g., http)?
     *
     * Schemes like file:// are not cacheable.
     */
    private fun isCacheableScheme(source: URI): Boolean {
        return cachedSchemes.contains(source.getScheme())
    }

    /**
     * Is `source` a cacheable URL?
     *
     * A URI is not cacheable if it uses a query string because our underlying infrastructure
     * relies on paths to uniquely identify resources and not path+query components.
     */
    private fun isCacheableResource(source: URI): Boolean {
        return source.getRawQuery() == null
    }
}
