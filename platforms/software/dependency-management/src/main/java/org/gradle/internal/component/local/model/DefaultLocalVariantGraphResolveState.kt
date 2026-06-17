/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.component.local.model

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.internal.component.model.VariantArtifactResolveState
import org.gradle.internal.model.CalculatedValue

/**
 * Default implementation of [LocalVariantGraphResolveState].
 */
class DefaultLocalVariantGraphResolveState(// Metadata
    private val instanceId: Long,
    private val metadata: LocalVariantGraphResolveMetadata,
    // State
    private val dependencies: CalculatedValue<VariantDependencyMetadata>,
    artifactSets: MutableSet<LocalVariantMetadata>
) : LocalVariantGraphResolveState {
    private val artifactState: DefaultLocalVariantArtifactResolveState

    init {
        this.dependencies = dependencies
        this.artifactState = DefaultLocalVariantArtifactResolveState(metadata.getId()!!.componentId, artifactSets)
    }

    override fun getInstanceId(): Long {
        return instanceId
    }

    override fun getName(): String {
        return metadata.getName()
    }

    override fun toString(): String {
        return metadata.toString()
    }

    override fun getMetadata(): LocalVariantGraphResolveMetadata {
        return metadata
    }

    override fun getAttributes(): ImmutableAttributes {
        return metadata.getAttributes()!!
    }

    override fun getCapabilities(): ImmutableCapabilities {
        return metadata.getCapabilities()!!
    }

    override fun getFiles(): MutableSet<LocalFileDependencyMetadata> {
        dependencies.finalizeIfNotAlready()
        return dependencies.get().files
    }

    override fun getDependencies(): MutableList<LocalOriginDependencyMetadata> {
        dependencies.finalizeIfNotAlready()
        return dependencies.get().dependencies
    }

    override fun getExcludes(): MutableList<out ExcludeMetadata> {
        dependencies.finalizeIfNotAlready()
        return dependencies.get().excludes
    }

    override fun prepareForArtifactResolution(): VariantArtifactResolveState {
        return artifactState
    }

    /**
     * The dependencies, dependency constraints, and excludes for this variant.
     */
    class VariantDependencyMetadata(
        val dependencies: MutableList<LocalOriginDependencyMetadata>,
        val files: MutableSet<LocalFileDependencyMetadata>,
        excludes: MutableList<ExcludeMetadata>
    ) {
        val excludes: ImmutableList<ExcludeMetadata>

        init {
            this.excludes = ImmutableList.copyOf<ExcludeMetadata>(excludes)
        }
    }

    private class DefaultLocalVariantArtifactResolveState(
        private val componentId: ComponentIdentifier,
        private val artifactSets: MutableSet<LocalVariantMetadata>
    ) : VariantArtifactResolveState {
        override fun getAdhocArtifacts(dependencyArtifacts: MutableList<IvyArtifactName>): ImmutableList<ComponentArtifactMetadata> {
            val artifacts = ImmutableList.builderWithExpectedSize<ComponentArtifactMetadata>(dependencyArtifacts.size)
            for (dependencyArtifact in dependencyArtifacts) {
                artifacts.add(getArtifactWithName(dependencyArtifact))
            }
            return artifacts.build()
        }

        fun getArtifactWithName(ivyArtifactName: IvyArtifactName): ComponentArtifactMetadata {
            for (artifactSet in getArtifactVariants()) {
                for (candidate in artifactSet.artifacts) {
                    if (candidate.getName() == ivyArtifactName) {
                        return candidate
                    }
                }
            }

            return MissingLocalArtifactMetadata(componentId, ivyArtifactName)
        }

        override fun getArtifactVariants(): MutableSet<LocalVariantMetadata> {
            return artifactSets
        }
    }
}
