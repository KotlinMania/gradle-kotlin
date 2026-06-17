/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.internal.component.model

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.internal.component.external.model.ExternalComponentResolveMetadata
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveMetadata
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.lazy.Lazy
import org.gradle.internal.lazy.Lazy.Companion.locking
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Supplier
import java.util.stream.Collectors

/**
 * Default implementation of [ExternalModuleComponentGraphResolveState]
 *
 *
 * The aim is to create only a single instance of this type per component and reuse that
 * for all resolution that happens in a build tree. This isn't quite the case yet.
 */
open class DefaultExternalModuleComponentGraphResolveState<G : ExternalModuleComponentGraphResolveMetadata?, A : ExternalComponentResolveMetadata?>(
    instanceId: Long,
    graphMetadata: G?,
    private val legacyMetadata: A?,
    private val idGenerator: ComponentIdGenerator
) : AbstractComponentGraphResolveState<G?>(instanceId, graphMetadata), ExternalModuleComponentGraphResolveState {
    // The resolve state for each configuration of this component
    private val variants: ConcurrentMap<ModuleConfigurationMetadata, DefaultConfigurationGraphResolveState> = ConcurrentHashMap<ModuleConfigurationMetadata, DefaultConfigurationGraphResolveState>()

    // The variants to use for variant selection during graph resolution
    private val allVariantsForGraphResolution: Lazy<MutableList<out VariantGraphResolveState>?>

    init {
        this.allVariantsForGraphResolution = locking().of<MutableList<out VariantGraphResolveState>?>(Supplier {
            graphMetadata!!.variantsForGraphTraversal.stream()
                .map<ModuleConfigurationMetadata> { obj: Any? -> ModuleConfigurationMetadata::class.java.cast(obj) }
                .map<VariantGraphResolveState> { variant: ModuleConfigurationMetadata? -> resolveStateFor(variant!!).asVariant() }
                .collect(Collectors.toList())
        }
        )
    }

    override fun getId(): ModuleComponentIdentifier {
        return getMetadata()!!.getId()
    }

    @Deprecated("")
    override fun getLegacyMetadata(): A? {
        return legacyMetadata
    }

    override fun getArtifactMetadata(): ComponentArtifactResolveMetadata {
        val legacyMetadata: A? = getLegacyMetadata()
        return DefaultExternalModuleComponentGraphResolveState.ExternalArtifactResolveMetadata(legacyMetadata!!)
    }

    override fun getCandidatesForGraphVariantSelection(): GraphSelectionCandidates {
        return ExternalGraphSelectionCandidates(this)
    }

    protected fun resolveStateFor(configuration: ModuleConfigurationMetadata): ConfigurationGraphResolveState {
        return variants.computeIfAbsent(configuration) { c: ModuleConfigurationMetadata? -> newVariantState(configuration) }
    }

    private fun newVariantState(configuration: ModuleConfigurationMetadata): DefaultConfigurationGraphResolveState {
        return DefaultConfigurationGraphResolveState(idGenerator.nextVariantId(), configuration)
    }

    private class DefaultConfigurationGraphResolveState(private val instanceId: Long, private val configuration: ModuleConfigurationMetadata) : VariantGraphResolveState,
        ConfigurationGraphResolveState {
        private val artifactResolveState: DefaultConfigurationArtifactResolveState

        init {
            this.artifactResolveState = DefaultConfigurationArtifactResolveState(configuration)
        }

        override fun getInstanceId(): Long {
            return instanceId
        }

        override fun getName(): String {
            return configuration.getName()
        }

        override fun getAttributes(): ImmutableAttributes {
            return configuration.getAttributes()
        }

        override fun getCapabilities(): ImmutableCapabilities {
            return configuration.getCapabilities()
        }

        override fun getDependencies(): MutableList<out DependencyMetadata> {
            return configuration.getDependencies()
        }

        override fun getExcludes(): MutableList<out ExcludeMetadata> {
            return configuration.excludes
        }

        override fun getMetadata(): ConfigurationGraphResolveMetadata {
            return configuration
        }

        override fun asVariant(): VariantGraphResolveState {
            return this
        }

        override fun prepareForArtifactResolution(): VariantArtifactResolveState {
            return artifactResolveState
        }
    }

    private class DefaultConfigurationArtifactResolveState(private val configuration: ConfigurationMetadata) : VariantArtifactResolveState {
        override fun getAdhocArtifacts(dependencyArtifacts: MutableList<IvyArtifactName>): ImmutableList<ComponentArtifactMetadata> {
            val artifacts = ImmutableList.builderWithExpectedSize<ComponentArtifactMetadata>(dependencyArtifacts.size)
            for (dependencyArtifact in dependencyArtifacts) {
                artifacts.add(configuration.artifact(dependencyArtifact))
            }
            return artifacts.build()
        }

        override fun getArtifactVariants(): MutableSet<out VariantResolveMetadata> {
            return configuration.getArtifactVariants()
        }
    }

    protected open class ExternalArtifactResolveMetadata(private val metadata: ExternalComponentResolveMetadata) : ComponentArtifactResolveMetadata {
        override fun getId(): ComponentIdentifier {
            return metadata.id
        }

        override fun getModuleVersionId(): ModuleVersionIdentifier {
            return metadata.moduleVersionId
        }

        override fun getSources(): ModuleSources {
            return metadata.sources
        }

        override fun getAttributes(): ImmutableAttributes {
            return metadata.attributes
        }

        override fun getAttributesSchema(): ImmutableAttributesSchema {
            return metadata.attributesSchema
        }
    }

    private class ExternalGraphSelectionCandidates(private val component: DefaultExternalModuleComponentGraphResolveState<*, *>) : GraphSelectionCandidates {
        private val variants: MutableList<out VariantGraphResolveState>

        init {
            this.variants = component.allVariantsForGraphResolution.get()!!
        }

        override fun getVariantsForAttributeMatching(): MutableList<out VariantGraphResolveState> {
            return variants
        }

        override fun getLegacyVariant(): VariantGraphResolveState? {
            val defaultVariant = component.getMetadata()!!.getConfiguration(Dependency.DEFAULT_CONFIGURATION)
            if (defaultVariant == null) {
                return null
            }

            return component.resolveStateFor(defaultVariant as ModuleConfigurationMetadata).asVariant()
        }
    }
}
