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
package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ResourceExceptions
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

class DefaultExternalResourceAccessor(private val fileStore: FileStore<String>, private val resourceAccessor: CacheAwareExternalResourceAccessor) : ExternalResourceAccessor {
    override fun resolveUri(uri: URI): LocallyAvailableExternalResource? {
        return resolve(ExternalResourceName(uri))
    }

    override fun resolveResource(resource: ExternalResourceName): LocallyAvailableExternalResource? {
        return resolve(resource)
    }

    private fun resolve(resource: ExternalResourceName): LocallyAvailableExternalResource? {
        LOGGER.debug("Loading {}", resource)

        try {
            return resourceAccessor.getResource(resource, null, object : CacheAwareExternalResourceAccessor.DefaultResourceFileStore<String?>(fileStore) {
                override fun computeKey(): String {
                    return resource.toString()
                }
            }, null)
        } catch (e: Exception) {
            throw ResourceExceptions.getFailed(resource.getUri(), e)
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultExternalResourceAccessor::class.java)
    }
}
