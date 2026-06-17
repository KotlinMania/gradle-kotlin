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
package org.gradle.internal.component.external.model.ivy

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.Dependency
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentIdGenerator
import org.gradle.internal.component.model.ConfigurationGraphResolveState
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.DefaultExternalModuleComponentGraphResolveState
import org.gradle.internal.component.model.GraphSelectionCandidates
import org.gradle.internal.component.model.VariantGraphResolveState
import java.util.function.Function

/**
 * External component state implementation for ivy components.
 */
class DefaultIvyComponentGraphResolveState(instanceId: Long, metadata: IvyModuleResolveMetadata, idGenerator: ComponentIdGenerator) :
    DefaultExternalModuleComponentGraphResolveState<IvyModuleResolveMetadata?, IvyModuleResolveMetadata?>(instanceId, metadata, metadata, idGenerator), IvyComponentGraphResolveState {
    override fun getConfigurationNames(): MutableSet<String> {
        return getMetadata()!!.configurationNames
    }

    override fun getConfiguration(configurationName: String): ConfigurationGraphResolveState? {
        val configuration = getMetadata()!!.getConfiguration(configurationName)
        if (configuration == null) {
            return null
        } else {
            return resolveStateFor(configuration)
        }
    }

    private fun getConfigurationAsVariant(name: String): VariantGraphResolveState? {
        val configuration = getConfiguration(name)
        if (configuration == null) {
            return null
        }
        return configuration.asVariant()
    }


    public override fun getArtifactMetadata(): IvyComponentArtifactResolveMetadata {
        @Suppress("deprecation") val legacyMetadata: IvyModuleResolveMetadata? = getLegacyMetadata()
        return DefaultIvyComponentGraphResolveState.DefaultIvyComponentArtifactResolveMetadata(legacyMetadata!!)
    }

    override fun getCandidatesForGraphVariantSelection(): IvyComponentGraphResolveState.IvyGraphSelectionCandidates {
        return DefaultIvyGraphSelectionCandidates(
            super.getCandidatesForGraphVariantSelection(),
            Function { name: String -> this.getConfigurationAsVariant(name)!! }
        )
    }

    private class DefaultIvyComponentArtifactResolveMetadata(private val metadata: IvyModuleResolveMetadata) : ExternalArtifactResolveMetadata(
        metadata
    ), IvyComponentArtifactResolveMetadata {
        override fun getConfigurationArtifacts(configurationName: String): ImmutableList<out ComponentArtifactMetadata>? {
            val configuration: ConfigurationMetadata? = metadata.getConfiguration(configurationName)
            if (configuration != null) {
                return configuration.artifacts
            }
            return null
        }
    }

    private class DefaultIvyGraphSelectionCandidates(
        private val candidates: GraphSelectionCandidates,
        private val configurationSupplier: Function<String, VariantGraphResolveState>
    ) : IvyComponentGraphResolveState.IvyGraphSelectionCandidates {
        override fun getVariantsForAttributeMatching(): MutableList<out VariantGraphResolveState> {
            return candidates.getVariantsForAttributeMatching()!!
        }

        override fun getLegacyVariant(): VariantGraphResolveState? {
            return getVariantByConfigurationName(Dependency.DEFAULT_CONFIGURATION)
        }

        override fun getVariantByConfigurationName(name: String): VariantGraphResolveState? {
            return configurationSupplier.apply(name)
        }
    }
}
