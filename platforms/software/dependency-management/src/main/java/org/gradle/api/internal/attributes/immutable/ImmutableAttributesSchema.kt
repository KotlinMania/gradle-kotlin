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
package org.gradle.api.internal.attributes.immutable

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import org.gradle.api.Action
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import java.util.Objects

/**
 * An immutable schema for describing the behavior of [attributes][Attribute].
 *
 *
 * Instances are created via a [ImmutableAttributesSchemaFactory].
 *
 * @see org.gradle.api.attributes.AttributesSchema
 */
class ImmutableAttributesSchema internal constructor(// package-private to allow access from ImmutableAttributesSchemaFactory
    val strategies: ImmutableMap<Attribute<*>, ImmutableAttributeMatchingStrategy<*>>,
    /**
     * Get the precedence of attributes in this schema.
     */
    val attributeDisambiguationPrecedence: ImmutableList<Attribute<*>>
) {
    // Computed values
    private val attributesByName: ImmutableMap<String, Attribute<*>>
    private val hashCode: Int

    // package-private to allow instantiation from ImmutableAttributesSchemaFactory
    init {
        this.attributesByName = computeAttributesByName(strategies)
        this.hashCode = computeHashCode(strategies, attributeDisambiguationPrecedence)
    }

    val attributes: ImmutableSet<Attribute<*>>
        /**
         * Get the attributes described by this schema.
         */
        get() = strategies.keys

    /**
     * Get the disambiguation rule for the given attribute.
     */
    fun <T> disambiguationRules(attribute: Attribute<T?>): ImmutableList<Action<in MultipleCandidatesDetails<T?>>> {
        val matchingStrategy = getStrategy<T?>(attribute)
        if (matchingStrategy != null) {
            return matchingStrategy.disambiguationRules
        }
        return ImmutableList.of<Action<in MultipleCandidatesDetails<T?>>>()
    }

    /**
     * Get the compatibility rule for the given attribute.
     */
    fun <T> compatibilityRules(attribute: Attribute<T?>): ImmutableList<Action<in CompatibilityCheckDetails<T?>>> {
        val matchingStrategy = getStrategy<T?>(attribute)
        if (matchingStrategy != null) {
            return matchingStrategy.compatibilityRules
        }
        return ImmutableList.of<Action<in CompatibilityCheckDetails<T?>>>()
    }

    /**
     * Get an attribute by name.
     */
    fun getAttributeByName(name: String): Attribute<*>? {
        return attributesByName.get(name)
    }

    fun <T> getStrategy(attribute: Attribute<T?>): ImmutableAttributeMatchingStrategy<T?>? {
        return strategies.get(attribute) as ImmutableAttributeMatchingStrategy<T?>?
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as ImmutableAttributesSchema
        return strategies == that.strategies &&
                this.attributeDisambiguationPrecedence == that.attributeDisambiguationPrecedence
    }

    override fun hashCode(): Int {
        return hashCode
    }

    class ImmutableAttributeMatchingStrategy<T>(
        val compatibilityRules: ImmutableList<Action<in CompatibilityCheckDetails<T?>>>,
        val disambiguationRules: ImmutableList<Action<in MultipleCandidatesDetails<T?>>>
    ) {
        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as ImmutableAttributeMatchingStrategy<*>
            return compatibilityRules == that.compatibilityRules &&
                    disambiguationRules == that.disambiguationRules
        }

        override fun hashCode(): Int {
            return Objects.hash(compatibilityRules, disambiguationRules)
        }
    }

    companion object {
        /**
         * An attribute schema that describes no attributes.
         */
        val EMPTY: ImmutableAttributesSchema = ImmutableAttributesSchema(
            ImmutableMap.of<Attribute<*>, ImmutableAttributeMatchingStrategy<*>>(),
            ImmutableList.of<Attribute<*>>()
        )

        private fun computeAttributesByName(strategies: ImmutableMap<Attribute<*>, *>): ImmutableMap<String, Attribute<*>> {
            val attributesByName = ImmutableMap.builder<String, Attribute<*>>()
            for (attribute in strategies.keys) {
                attributesByName.put(attribute.getName(), attribute)
            }

            // TODO: In some cases, two attributes may be registered with the same name.
            // This is something we should probably forbid upstream.
            return attributesByName.buildKeepingLast()
        }

        private fun computeHashCode(
            strategies: ImmutableMap<Attribute<*>, ImmutableAttributeMatchingStrategy<*>>,
            precedence: ImmutableList<Attribute<*>>
        ): Int {
            var result = strategies.hashCode()
            result = 31 * result + precedence.hashCode()
            return result
        }
    }
}
