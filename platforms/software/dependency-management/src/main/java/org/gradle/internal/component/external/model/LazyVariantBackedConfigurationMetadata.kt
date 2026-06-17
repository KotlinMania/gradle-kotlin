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
package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.DisplayName
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ModuleConfigurationMetadata.getDependencies

/**
 * An immutable [ConfigurationMetadata] wrapper around a [ComponentVariant].
 */
internal class LazyVariantBackedConfigurationMetadata(
    componentId: ModuleComponentIdentifier,
    variant: ComponentVariant,
    componentLevelAttributes: ImmutableAttributes?,
    attributesFactory: AttributesFactory,
    private val variantMetadataRules: VariantMetadataRules
) : AbstractVariantBackedConfigurationMetadata(
    componentId, RuleAwareVariant(
        componentId, variant, attributesFactory, componentLevelAttributes,
        variantMetadataRules
    )
) {
    private var calculatedDependencies: MutableList<out ModuleDependencyMetadata?>? = null

    val dependencies: MutableList<out ModuleDependencyMetadata?>?
        get() {
            if (calculatedDependencies == null) {
                calculatedDependencies = variantMetadataRules.applyDependencyMetadataRules(getVariant(), super.getDependencies())
            }
            return calculatedDependencies
        }

    /**
     * This class wraps the component variant so that attribute rules are executed once
     * for all, and passed correctly to the various consumers. In particular, we need to make sure
     * that the attributes are the same whenever we resolve the graph for dependencies and artifacts.
     */
    private class RuleAwareVariant(
        private val componentId: ModuleComponentIdentifier,
        private val delegate: ComponentVariant,
        private val attributesFactory: AttributesFactory,
        private val componentLevelAttributes: ImmutableAttributes?,
        private val variantMetadataRules: VariantMetadataRules
    ) : ComponentVariant {
        private var computedAttributes: ImmutableAttributes? = null
        private var computedCapabilities: ImmutableCapabilities? = null
        private var computedArtifacts: ImmutableList<out ComponentArtifactMetadata?>? = null

        val name: String
            get() = delegate.name!!

        val identifier: VariantResolveMetadata.Identifier
            get() = delegate.identifier

        override fun asDescribable(): DisplayName? {
            return delegate.asDescribable()
        }

        val attributes: ImmutableAttributes
            /**
             * Returns the complete set of attributes of this variant, which consists of a view of the union
             * of the component level attributes and the variant attributes as found in metadata, potentially
             * modified by rules.
             *
             * @return the updated variant attributes
             */
            get() {
                if (computedAttributes == null) {
                    computedAttributes = variantMetadataRules.applyVariantAttributeRules(delegate, mergeComponentAndVariantAttributes(delegate.attributes)!!)
                }
                return computedAttributes
            }

        val artifacts: ImmutableList<out ComponentArtifactMetadata?>
            get() {
                if (computedArtifacts == null) {
                    computedArtifacts =
                        variantMetadataRules.applyVariantFilesMetadataRulesToArtifacts<ComponentArtifactMetadata?>(delegate, delegate.artifacts, componentId)
                }
                return computedArtifacts
            }

        override fun getDependencies(): ImmutableList<out ComponentVariant.Dependency?>? {
            return delegate.getDependencies()
        }

        override fun getDependencyConstraints(): ImmutableList<out ComponentVariant.DependencyConstraint?>? {
            return delegate.getDependencyConstraints()
        }

        override fun getFiles(): ImmutableList<out ComponentVariant.File?>? {
            return delegate.getFiles()
        }

        val capabilities: ImmutableCapabilities
            get() {
                if (computedCapabilities == null) {
                    computedCapabilities = variantMetadataRules.applyCapabilitiesRules(delegate, delegate.capabilities)
                }
                return computedCapabilities!!
            }

        override fun isExternalVariant(): Boolean {
            return delegate.isExternalVariant()
        }

        override fun isEligibleForCaching(): Boolean {
            return delegate.isEligibleForCaching()
        }

        fun mergeComponentAndVariantAttributes(variantAttributes: AttributeContainerInternal): AttributeContainerInternal? {
            return attributesFactory.concat(componentLevelAttributes, variantAttributes.asImmutable())
        }
    }
}
