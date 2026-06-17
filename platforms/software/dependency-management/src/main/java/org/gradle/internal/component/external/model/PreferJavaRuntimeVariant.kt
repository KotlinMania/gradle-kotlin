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
import com.google.common.collect.ImmutableMap
import org.gradle.api.Action
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import javax.inject.Inject

/**
 * When no consumer attributes are provided, prefer the Java runtime variant over the API variant.
 *
 * Gradle has long assumed that, by default, consumers of a maven repository require the _runtime_ variant
 * of the published library.
 * The following disambiguation rule encodes this assumption for the case where a java library is published
 * with variants using Gradle module metadata. This will allow us to migrate to consuming the new module
 * metadata format by default without breaking a bunch of consumers that depend on this assumption,
 * declaring no preference for a particular variant.
 */
@ServiceScope(Scope.BuildSession::class)
class PreferJavaRuntimeVariant @Inject constructor(
    instantiator: NamedObjectInstantiator,
    schemaFactory: ImmutableAttributesSchemaFactory
) {
    val schema: ImmutableAttributesSchema

    init {
        this.schema = create(instantiator, schemaFactory)
    }

    private class PreferRuntimeVariantUsageDisambiguationRule(private val runtimeUsage: Usage) : Action<MultipleCandidatesDetails<Usage>> {
        override fun execute(details: MultipleCandidatesDetails<Usage>) {
            if (details.getConsumerValue() == null) {
                val candidates = details.getCandidateValues()
                if (candidates.contains(runtimeUsage)) {
                    details.closestMatch(runtimeUsage)
                }
            }
        }
    }

    private class PreferJarVariantUsageDisambiguationRule(private val jarLibraryElements: LibraryElements) : Action<MultipleCandidatesDetails<LibraryElements>> {
        override fun execute(details: MultipleCandidatesDetails<LibraryElements>) {
            if (details.getConsumerValue() == null) {
                val candidates = details.getCandidateValues()
                if (candidates.contains(jarLibraryElements)) {
                    details.closestMatch(jarLibraryElements)
                }
            }
        }
    }

    companion object {
        private fun create(
            instantiator: NamedObjectInstantiator,
            schemaFactory: ImmutableAttributesSchemaFactory
        ): ImmutableAttributesSchema {
            val runtimeUsage = instantiator.named<Usage>(Usage::class.java, Usage.JAVA_RUNTIME)
            val jarLibraryElements = instantiator.named<LibraryElements>(LibraryElements::class.java, LibraryElements.JAR)

            val usageDisambiguationRule = PreferRuntimeVariantUsageDisambiguationRule(runtimeUsage)
            val formatDisambiguationRule = PreferJarVariantUsageDisambiguationRule(jarLibraryElements)

            val strategies =
                ImmutableMap.of<Attribute<*>, ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<*>>(
                    Usage.USAGE_ATTRIBUTE,
                    ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<Usage?>(
                        ImmutableList.of<Action<in CompatibilityCheckDetails<Usage>>>(),
                        ImmutableList.of<Action<in MultipleCandidatesDetails<Usage>>>(usageDisambiguationRule)
                    ),
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<LibraryElements?>(
                        ImmutableList.of<Action<in CompatibilityCheckDetails<LibraryElements>>>(),
                        ImmutableList.of<Action<in MultipleCandidatesDetails<LibraryElements>>>(formatDisambiguationRule)
                    )
                )

            return schemaFactory.create(
                strategies,
                ImmutableList.of<Attribute<*>>(Usage.USAGE_ATTRIBUTE, LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE)
            )
        }
    }
}
