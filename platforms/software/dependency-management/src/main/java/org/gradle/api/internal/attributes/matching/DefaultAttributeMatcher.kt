/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableList
import com.google.common.primitives.Ints
import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.ImmutableAttributesEntry
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder.Companion.logging
import org.gradle.internal.model.InMemoryCacheFactory
import org.gradle.internal.model.InMemoryLoadingCache
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.function.Function

/**
 * An [AttributeMatcher], which optimizes for the case of only comparing 0 or 1 candidates
 * and delegates to [MultipleCandidateMatcher] for all other cases.
 */
class DefaultAttributeMatcher(
    private val schema: AttributeSelectionSchema,
    cacheFactory: InMemoryCacheFactory
) : AttributeMatcher {
    /**
     * Attribute matching can be very expensive. In case there are multiple candidates, we
     * cache the result of the query, because it's often the case that we ask for the same
     * disambiguation of attributes several times in a row (but with different candidates).
     */
    private val cachedQueries: InMemoryLoadingCache<CachedQuery, IntArray>
    private val matchingCandidatesCache: InMemoryLoadingCache<MatchingCandidateCacheKey, Boolean>

    init {
        this.cachedQueries = cacheFactory.create<CachedQuery, IntArray>(Function { key: CachedQuery -> this.doMatchMultipleCandidates(key) })
        this.matchingCandidatesCache = cacheFactory.create<MatchingCandidateCacheKey, Boolean>(Function { k: MatchingCandidateCacheKey -> this.doIsMatchingCandidate(k) })
    }

    override fun <T> isMatchingValue(attribute: Attribute<T?>, candidate: T?, requested: T?): Boolean {
        return schema.matchValue<T?>(attribute, requested, candidate)
    }

    override fun isMatchingCandidate(candidate: ImmutableAttributes, requested: ImmutableAttributes): Boolean {
        val key = MatchingCandidateCacheKey(candidate, requested)
        return matchingCandidatesCache.get(key)
    }

    private fun doIsMatchingCandidate(k: MatchingCandidateCacheKey): Boolean {
        return allCommonAttributesSatisfy(
            k.candidate,
            k.requested,
            DefaultAttributeMatcher.CoercingAttributeValuePredicate { attribute: Attribute<A?>, requested: A?, candidate: A? -> schema.matchValue(attribute, requested, candidate) })
    }

    override fun areMutuallyCompatible(candidate: ImmutableAttributes, requested: ImmutableAttributes): Boolean {
        return allCommonAttributesSatisfy(
            candidate,
            requested,
            DefaultAttributeMatcher.CoercingAttributeValuePredicate { attribute: Attribute<A?>, requested: A?, candidate: A? -> schema.weakMatchValue(attribute, requested, candidate) })
    }

    /**
     * Return true iff all common attributes between the candidate and requested
     * attribute sets satisfy the given predicate.
     */
    private fun allCommonAttributesSatisfy(
        candidate: ImmutableAttributes,
        requested: ImmutableAttributes,
        predicate: CoercingAttributeValuePredicate
    ): Boolean {
        if (requested.isEmpty() || candidate.isEmpty()) {
            return true
        }

        for (requestedEntry in requested.getEntries()) {
            val attribute: Attribute<*> = requestedEntry.getKey()
            val candidateEntry = candidate.findEntry(attribute.getName())

            if (candidateEntry != null) {
                val typedAttribute = schema.tryRehydrate(attribute)
                if (!predicate.test(typedAttribute, requestedEntry, candidateEntry)) {
                    return false
                }
            }
        }

        return true
    }

    /**
     * Matches two attribute values, one from the consumer and one from the producer,
     * that share a common attribute type.
     */
    private interface CoercingAttributeValuePredicate {
        /**
         * Test that the candidate attribute value satisfies the requested attribute value.
         */
        fun <A> test(attribute: Attribute<A?>, requested: A?, candidate: A?): Boolean

        /**
         * Coerce the candidate and requested attribute values to the type of the attribute
         * and test that they match.
         */
        fun <T> test(
            attribute: Attribute<T?>,
            requested: ImmutableAttributesEntry<*>,
            candidate: ImmutableAttributesEntry<*>
        ): Boolean {
            val requestedValue = requested.coerce<T?>(attribute)
            val candidateValue = candidate.coerce<T?>(attribute)
            return test<T?>(attribute, requestedValue, candidateValue)
        }
    }

    override fun describeMatching(candidate: ImmutableAttributes, requested: ImmutableAttributes): MutableList<AttributeMatcher.MatchingDescription<*>> {
        if (requested.isEmpty() || candidate.isEmpty()) {
            return mutableListOf<AttributeMatcher.MatchingDescription<*>>()
        }

        val matches: CoercingAttributeValuePredicate =
            DefaultAttributeMatcher.CoercingAttributeValuePredicate { attribute: Attribute<A?>, requested: A?, candidate: A? -> schema.matchValue(attribute, requested, candidate) }

        val attributes = requested.getEntries()
        val result: MutableList<AttributeMatcher.MatchingDescription<*>> = ArrayList<AttributeMatcher.MatchingDescription<*>>(attributes.size)
        for (requestedEntry in attributes) {
            val attribute: Attribute<*> = requestedEntry.getKey()
            val candidateEntry = candidate.findEntry(attribute.getName())

            if (candidateEntry != null) {
                val typedAttribute = schema.tryRehydrate(attribute)
                val match = matches.test(typedAttribute, requestedEntry, candidateEntry)
                result.add(AttributeMatcher.MatchingDescription<Any?>(requestedEntry, candidateEntry, match))
            } else {
                result.add(AttributeMatcher.MatchingDescription<Any?>(requestedEntry, candidateEntry, false))
            }
        }
        return result
    }

    override fun <T : AttributeMatchingCandidate?> matchMultipleCandidates(
        candidates: MutableList<out T>,
        requested: ImmutableAttributes
    ): MutableList<T?> {
        val explanationBuilder = logging()

        if (candidates.isEmpty()) {
            explanationBuilder.noCandidates(requested)
            return ImmutableList.of<T?>()
        }

        if (candidates.size == 1) {
            val candidate: T? = candidates.iterator().next()
            val candidateAttributes = candidate!!.getAttributes()
            if (isMatchingCandidate(candidateAttributes, requested)) {
                explanationBuilder.singleMatch(candidateAttributes, ImmutableList.of<ImmutableAttributes>(candidateAttributes), requested)
                return mutableListOf<T?>(candidate)
            }
            explanationBuilder.candidateDoesNotMatchAttributes(candidateAttributes, requested)
            return ImmutableList.of<T?>()
        }

        // Often times, collections of candidates will themselves differ even though their attributes are the same.
        // Disambiguating two different candidate lists which map to the same attribute lists in reality performs
        // the same work, so instead we cache disambiguation results based on the attributes being disambiguated.
        // The result of this is a list of indices into the original candidate list from which the
        // attributes-to-disambiguate are derived. When retrieving a result from the cache, we use the resulting
        // indices to index back into the original candidates list.
        val query: CachedQuery = CachedQuery.Companion.from(requested, candidates)
        val indices = cachedQueries.get(query)
        return CachedQuery.Companion.getMatchesFromCandidateIndices<T?>(indices, candidates)
    }

    private fun doMatchMultipleCandidates(key: CachedQuery): IntArray {
        val explanationBuilder = logging()
        val matches = MultipleCandidateMatcher(schema, key.candidates, key.requestedAttributes, explanationBuilder).getMatches()
        LOGGER.debug("Selected matches {} from candidates {} for {}", Ints.asList(*matches), key.candidates, key.requestedAttributes)
        return matches
    }

    private class CachedQuery(private val requestedAttributes: ImmutableAttributes, private val candidates: Array<ImmutableAttributes>) {
        private val hashCode: Int

        init {
            this.hashCode = computeHashCode(requestedAttributes, candidates)
        }

        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as CachedQuery
            return hashCode == that.hashCode &&
                    requestedAttributes == that.requestedAttributes && candidates.contentEquals(that.candidates)
        }

        override fun hashCode(): Int {
            return hashCode
        }

        override fun toString(): String {
            return "CachedQuery{" +
                    "requestedAttributes=" + requestedAttributes +
                    ", candidates=" + candidates.contentToString() +
                    '}'
        }

        companion object {
            private fun computeHashCode(requestedAttributes: ImmutableAttributes, candidates: Array<ImmutableAttributes>): Int {
                var hash = requestedAttributes.hashCode()
                for (candidate in candidates) {
                    hash = 31 * hash + candidate.hashCode()
                }
                return hash
            }

            fun <T : AttributeMatchingCandidate?> from(requestedAttributes: ImmutableAttributes, candidates: MutableList<T?>): CachedQuery {
                val size = candidates.size
                val attributes: Array<ImmutableAttributes> = arrayOfNulls<ImmutableAttributes>(size)
                for (i in 0..<size) {
                    attributes[i] = candidates.get(i)!!.getAttributes()
                }
                return CachedQuery(requestedAttributes, attributes)
            }

            private fun <T : AttributeMatchingCandidate?> getMatchesFromCandidateIndices(indices: IntArray, candidates: MutableList<out T>): MutableList<T?> {
                if (indices.size == 0) {
                    return mutableListOf<T?>()
                }

                val matches: MutableList<T?> = ArrayList<T?>(indices.size)
                for (index in indices) {
                    matches.add(candidates.get(index))
                }

                return matches
            }
        }
    }

    private class MatchingCandidateCacheKey(private val candidate: ImmutableAttributes, private val requested: ImmutableAttributes) {
        private val hashCode: Int

        init {
            this.hashCode = 31 * candidate.hashCode() + requested.hashCode()
        }

        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val cacheKey = o as MatchingCandidateCacheKey
            return candidate == cacheKey.candidate && requested == cacheKey.requested
        }

        override fun hashCode(): Int {
            return hashCode
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultAttributeMatcher::class.java)
    }
}
