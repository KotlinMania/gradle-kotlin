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

import com.google.common.collect.Sets
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.ImmutableAttributesEntry
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder
import java.util.Arrays
import java.util.BitSet
import java.util.function.IntFunction

/**
 * This is the heart of the attribute matching algorithm and is used whenever there are multiple candidates to choose from.
 *
 *  1.
 * For each candidate, check whether its attribute values are compatible (according to the [AttributeSelectionSchema]) with the values that were requested.
 * Any missing or extra attributes on the candidate are ignored at this point. If there are 0 or 1 compatible candidates after this, return that as the result.
 *
 *  1.
 * If there are multiple candidates matching the requested values, check whether one of the candidates is a strict superset of all the others, i.e. it matched more
 * of the requested attributes than any other one. Missing or extra attributes don't count. If such a strict superset candidate exists, it is returned as the single match.
 *
 *  1.
 * Otherwise continue with disambiguation. Disambiguation iterates through the attributes and presents the different values to the [AttributeSelectionSchema].
 * The schema can declare a subset of these values as preferred. Candidates whose value is not in that subset are rejected. If a single candidate remains after
 * disambiguating the requested attributes, this candidate is returned. Otherwise disambiguation continues with extra attributes.
 *
 *  1.
 * If we run out of candidates during this process, the intersection of preferred values is not satisfied by any of them, so there could be multiple valid choices.
 * In that case return all compatible candidates, as none of them is preferable over any other.
 *
 *  1.
 * If there are one or more candidates left after disambiguation, return those.
 *
 *
 *
 * Implementation notes:
 *
 *
 * For matching and disambiguating the requested values, we keep a table of candidate values to avoid recomputing them. The table has one row for each candidate and one column for each attribute.
 * The cells contain the values of the candidate for the given attribute. This table is packed into a single flat array in order to reduce memory usage and increase data locality.
 *
 *
 * The information which candidates are compatible and which candidates are still valid during disambiguation is kept in two [BitSet]s. The nth bit is set if the nth candidate
 * is compatible. The longest match is kept using two integers, one containing the length of the match, the other containing the index of the candidate that was the longest.
 *
 *
 */
internal class MultipleCandidateMatcher(
    private val schema: AttributeSelectionSchema,
    private val candidates: Array<ImmutableAttributes>,
    private val requested: ImmutableAttributes,
    private val explanationBuilder: AttributeMatchingExplanationBuilder
) {
    private val requestedAttributes: MutableList<Attribute<*>>
    private val compatible: BitSet

    /**
     * Cache of requested attribute values.
     */
    private val requestedAttributeValues: Array<Any?>

    /**
     * Cache of candidate attribute values for each requested attribute. Initialized by [.findCompatibleCandidates].
     *
     * @see .setCandidateValue
     * @see .getCandidateValue
     */
    private val candidateValues: Array<Any?>

    private var candidateWithLongestMatch = 0
    private var lengthOfLongestMatch = 0

    private var remaining: BitSet? = null

    init {
        this.requestedAttributes = requested.keySet().asList()
        this.requestedAttributeValues = getRequestedValues(requestedAttributes, requested)

        this.candidateValues = arrayOfNulls<Any>(candidates.size * requestedAttributes.size)

        this.compatible = BitSet(candidates.size)
        compatible.set(0, candidates.size)
    }

    val matches: IntArray
        get() {
            findCompatibleCandidates()
            if (compatible.cardinality() <= 1) {
                return getCandidates(compatible)
            }
            if (longestMatchIsSuperSetOfAllOthers()) {
                val o = candidates[candidateWithLongestMatch]
                explanationBuilder.candidateIsSuperSetOfAllOthers(o)
                return intArrayOf(candidateWithLongestMatch)
            }
            return disambiguateCompatibleCandidates()
        }

    private fun findCompatibleCandidates() {
        if (requested.isEmpty()) {
            // Avoid iterating on candidates if there's no requested attribute
            return
        }
        for (c in candidates.indices) {
            matchCandidate(c)
        }
    }

    private fun matchCandidate(c: Int) {
        var matchLength = 0

        for (a in requestedAttributes.indices) {
            val result = recordAndMatchCandidateValue(c, a)
            if (result == MatchResult.NO_MATCH) {
                compatible.clear(c)
                return
            }
            if (result == MatchResult.MATCH) {
                matchLength++
            }
        }

        if (matchLength > lengthOfLongestMatch) {
            lengthOfLongestMatch = matchLength
            candidateWithLongestMatch = c
        }
    }

    private fun recordAndMatchCandidateValue(c: Int, a: Int): MatchResult {
        val requestedValue = requestedAttributeValues[a]
        val attribute = requestedAttributes.get(a)
        val candidateEntry = candidates[c].findEntry(attribute.getName())

        if (candidateEntry == null) {
            setCandidateValue(c, a, null)
            explanationBuilder.candidateAttributeMissing(candidates[c], attribute, requestedValue!!)
            return MatchResult.MISSING
        }

        val coercedValue: Any = candidateEntry.coerce(attribute)
        setCandidateValue(c, a, coercedValue)

        if (unsafeMatchValue(attribute, requestedValue!!, coercedValue)) {
            return MatchResult.MATCH
        }
        explanationBuilder.candidateAttributeDoesNotMatch(candidates[c], attribute, requestedValue, candidateEntry)
        return MatchResult.NO_MATCH
    }

    private fun unsafeMatchValue(
        attribute: Attribute<*>,
        requested: Any,
        candidate: Any
    ): Boolean {
        return schema.matchValue<Any>(
            uncheckedCast<Attribute<Any>?>(attribute)!!,
            uncheckedCast<Any?>(requested)!!,
            uncheckedCast<Any?>(candidate)!!
        )
    }

    private fun longestMatchIsSuperSetOfAllOthers(): Boolean {
        var c = compatible.nextSetBit(0)
        while (c >= 0) {
            if (c == candidateWithLongestMatch) {
                c = compatible.nextSetBit(c + 1)
                continue
            }
            var lengthOfOtherMatch = 0
            for (a in requestedAttributes.indices) {
                if (getCandidateValue(c, a) == null) {
                    continue
                }
                lengthOfOtherMatch++
                if (getCandidateValue(candidateWithLongestMatch, a) == null) {
                    return false
                }
            }
            if (lengthOfOtherMatch == lengthOfLongestMatch) {
                return false
            }
            c = compatible.nextSetBit(c + 1)
        }
        return true
    }

    private fun disambiguateCompatibleCandidates(): IntArray {
        remaining = BitSet(candidates.size)
        remaining!!.or(compatible)

        disambiguateWithRequestedAttributeValues()
        if (remaining!!.cardinality() == 0) {
            return getCandidates(compatible)
        } else if (remaining!!.cardinality() == 1) {
            return getCandidates(remaining!!)
        }

        val extraAttributes = schema.collectExtraAttributes(candidates, requested)
        if (remaining!!.cardinality() > 1) {
            disambiguateWithExtraAttributes(extraAttributes)
        }
        if (remaining!!.cardinality() > 1) {
            disambiguateWithRequestedAttributeKeys(extraAttributes)
        }
        return if (remaining!!.cardinality() == 0) getCandidates(compatible) else getCandidates(remaining!!)
    }

    private fun disambiguateWithRequestedAttributeKeys(extraAttributes: Array<Attribute<*>>) {
        if (requestedAttributes.isEmpty()) {
            return
        }
        for (extraAttribute in extraAttributes) {
            // We consider only extra attributes which are NOT on every candidate:
            // Because they are EXTRA attributes, we consider that a
            // candidate which does NOT provide this value is a better match
            val candidateCount = candidates.size
            val any = BitSet(candidateCount)
            for (c in 0..<candidateCount) {
                val candidateAttributeSet = candidates[c]
                if (candidateAttributeSet.findEntry(extraAttribute.getName()) != null) {
                    any.set(c)
                }
            }
            if (any.cardinality() > 0 && any.cardinality() != candidateCount) {
                // there is at least one candidate which does NOT provide this attribute
                remaining!!.andNot(any)
                if (remaining!!.cardinality() == 0) {
                    // there are no left candidate, do not bother checking other attributes
                    break
                }
            }
        }
    }

    private fun disambiguateWithRequestedAttributeValues() {
        // We need to take the existing requested attributes and sort them in "precedence" order
        // This returns a structure that tells us the order of requestedAttributes by their index in
        // requestedAttributes.
        //
        // If the requested attributes are [ A, B, C ]
        // If the attribute precedence is [ C, A ]
        // The indices are [ A: 0, B: 1, C: 2 ]
        // The sorted indices are [ 2, 0 ]
        // The unsorted indices are [ 1 ]
        //
        val precedenceResult = schema.orderByPrecedence(requested.keySet())

        for (a in precedenceResult.getSortedOrder()) {
            disambiguateRequestedAttribute(a)
            if (remaining!!.cardinality() == 0) {
                return
            } else if (remaining!!.cardinality() == 1) {
                // If we're down to one candidate and the attribute has a known precedence,
                // we can stop now and choose this candidate as the match.
                return
            }
        }
        // If the attribute does not have a known precedence, then we cannot stop
        // until we've disambiguated all of the attributes.
        for (a in precedenceResult.getUnsortedOrder()) {
            disambiguateRequestedAttribute(a)
            if (remaining!!.cardinality() == 0) {
                return
            }
        }
    }

    private fun disambiguateRequestedAttribute(a: Int) {
        val candidateValues: MutableSet<Any> = getCandidateValues<Any>(compatible, IntFunction { c: Int -> getCandidateValue(c, a) })
        if (candidateValues.size <= 1) {
            return
        }

        val matches = unsafeDisambiguate(requestedAttributes.get(a), requestedAttributeValues[a], candidateValues)
        if (matches != null && matches.size < candidateValues.size) {
            // Remove any candidates which do not satisfy the disambiguation rule.
            var c = remaining!!.nextSetBit(0)
            while (c >= 0) {
                if (!matches.contains(getCandidateValue(c, a))) {
                    remaining!!.clear(c)
                }
                c = remaining!!.nextSetBit(c + 1)
            }
        }
    }

    fun unsafeDisambiguate(
        attribute: Attribute<*>,
        requested: Any?,
        candidates: MutableSet<Any>
    ): MutableSet<Any>? {
        return schema.disambiguate<Any>(
            uncheckedCast<Attribute<Any>?>(attribute)!!,
            uncheckedCast<Any?>(requested),
            uncheckedCast<MutableSet<Any>?>(candidates)!!
        )
    }

    /**
     * @param attribute The attribute to disambiguate.
     * @param candidates The set of candidate attribute sets to extract values from during disambiguation.
     */
    private fun <E> disambiguateExtraAttribute(attribute: Attribute<E?>, candidates: BitSet) {
        val candidateValues: MutableSet<E?> = getCandidateValues<E?>(candidates, IntFunction { c: Int -> getCandidateValue<E?>(c, attribute) })

        // We continue disambiguation for attributes with only one value since we may have some candidates with
        // no value for this attribute in addition to those with a value. Since we do not include `null` in the
        // candidate values, we must continue to execute the disambiguation rule in case the rule specifies a
        // default value and thus removes the candidates which do not have a value for this attribute.
        if (candidateValues.isEmpty()) {
            return
        }

        val matches = schema.disambiguate<E?>(attribute, null, candidateValues)
        if (matches != null) {
            // Remove any candidates which do not satisfy the disambiguation rule.
            var c = remaining!!.nextSetBit(0)
            while (c >= 0) {
                if (!matches.contains(getCandidateValue<E?>(c, attribute))) {
                    remaining!!.clear(c)
                }
                c = remaining!!.nextSetBit(c + 1)
            }
        }
    }

    private fun disambiguateWithExtraAttributes(extraAttributes: Array<Attribute<*>>) {
        val precedenceResult = schema.orderByPrecedence(Arrays.asList<Attribute<*>>(*extraAttributes))

        for (a in precedenceResult.getSortedOrder()) {
            disambiguateExtraAttribute(extraAttributes[a], remaining!!)
            if (remaining!!.cardinality() == 0) {
                return
            } else if (remaining!!.cardinality() == 1) {
                // If we're down to one candidate and the attribute has a known precedence,
                // we can stop now and choose this candidate as the match.
                return
            }
        }

        // When disambiguating in unknown precedence order, we always use the same set of candidates
        // so that this step is not affected by the order in which we iterate.
        val candidates = BitSet()
        candidates.or(remaining)

        // If the attribute does not have a known precedence, then we cannot stop
        // until we've disambiguated all of the attributes.
        for (a in precedenceResult.getUnsortedOrder()) {
            disambiguateExtraAttribute(extraAttributes[a], candidates)
            if (remaining!!.cardinality() == 0) {
                return
            }
        }
    }

    private fun getCandidates(liveSet: BitSet): IntArray {
        if (liveSet.cardinality() == 0) {
            return IntArray(0)
        }
        if (liveSet.cardinality() == 1) {
            return intArrayOf(liveSet.nextSetBit(0))
        }

        var i = 0
        val result = IntArray(liveSet.cardinality())
        var c = liveSet.nextSetBit(0)
        while (c >= 0) {
            result[i++] = c
            c = liveSet.nextSetBit(c + 1)
        }
        return result
    }

    private fun <E> getCandidateValue(c: Int, attribute: Attribute<E?>): E? {
        val attributeEntry = candidates[c].findEntry(attribute.getName())
        return if (attributeEntry != null) attributeEntry.coerce<E?>(attribute) else null
    }

    private fun getCandidateValue(c: Int, a: Int): Any? {
        return candidateValues[getValueIndex(c, a)]
    }

    private fun setCandidateValue(c: Int, a: Int, value: Any?) {
        candidateValues[getValueIndex(c, a)] = value
    }

    private fun getValueIndex(c: Int, a: Int): Int {
        return c * requestedAttributes.size + a
    }

    private enum class MatchResult {
        MATCH,
        MISSING,
        NO_MATCH
    }

    companion object {
        private fun getRequestedValues(requestedAttributes: MutableList<Attribute<*>>, requested: ImmutableAttributes): Array<Any?> {
            val requestedAttributeValues = arrayOfNulls<Any>(requestedAttributes.size)
            for (a in requestedAttributes.indices) {
                val attribute = requestedAttributes.get(a)
                val requestedEntry: ImmutableAttributesEntry<*>? = requested.findEntry(attribute)
                requestedAttributeValues[a] = if (requestedEntry != null) requestedEntry.getIsolatedValue() else null
            }
            return requestedAttributeValues
        }

        /**
         * Given each compatible candidate, determine all values corresponding to some attribute, as defined by `candidateValueFetcher`.
         *
         * @param candidateValueFetcher A function which returns the candidate value for some
         * attribute for the candidate at the provided index.
         *
         * @return A new set containing all compatible values for some attribute.
         */
        private fun <E> getCandidateValues(compatible: BitSet, candidateValueFetcher: IntFunction<E?>): MutableSet<E?> {
            // It's often the case that all the candidate values are the same. In this case, we avoid
            // the creation of a set, and just iterate until we find a different value. Then, only in
            // this case, we lazily initialize a set and collect all the candidate values.
            var candidateValues: MutableSet<E?>? = null
            var compatibleValue: E? = null
            var first = true
            var c = compatible.nextSetBit(0)
            while (c >= 0) {
                val candidateValue = candidateValueFetcher.apply(c)
                if (candidateValue == null) {
                    c = compatible.nextSetBit(c + 1)
                    continue
                }
                if (first) {
                    // first match, just record the value. We can't use "null" as the candidate value may be null
                    compatibleValue = candidateValue
                    first = false
                } else if (compatibleValue !== candidateValue || candidateValues != null) {
                    // we see a different value, or the set already exists, in which case we initialize
                    // the set if it wasn't done already, and collect all values.
                    if (candidateValues == null) {
                        candidateValues = Sets.newHashSetWithExpectedSize<E?>(compatible.cardinality())
                        candidateValues.add(compatibleValue)
                    }
                    candidateValues.add(candidateValue)
                }
                c = compatible.nextSetBit(c + 1)
            }
            if (candidateValues == null) {
                if (compatibleValue == null) {
                    return mutableSetOf<E?>()
                }
                return mutableSetOf<E?>(compatibleValue)
            }
            return candidateValues
        }
    }
}
