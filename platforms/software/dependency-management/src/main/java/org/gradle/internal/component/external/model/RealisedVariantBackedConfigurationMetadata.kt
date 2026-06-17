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
package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.DisplayName

class RealisedVariantBackedConfigurationMetadata(id: ModuleComponentIdentifier, variant: ComponentVariant, componentLevelAttributes: ImmutableAttributes, attributesFactory: AttributesFactory) :
    AbstractVariantBackedConfigurationMetadata(
        id,
        ComponentAttributesAwareVariant(variant, attributesFactory, componentLevelAttributes),
        (variant as AbstractRealisedModuleComponentResolveMetadata.ImmutableRealisedVariantImpl).dependencyMetadata
    ) {
    /**
     * This class wraps the component variant so that the set of attributes returned by $[.getAttributes] includes both
     * the variant and the component attributes.
     * See also $[LazyVariantBackedConfigurationMetadata].RuleAwareVariant which serves a similar purpose.
     */
    private class ComponentAttributesAwareVariant(private val delegate: ComponentVariant, private val attributesFactory: AttributesFactory, private val componentLevelAttributes: ImmutableAttributes) :
        ComponentVariant {
        private var computedAttributes: ImmutableAttributes? = null

        val name: String
            get() = delegate.name!!

        val identifier: VariantResolveMetadata.Identifier
            get() = delegate.identifier

        override fun asDescribable(): DisplayName {
            return delegate.asDescribable()!!
        }

        val attributes: ImmutableAttributes
            /**
             * Returns the complete set of attributes of this variant, which consists of a view of the union
             * of the component level attributes and the variant attributes as found in metadata.
             *
             * @return the updated variant attributes
             */
            get() {
                if (computedAttributes == null) {
                    computedAttributes = mergeComponentAndVariantAttributes(delegate.attributes)
                }
                return computedAttributes
            }

        val artifacts: ImmutableList<out ComponentArtifactMetadata>
            get() = delegate.artifacts

        override fun getDependencies(): ImmutableList<out ComponentVariant.Dependency> {
            return delegate.dependencies
        }

        override fun getDependencyConstraints(): ImmutableList<out ComponentVariant.DependencyConstraint> {
            return delegate.dependencyConstraints
        }

        override fun getFiles(): ImmutableList<out ComponentVariant.File> {
            return delegate.files
        }

        val capabilities: ImmutableCapabilities
            get() = delegate.capabilities

        override fun isExternalVariant(): Boolean {
            return delegate.isExternalVariant()
        }

        override fun isEligibleForCaching(): Boolean {
            return delegate.isEligibleForCaching()
        }

        fun mergeComponentAndVariantAttributes(variantAttributes: AttributeContainerInternal): ImmutableAttributes {
            return attributesFactory.concat(componentLevelAttributes, variantAttributes.asImmutable())
        }
    }
}
