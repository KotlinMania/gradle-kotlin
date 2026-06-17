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

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSortedMap
import org.gradle.api.internal.artifacts.repositories.resolver.M2ResourcePattern
import org.gradle.api.internal.artifacts.repositories.resolver.MavenPattern
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern
import org.gradle.internal.hash.Hasher
import org.gradle.internal.scan.UsedByScanPlugin
import java.net.URI
import java.util.function.Consumer

class MavenRepositoryDescriptor private constructor(
    id: String?,
    name: String?,
    url: URI?,
    private val metadataResources: ImmutableList<ResourcePattern?>?,
    private val artifactResources: ImmutableList<ResourcePattern?>?,
    metadataSources: ImmutableList<String?>?,
    authenticated: Boolean,
    authenticationSchemes: ImmutableList<String?>?,
    private val artifactUrls: ImmutableList<URI>
) : UrlRepositoryDescriptor(id, name, url, metadataSources, authenticated, authenticationSchemes) {
    @UsedByScanPlugin("doesn't link against this type, but expects these values - See ResolveConfigurationDependenciesBuildOperationType")
    private enum class Property {
        ARTIFACT_URLS,
    }

    override fun getType(): Type {
        return Type.MAVEN
    }

    override fun getMetadataResources(): ImmutableList<ResourcePattern?>? {
        return metadataResources
    }

    override fun getArtifactResources(): ImmutableList<ResourcePattern?>? {
        return artifactResources
    }

    override fun addProperties(builder: ImmutableSortedMap.Builder<String?, Any?>) {
        super.addProperties(builder)
        builder.put(Property.ARTIFACT_URLS.name, artifactUrls)
    }

    class Builder(name: String?, url: URI?) : UrlRepositoryDescriptor.Builder<Builder?>(name, url) {
        private var artifactUrls: ImmutableList<URI>? = null

        fun setArtifactUrls(artifactUrls: MutableCollection<URI?>): Builder {
            this.artifactUrls = ImmutableList.copyOf<URI?>(artifactUrls)
            return this
        }

        fun create(): MavenRepositoryDescriptor {
            Preconditions.checkNotNull<ImmutableList<URI?>?>(artifactUrls)
            Preconditions.checkNotNull<ImmutableList<String?>?>(metadataSources)

            val metadataResources = ImmutableList.of<ResourcePattern?>(M2ResourcePattern(url, MavenPattern.M2_PATTERN))
            val artifactResourcesBuilder = ImmutableList.builderWithExpectedSize<ResourcePattern?>(1 + artifactUrls!!.size)
            artifactResourcesBuilder.add(M2ResourcePattern(url, MavenPattern.M2_PATTERN))
            for (rootUri in artifactUrls!!) {
                artifactResourcesBuilder.add(M2ResourcePattern(rootUri, MavenPattern.M2_PATTERN))
            }
            val artifactResources = artifactResourcesBuilder.build()

            val id = calculateId(MavenResolver::class.java, metadataResources, artifactResources, metadataSources, Consumer { hasher: Hasher? -> })

            return MavenRepositoryDescriptor(
                id,
                Preconditions.checkNotNull<String?>(name),
                url,
                metadataResources,
                artifactResources,
                metadataSources,
                Preconditions.checkNotNull<Boolean?>(authenticated)!!,
                Preconditions.checkNotNull<ImmutableList<String?>?>(authenticationSchemes),
                artifactUrls!!
            )
        }
    }
}
