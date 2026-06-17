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

import org.gradle.api.Action
import org.gradle.api.artifacts.DependencyConstraintMetadata
import org.gradle.api.artifacts.DependencyConstraintsMetadata
import org.gradle.api.artifacts.DirectDependenciesMetadata
import org.gradle.api.artifacts.DirectDependencyMetadata
import org.gradle.api.artifacts.MutableVariantFilesMetadata
import org.gradle.api.artifacts.VariantMetadata
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.MutableCapabilitiesMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.VariantMetadataRules
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser

/**
 * Adapts a mutable module component resolve metadata instance into a form that is suitable
 * for mutation through the Gradle DSL: we don't want to expose all the resolve component
 * metadata methods, only those which make sense, and that we can reason about safely. The adapter
 * is responsible for targetting variants subject to a rule.
 */
class VariantMetadataAdapter
/**
 * If `variantName` is null, this adapter applies to all variants within a component.
 */(
    private val variantName: String?,
    private val metadata: MutableModuleComponentResolveMetadata, private val instantiator: Instantiator,
    private val dependencyMetadataNotationParser: NotationParser<Any, DirectDependencyMetadata>,
    private val dependencyConstraintMetadataNotationParser: NotationParser<Any, DependencyConstraintMetadata>
) : VariantMetadata {
    override fun withDependencies(action: Action<in DirectDependenciesMetadata>) {
        metadata.variantMetadataRules!!.addDependencyAction(
            instantiator,
            dependencyMetadataNotationParser,
            dependencyConstraintMetadataNotationParser,
            VariantMetadataRules.VariantAction<DirectDependenciesMetadata?>(variantName, action)
        )
    }

    override fun withDependencyConstraints(action: Action<in DependencyConstraintsMetadata>) {
        metadata.variantMetadataRules!!.addDependencyConstraintAction(
            instantiator,
            dependencyMetadataNotationParser,
            dependencyConstraintMetadataNotationParser,
            VariantMetadataRules.VariantAction<DependencyConstraintsMetadata?>(variantName, action)
        )
    }

    override fun withCapabilities(action: Action<in MutableCapabilitiesMetadata>) {
        metadata.variantMetadataRules!!.addCapabilitiesAction(VariantMetadataRules.VariantAction<MutableCapabilitiesMetadata?>(variantName, action))
    }

    override fun withFiles(action: Action<in MutableVariantFilesMetadata>) {
        metadata.variantMetadataRules!!.addVariantFilesAction(VariantMetadataRules.VariantAction<MutableVariantFilesMetadata?>(variantName, action))
    }

    override fun attributes(action: Action<in AttributeContainer>): VariantMetadata {
        metadata.variantMetadataRules!!.addAttributesAction(metadata.attributesFactory, VariantMetadataRules.VariantAction<AttributeContainer?>(variantName, action))
        return this
    }

    override fun getAttributes(): AttributeContainer {
        return metadata.variantMetadataRules!!.getAttributes(variantName)
    }
}
