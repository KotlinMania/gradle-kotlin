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
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.internal.component.external.model.ExternalModuleDependencyMetadata
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.GraphVariantSelector
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.VariantGraphResolveState

/**
 * Represents a dependency declared in an Ivy descriptor file.
 *
 *
 * This dependency metadata is bound to a source configuration, since Ivy resolves
 * target components differently based on the configuration that they are sourced from.
 *
 */
class IvyDependencyMetadata private constructor(
    private val configuration: ConfigurationMetadata,
    @JvmField val dependencyDescriptor: IvyDependencyDescriptor,
    reason: String?,
    endorsing: Boolean,
    artifacts: ImmutableList<IvyArtifactName>
) : ExternalModuleDependencyMetadata(reason, endorsing, artifacts) {
    @JvmOverloads
    constructor(configuration: ConfigurationMetadata, dependencyDescriptor: IvyDependencyDescriptor, reason: String? = null, endorsing: Boolean = false) : this(
        configuration,
        dependencyDescriptor,
        reason,
        endorsing,
        dependencyDescriptor.getConfigurationArtifacts(configuration)
    )

    override fun selectLegacyVariants(
        variantSelector: GraphVariantSelector,
        consumerAttributes: ImmutableAttributes,
        targetComponentState: ComponentGraphResolveState,
        consumerSchema: ImmutableAttributesSchema
    ): MutableList<out VariantGraphResolveState> {
        // We only want to use ivy's configuration selection mechanism when an ivy component is selecting
        // configurations from another ivy component.
        if (targetComponentState is IvyComponentGraphResolveState) {
            val ivyComponent = targetComponentState
            return dependencyDescriptor.selectLegacyConfigurations(configuration, ivyComponent, variantSelector.failureHandler)
        }

        // We have already verified that the target component does not support attribute matching,
        // so if it is not an ivy component, use the standard legacy selection mechanism.
        val selected = variantSelector.selectLegacyVariant(consumerAttributes, targetComponentState, consumerSchema, variantSelector.failureHandler)
        return mutableListOf<VariantGraphResolveState>(selected)
    }

    val excludes: ImmutableList<ExcludeMetadata>
        get() = dependencyDescriptor.getConfigurationExcludes(configuration.hierarchy)

    override fun withReason(reason: String): ModuleDependencyMetadata {
        return IvyDependencyMetadata(configuration, dependencyDescriptor, reason, isEndorsingStrictVersions, artifacts)
    }

    override fun withEndorseStrictVersions(endorse: Boolean): ModuleDependencyMetadata {
        return IvyDependencyMetadata(configuration, dependencyDescriptor, reason, endorse, artifacts)
    }

    override fun withRequested(newSelector: ModuleComponentSelector): ModuleDependencyMetadata {
        val newDescriptor = dependencyDescriptor.withRequested(newSelector)
        return IvyDependencyMetadata(configuration, newDescriptor, reason, isEndorsingStrictVersions, artifacts)
    }

    override fun withArtifacts(newArtifacts: ImmutableList<IvyArtifactName>): ModuleDependencyMetadata {
        return IvyDependencyMetadata(configuration, dependencyDescriptor, reason, isEndorsingStrictVersions, newArtifacts)
    }

    override fun withRequestedAndArtifacts(newSelector: ModuleComponentSelector, newArtifacts: ImmutableList<IvyArtifactName>): ModuleDependencyMetadata {
        val newDelegate = dependencyDescriptor.withRequested(newSelector)
        return IvyDependencyMetadata(configuration, newDelegate, reason, isEndorsingStrictVersions, newArtifacts)
    }

    fun withDescriptor(descriptor: IvyDependencyDescriptor): ModuleDependencyMetadata {
        return IvyDependencyMetadata(configuration, descriptor, reason, isEndorsingStrictVersions, dependencyDescriptor.getConfigurationArtifacts(configuration))
    }
}
