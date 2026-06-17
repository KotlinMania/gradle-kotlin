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
package org.gradle.api.internal.attributes.matching

import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.ImmutableAttributes

/**
 * Exposes operations for working with attributes. These operations are intended to be
 * backed by rules defined in a [org.gradle.api.attributes.AttributesSchema].
 */
interface AttributeSelectionSchema {
    /**
     * Return true iff the given attribute is present in this schema.
     */
    fun hasAttribute(attribute: Attribute<*>): Boolean

    /**
     * Given a set of candidate attribute values (`candidates`) for a given `attribute`, produce
     * a set of matching values from within the candidate set based on the provided `requested` value.
     *
     * @param attribute The attribute being disambiguated.
     * @param requested The requested attribute. If null, `attribute` is an extra attribute.
     * @param candidates All candidate values. If a remaining candidates does not include a value
     * for `attribute`, null is not included in this set.
     *
     * @return A subset of `candidates` which contain matched attribute values. Or, null if no matches were found.
     */
    fun <T> disambiguate(attribute: Attribute<T?>, requested: T?, candidates: MutableSet<T?>): MutableSet<T?>?

    fun <T> matchValue(attribute: Attribute<T?>, requested: T?, candidate: T?): Boolean

    /**
     * Determine if two values are compatible with each other. This is a "two directional"
     * match. If the two values mach in any direction, this method returns true.
     */
    fun <T> weakMatchValue(attribute: Attribute<T?>, requested: T?, candidate: T?): Boolean {
        return matchValue<T?>(attribute, requested, candidate) || matchValue<T?>(attribute, candidate, requested)
    }

    /**
     * Attempt to "rehydrate" an attribute that was previously desugared.
     * Desugared attributes are converted from rich types to primitives
     * during serialization.
     *
     * @return The attribute in this schema that has the same name as the provided
     * attribute, or the provided attribute if no such attribute exists.
     */
    fun tryRehydrate(attribute: Attribute<*>): Attribute<*> {
        val typedAttribute = getAttribute(attribute.getName())
        if (typedAttribute == null) {
            return attribute
        }
        return typedAttribute
    }

    fun getAttribute(name: String): Attribute<*>?

    /**
     * Collects attributes that were present on the candidates, but which the consumer did not ask for.
     */
    fun collectExtraAttributes(candidates: Array<ImmutableAttributes>, requested: ImmutableAttributes): Array<Attribute<*>>?

    class PrecedenceResult(val sortedOrder: MutableList<Int>, val unsortedOrder: MutableCollection<Int>) {
        constructor(unsortedIndices: MutableCollection<Int>) : this(mutableListOf<Int>(), unsortedIndices)
    }

    /**
     * Given a set of attributes, order those attributes based on the precedence defined by
     * this schema.
     *
     * @param requested The attributes to order. **Must have a consistent iteration ordering and cannot contain duplicates**.
     *
     * @return The ordered attributes.
     */
    fun orderByPrecedence(requested: MutableCollection<Attribute<*>>): PrecedenceResult?
}
