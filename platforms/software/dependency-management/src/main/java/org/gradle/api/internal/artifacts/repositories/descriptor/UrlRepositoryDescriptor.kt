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
package org.gradle.api.internal.artifacts.repositories.descriptor

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSortedMap
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern
import org.gradle.api.internal.cache.StringInterner
import org.gradle.internal.hash.Hasher
import org.gradle.internal.hash.Hashing
import org.gradle.internal.scan.UsedByScanPlugin
import java.net.URI
import java.util.function.Consumer

abstract class UrlRepositoryDescriptor protected constructor(
    id: String?,
    name: String?,
    val url: URI?,
    val metadataSources: ImmutableList<String?>,
    val authenticated: Boolean,
    val authenticationSchemes: ImmutableList<String?>
) : RepositoryDescriptor(id, name) {
    @UsedByScanPlugin("doesn't link against this type, but expects these values - See ResolveConfigurationDependenciesBuildOperationType")
    enum class Property {
        URL,
        METADATA_SOURCES,
        AUTHENTICATED,
        AUTHENTICATION_SCHEMES,
    }

    abstract val metadataResources: ImmutableList<ResourcePattern?>?

    abstract val artifactResources: ImmutableList<ResourcePattern?>?

    override fun addProperties(builder: ImmutableSortedMap.Builder<String?, Any?>) {
        if (url != null) {
            builder.put(Property.URL.name, url)
        }
        builder.put(Property.METADATA_SOURCES.name, metadataSources)
        builder.put(Property.AUTHENTICATED.name, authenticated)
        builder.put(Property.AUTHENTICATION_SCHEMES.name, authenticationSchemes)
    }

    internal abstract class Builder<T : Builder<T?>?>(val name: String?, val url: URI?) {
        var metadataSources: ImmutableList<String?>? = null
        var authenticated: Boolean? = null
        var authenticationSchemes: ImmutableList<String?>? = null

        protected fun self(): T? {
            return this as T
        }

        fun setMetadataSources(metadataSources: MutableList<String?>): T? {
            this.metadataSources = ImmutableList.copyOf<String?>(metadataSources)
            return self()
        }

        fun setAuthenticated(authenticated: Boolean): T? {
            this.authenticated = authenticated
            return self()
        }

        fun setAuthenticationSchemes(authenticationSchemes: MutableList<String?>): T? {
            this.authenticationSchemes = ImmutableList.copyOf<String?>(authenticationSchemes)
            return self()
        }

        protected fun calculateId(
            implementation: Class<out ExternalResourceResolver?>,
            metadataResources: MutableList<ResourcePattern>,
            artifactResources: MutableList<ResourcePattern>,
            metadataSources: MutableList<String>,
            additionalInputs: Consumer<Hasher?>
        ): String {
            val cacheHasher = Hashing.newHasher()
            cacheHasher.putString(implementation.getName())
            cacheHasher.putInt(metadataResources.size)
            for (ivyPattern in metadataResources) {
                cacheHasher.putString(ivyPattern.getPattern())
            }
            cacheHasher.putInt(artifactResources.size)
            for (artifactPattern in artifactResources) {
                cacheHasher.putString(artifactPattern.getPattern())
            }
            cacheHasher.putInt(metadataResources.size)
            for (source in metadataSources) {
                cacheHasher.putString(source)
            }
            additionalInputs.accept(cacheHasher)
            return REPOSITORY_ID_INTERNER.intern(cacheHasher.hash().toString())
        }

        companion object {
            private val REPOSITORY_ID_INTERNER = StringInterner()
        }
    }
}
