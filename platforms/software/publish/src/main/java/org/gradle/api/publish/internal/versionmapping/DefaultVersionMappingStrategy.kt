/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.publish.internal.versionmapping

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ImmutableList
import com.google.common.collect.Multimap
import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.attributes.matching.AttributeMatcher
import org.gradle.api.internal.attributes.matching.ImmutableAttributesBackedMatchingCandidate
import org.gradle.api.model.ObjectFactory
import org.gradle.api.publish.VariantVersionMappingStrategy
import javax.inject.Inject

class DefaultVersionMappingStrategy @Inject constructor(
    private val objectFactory: ObjectFactory,
    private val configurations: ConfigurationContainer,
    private val schema: AttributesSchemaInternal,
    private val attributesFactory: AttributesFactory,
    private val attributeSchemaServices: AttributeSchemaServices
) : VersionMappingStrategyInternal {
    private val mappingsForAllVariants: MutableList<Action<in VariantVersionMappingStrategy>> = ArrayList<Action<in VariantVersionMappingStrategy>>(2)
    private val defaultConfigurations: MutableMap<ImmutableAttributesBackedMatchingCandidate, String> = HashMap<ImmutableAttributesBackedMatchingCandidate, String>()
    private val attributeBasedMappings: Multimap<ImmutableAttributesBackedMatchingCandidate, Action<in VariantVersionMappingStrategy>> =
        ArrayListMultimap.create<ImmutableAttributesBackedMatchingCandidate, Action<in VariantVersionMappingStrategy>>()

    private var matcher: AttributeMatcher? = null
        get() {
            if (field == null) {
                val immutableSchema = attributeSchemaServices.schemaFactory.create(schema)
                field = attributeSchemaServices.getMatcher(immutableSchema, ImmutableAttributesSchema.EMPTY)
            }

            return field
        }

    override fun allVariants(action: Action<in VariantVersionMappingStrategy>) {
        mappingsForAllVariants.add(action)
    }

    override fun <T> variant(attribute: Attribute<T?>, attributeValue: T?, action: Action<in VariantVersionMappingStrategy>) {
        val attributes = attributesFactory.of<T?>(attribute, attributeValue)
        attributeBasedMappings.put(ImmutableAttributesBackedMatchingCandidate(attributes), action)
    }

    override fun usage(usage: String, action: Action<in VariantVersionMappingStrategy>) {
        variant<Usage>(Usage.USAGE_ATTRIBUTE, objectFactory.named<Usage>(Usage::class.java, usage), action)
    }

    override fun defaultResolutionConfiguration(usage: String, defaultConfiguration: String) {
        val attributes = attributesFactory.of<Usage>(Usage.USAGE_ATTRIBUTE, objectFactory.named<Usage>(Usage::class.java, usage))
        defaultConfigurations.put(ImmutableAttributesBackedMatchingCandidate(attributes), defaultConfiguration)
    }

    override fun findStrategyForVariant(variantAttributes: ImmutableAttributes): VariantVersionMappingStrategyInternal {
        val strategy = createDefaultMappingStrategy(variantAttributes)
        // Apply strategies for "all variants"
        for (action in mappingsForAllVariants) {
            action.execute(strategy)
        }

        // Then use attribute specific mapping
        if (!attributeBasedMappings.isEmpty()) {
            val candidates: MutableList<ImmutableAttributesBackedMatchingCandidate> = ImmutableList.copyOf<ImmutableAttributesBackedMatchingCandidate>(attributeBasedMappings.keySet())
            val matches: MutableList<ImmutableAttributesBackedMatchingCandidate>? = this.matcher!!.matchMultipleCandidates<ImmutableAttributesBackedMatchingCandidate?>(candidates, variantAttributes)
            if (matches!!.size == 1) {
                val actions = attributeBasedMappings.get(matches.get(0))
                for (action in actions) {
                    action.execute(strategy)
                }
            } else if (matches.size > 1) {
                throw InvalidUserCodeException("Unable to find a suitable version mapping strategy for " + variantAttributes)
            }
        }
        return strategy
    }

    private fun createDefaultMappingStrategy(variantAttributes: ImmutableAttributes): DefaultVariantVersionMappingStrategy {
        val strategy = DefaultVariantVersionMappingStrategy(configurations)
        if (!defaultConfigurations.isEmpty()) {
            // First need to populate the default variant version mapping strategy with the default values
            // provided by plugins
            val candidates: MutableList<ImmutableAttributesBackedMatchingCandidate> = ImmutableList.copyOf<ImmutableAttributesBackedMatchingCandidate>(defaultConfigurations.keys)
            val matches: MutableList<ImmutableAttributesBackedMatchingCandidate>? = this.matcher!!.matchMultipleCandidates<ImmutableAttributesBackedMatchingCandidate?>(candidates, variantAttributes)
            for (match in matches!!) {
                strategy.setDefaultResolutionConfiguration(configurations.getByName(defaultConfigurations.get(match)!!))
            }
        }
        return strategy
    }
}
