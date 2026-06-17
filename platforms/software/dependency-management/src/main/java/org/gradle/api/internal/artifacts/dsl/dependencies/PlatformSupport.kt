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
package org.gradle.api.internal.artifacts.dsl.dependencies

import com.google.common.base.Objects
import org.gradle.api.Action
import org.gradle.api.ActionConfiguration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.HasConfigurableAttributes
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.internal.artifacts.repositories.metadata.MavenVariantAttributesFactory
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.component.external.model.ComponentVariant
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import javax.inject.Inject

@ServiceScope(Scope.Global::class)
class PlatformSupport(instantiator: NamedObjectInstantiator) {
    private val library: Category
    val regularPlatformCategory: Category
    private val enforcedPlatform: Category

    init {
        library = instantiator.named<Category>(Category::class.java, Category.LIBRARY)
        this.regularPlatformCategory = instantiator.named<Category>(Category::class.java, Category.REGULAR_PLATFORM)
        enforcedPlatform = instantiator.named<Category>(Category::class.java, Category.ENFORCED_PLATFORM)
    }

    fun isTargetingPlatform(target: HasConfigurableAttributes<*>): Boolean {
        val category = target.getAttributes().getAttribute<Category>(Category.CATEGORY_ATTRIBUTE)
        return this.regularPlatformCategory == category || enforcedPlatform == category
    }

    fun configureSchema(attributesSchema: AttributesSchemaInternal) {
        configureCategoryDisambiguationRule(attributesSchema)
    }

    private fun configureCategoryDisambiguationRule(attributesSchema: AttributesSchema) {
        val categorySchema = attributesSchema.attribute<Category>(Category.CATEGORY_ATTRIBUTE)
        categorySchema.getDisambiguationRules().add(ComponentCategoryDisambiguationRule::class.java, Action { actionConfiguration: ActionConfiguration? ->
            actionConfiguration!!.params(library)
            actionConfiguration.params(this.regularPlatformCategory)
        })
    }

    fun <T> addPlatformAttribute(dependency: HasConfigurableAttributes<T?>, category: String) {
        dependency.attributes(Action { attributeContainer: AttributeContainer? ->
            attributeContainer!!.attribute<Category>(
                Category.CATEGORY_ATTRIBUTE,
                attributeContainer.named<Category>(Category::class.java, category)
            )
        })
    }

    class ComponentCategoryDisambiguationRule @Inject internal constructor(val library: Category, val platform: Category) : AttributeDisambiguationRule<Category?> {
        override fun execute(details: MultipleCandidatesDetails<Category>) {
            val consumerValue = details.getConsumerValue()
            if (consumerValue == null) {
                val candidateValues = details.getCandidateValues()
                if (candidateValues.contains(library)) {
                    // default to library
                    details.closestMatch(library)
                } else if (candidateValues.contains(platform)) {
                    // default to normal platform when only platforms are available and nothing has been requested
                    details.closestMatch(platform)
                }
            }
        }
    }

    companion object {
        fun configureFailureHandler(handler: ResolutionFailureHandler) {
            // TODO: This should not be here.
            // This failure handler has nothing to do with platforms.
            // This should live in JavaEcosystemSupport.
            handler.addFailureDescriber<NoCompatibleVariantsFailure?>(NoCompatibleVariantsFailure::class.java, TargetJVMVersionOnLibraryTooNewFailureDescriber::class.java)
        }

        /**
         * Checks if the variant is an `enforced-platform` one.
         *
         *
         * This method is designed to be called on parsed metadata and thus interacts with the `String` version of the attribute.
         *
         * @param variant the variant to test
         * @return `true` if this represents an `enforced-platform`, `false` otherwise
         */
        fun hasForcedDependencies(variant: ComponentVariant): Boolean {
            return Objects.equal(variant.attributes.getAttribute(MavenVariantAttributesFactory.CATEGORY_ATTRIBUTE), Category.ENFORCED_PLATFORM)
        }
    }
}
