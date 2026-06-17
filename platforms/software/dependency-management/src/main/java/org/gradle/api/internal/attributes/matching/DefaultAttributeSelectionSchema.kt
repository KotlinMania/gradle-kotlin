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
package org.gradle.api.internal.attributes.matching

import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.CompatibilityCheckResult
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.internal.component.model.DefaultCompatibilityCheckResult
import org.gradle.internal.component.model.DefaultMultipleCandidateResult
import java.util.stream.Collectors
import java.util.stream.IntStream

/**
 * Default implementation of [AttributeSelectionSchema], based off of a backing
 * [schema][ImmutableAttributesSchema].
 *
 *
 * This implementation should rarely be used on its own, and should almost always be
 * wrapped in a [CachingAttributeSelectionSchema].
 */
class DefaultAttributeSelectionSchema(private val schema: ImmutableAttributesSchema) : AttributeSelectionSchema {
    override fun hasAttribute(attribute: Attribute<*>): Boolean {
        return schema.getAttributes().contains(attribute)
    }

    override fun <T> disambiguate(attribute: Attribute<T?>, requested: T?, candidates: MutableSet<T?>): MutableSet<T?> {
        val rules = schema.disambiguationRules<T?>(attribute)
        if (!rules.isEmpty()) {
            val result = DefaultMultipleCandidateResult<T?>(requested, candidates)
            for (rule in rules) {
                rule.execute(result)
                if (result.hasResult()) {
                    return result.getMatches()
                }
            }
        }

        if (requested != null && candidates.contains(requested)) {
            return mutableSetOf<T?>(requested)
        }

        return null
    }

    override fun <T> matchValue(attribute: Attribute<T?>, requested: T?, candidate: T?): Boolean {
        if (requested == candidate) {
            return true
        }

        val rules = schema.compatibilityRules<T?>(attribute)
        if (!rules.isEmpty()) {
            val result: CompatibilityCheckResult<T?> = DefaultCompatibilityCheckResult<T?>(requested, candidate)
            for (rule in rules) {
                rule.execute(result)
                if (result.hasResult()) {
                    return result.isCompatible()
                }
            }
        }

        return false
    }

    override fun getAttribute(name: String): Attribute<*> {
        return schema.getAttributeByName(name)!!
    }

    override fun collectExtraAttributes(candidateAttributeSets: Array<ImmutableAttributes>, requested: ImmutableAttributes): Array<Attribute<*>> {
        val extraAttributes: MutableSet<Attribute<*>> = LinkedHashSet<Attribute<*>>()
        for (attributes in candidateAttributeSets) {
            extraAttributes.addAll(attributes.keySet())
        }
        removeSameAttributes(requested, extraAttributes)
        val extraAttributesArray = extraAttributes.toTypedArray<Attribute<*>>()
        for (i in extraAttributesArray.indices) {
            // Some of these attributes might be weakly typed, e.g. coming as Strings from an
            // artifact repository. We always check whether the schema has a more strongly typed
            // version of an attribute and use that one instead to apply its disambiguation rules.
            extraAttributesArray[i] = tryRehydrate(extraAttributesArray[i])
        }
        return extraAttributesArray
    }

    override fun orderByPrecedence(requested: MutableCollection<Attribute<*>>): AttributeSelectionSchema.PrecedenceResult {
        if (schema.getAttributeDisambiguationPrecedence().isEmpty()) {
            // If no attribute precedence has been set anywhere, we can just iterate in order
            return AttributeSelectionSchema.PrecedenceResult(IntStream.range(0, requested.size).boxed().collect(Collectors.toList()))
        }

        // Populate requested attribute -> position in requested attribute list
        val remaining: MutableMap<String, Int> = LinkedHashMap<String, Int>()
        var position = 0
        for (requestedAttribute in requested) {
            remaining.put(requestedAttribute.getName(), position++)
        }

        val sorted: MutableList<Int> = ArrayList<Int>(remaining.size)

        // Add attribute index to sorted in the order of precedence
        for (preferredAttribute in schema.getAttributeDisambiguationPrecedence()) {
            if (requested.contains(preferredAttribute)) {
                sorted.add(remaining.remove(preferredAttribute.getName())!!)
            }
        }

        // If nothing was sorted, there were no attributes in the request that matched any attribute precedences
        if (sorted.isEmpty()) {
            // Iterate in order
            return AttributeSelectionSchema.PrecedenceResult(remaining.values)
        } else {
            // sorted now contains any requested attribute indices in the order they appear in
            // the consumer and producer's attribute precedences
            return AttributeSelectionSchema.PrecedenceResult(sorted, remaining.values)
        }
    }

    companion object {
        private fun removeSameAttributes(requested: ImmutableAttributes, extraAttributes: MutableSet<Attribute<*>>) {
            for (attribute in requested.keySet()) {
                val it = extraAttributes.iterator()
                while (it.hasNext()) {
                    val next = it.next()
                    if (next.getName() == attribute.getName()) {
                        it.remove()
                        break
                    }
                }
            }
        }
    }
}
