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
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResourcePattern
import org.gradle.api.internal.artifacts.repositories.resolver.M2ResourcePattern
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern
import org.gradle.internal.hash.Hasher
import org.gradle.internal.scan.UsedByScanPlugin
import org.jspecify.annotations.NullMarked
import java.net.URI
import java.util.function.Consumer

class IvyRepositoryDescriptor private constructor(
    id: String?,
    name: String?,
    url: URI?,
    metadataSources: ImmutableList<String?>?,
    authenticated: Boolean,
    authenticationSchemes: ImmutableList<String?>?,
    private val ivyPatterns: ImmutableList<String?>,
    private val ivyResources: ImmutableList<ResourcePattern?>?,
    private val artifactPatterns: ImmutableList<String?>,
    private val artifactResources: ImmutableList<ResourcePattern?>?,
    private val layoutType: String,
    val isM2Compatible: Boolean
) : UrlRepositoryDescriptor(id, name, url, metadataSources, authenticated, authenticationSchemes) {
    @UsedByScanPlugin("doesn't link against this type, but expects these values - See ResolveConfigurationDependenciesBuildOperationType")
    private enum class Property {
        IVY_PATTERNS,
        ARTIFACT_PATTERNS,
        LAYOUT_TYPE,
        M2_COMPATIBLE
    }

    override fun getMetadataResources(): ImmutableList<ResourcePattern?>? {
        return ivyResources
    }

    override fun getArtifactResources(): ImmutableList<ResourcePattern?>? {
        return artifactResources
    }

    override fun getType(): Type {
        return Type.IVY
    }

    fun getArtifactPatterns(): MutableList<String?> {
        return artifactPatterns
    }

    override fun addProperties(builder: ImmutableSortedMap.Builder<String?, Any?>) {
        super.addProperties(builder)
        builder.put(Property.IVY_PATTERNS.name, ivyPatterns)
        builder.put(Property.ARTIFACT_PATTERNS.name, artifactPatterns)
        builder.put(Property.LAYOUT_TYPE.name, layoutType)
        builder.put(Property.M2_COMPATIBLE.name, this.isM2Compatible)
    }

    @NullMarked
    private class Resource(val baseUri: URI, val pattern: String)

    class Builder(name: String?, url: URI?) : UrlRepositoryDescriptor.Builder<Builder?>(name, url) {
        private val ivyPatterns: MutableList<String?> = ArrayList<String?>()
        private val artifactPatterns: MutableList<String?> = ArrayList<String?>()
        private var layoutType: String? = null
        private var m2Compatible: Boolean? = null

        // Artifact resources derived from other configuration
        private val ivyResources: MutableList<Resource> = ArrayList<Resource>()
        private val artifactResources: MutableList<Resource> = ArrayList<Resource>()

        fun addIvyPattern(declaredPattern: String?) {
            ivyPatterns.add(declaredPattern)
        }

        fun addIvyResource(baseUri: URI?, pattern: String) {
            if (baseUri != null) {
                ivyResources.add(Resource(baseUri, pattern))
            }
        }

        fun addArtifactPattern(declaredPattern: String?) {
            artifactPatterns.add(declaredPattern)
        }

        fun addArtifactResource(rootUri: URI?, pattern: String) {
            if (rootUri != null) {
                artifactResources.add(Resource(rootUri, pattern))
            }
        }

        fun setLayoutType(layoutType: String?): Builder {
            this.layoutType = layoutType
            return this
        }

        fun setM2Compatible(m2Compatible: Boolean): Builder {
            this.m2Compatible = m2Compatible
            return this
        }

        fun create(): IvyRepositoryDescriptor {
            Preconditions.checkNotNull<Boolean?>(m2Compatible)
            Preconditions.checkNotNull<ImmutableList<String?>?>(metadataSources)

            val ivyResourcesBuilder = ImmutableList.builderWithExpectedSize<ResourcePattern?>(ivyPatterns.size)
            for (resource in ivyResources) {
                ivyResourcesBuilder.add(toResourcePattern(resource.baseUri, resource.pattern))
            }
            val artifactResourcesBuilder = ImmutableList.builderWithExpectedSize<ResourcePattern?>(artifactPatterns.size + artifactResources.size)
            for (resource in artifactResources) {
                artifactResourcesBuilder.add(toResourcePattern(resource.baseUri, resource.pattern))
            }

            val effectiveIvyResources = ivyResourcesBuilder.build()
            val effectiveArtifactResources = artifactResourcesBuilder.build()

            val id = calculateId(IvyResolver::class.java, effectiveIvyResources, effectiveArtifactResources, metadataSources, Consumer { hasher: Hasher? -> hasher!!.putBoolean(m2Compatible!!) })

            return IvyRepositoryDescriptor(
                id,
                Preconditions.checkNotNull<String?>(name),
                url,
                metadataSources,
                Preconditions.checkNotNull<Boolean?>(authenticated)!!,
                Preconditions.checkNotNull<ImmutableList<String?>?>(authenticationSchemes),
                ImmutableList.copyOf<String?>(ivyPatterns),
                effectiveIvyResources,
                ImmutableList.copyOf<String?>(artifactPatterns),
                effectiveArtifactResources,
                Preconditions.checkNotNull<String?>(layoutType)!!,
                m2Compatible!!
            )
        }

        private fun toResourcePattern(baseUri: URI, pattern: String): ResourcePattern {
            return if (m2Compatible) M2ResourcePattern(baseUri, pattern) else IvyResourcePattern(baseUri, pattern)
        }
    }
}
