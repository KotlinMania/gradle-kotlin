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

import com.google.common.base.Objects
import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor
import org.gradle.internal.resolve.caching.ImplicitInputRecord
import org.gradle.internal.resolve.caching.ImplicitInputRecorder
import org.gradle.internal.resolve.caching.ImplicitInputsProvidingService
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.local.FileStore
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor
import java.io.InputStream
import java.net.URI

class ExternalRepositoryResourceAccessor(private val rootUri: URI, cacheAwareExternalResourceAccessor: CacheAwareExternalResourceAccessor, fileStore: FileStore<String>) : RepositoryResourceAccessor,
    ImplicitInputsProvidingService<String?, Long?, RepositoryResourceAccessor?> {
    private val rootUriAsString: String
    private val resourceResolver: ExternalResourceAccessor

    init {
        this.rootUriAsString = rootUri.toString()
        this.resourceResolver = DefaultExternalResourceAccessor(fileStore, cacheAwareExternalResourceAccessor)
    }

    override fun withResource(relativePath: String, action: Action<in InputStream>) {
        val location = ExternalResourceName(rootUri, relativePath)
        val resource = resourceResolver.resolveResource(location)
        if (resource != null) {
            resource.withContent(action)
        }
    }

    override fun withImplicitInputRecorder(registrar: ImplicitInputRecorder): RepositoryResourceAccessor {
        return RepositoryResourceAccessor { relativePath: String?, action: Action<in InputStream?>? ->
            val location = ExternalResourceName(rootUri, relativePath!!)
            val resource = resourceResolver.resolveResource(location)
            registrar.register<String?, Long?>(SERVICE_TYPE, ServiceCall(rootUriAsString + ";" + relativePath, hashFor(resource)))
            if (resource != null) {
                resource.withContent(action)
            }
        }
    }

    override fun isUpToDate(resource: String, oldValue: Long?): Boolean {
        val parts = resource.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        if (rootUriAsString != parts[0]) {
            // not the same provider
            return false
        }
        val externalResourceName = ExternalResourceName(rootUri, parts[1])
        val locallyAvailableExternalResource = resourceResolver.resolveResource(externalResourceName)
        return Objects.equal(oldValue, hashFor(locallyAvailableExternalResource))
    }

    private class ServiceCall(val input: String, private val hash: Long?) : ImplicitInputRecord<String?, Long?> {
        val output: Long
            get() = hash!!
    }

    companion object {
        private val SERVICE_TYPE: String = RepositoryResourceAccessor::class.java.getName()

        private fun hashFor(resource: LocallyAvailableExternalResource?): Long? {
            return if (resource == null) null else resource.getMetaData()!!.getLastModified()!!.getTime()
        }
    }
}
