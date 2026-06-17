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
import org.gradle.api.internal.attributes.ImmutableAttributesEntry

interface AttributeMatcher {
    /**
     * Determines whether the given candidate is compatible with the requested criteria.
     */
    fun isMatchingCandidate(candidate: ImmutableAttributes, requested: ImmutableAttributes): Boolean

    /**
     * Determines whether two candidates are mutually compatible.
     *
     * @return true if for each shared key in the provided attribute sets, the corresponding
     * attribute value in each set is compatible. false otherwise.
     */
    fun areMutuallyCompatible(first: ImmutableAttributes, second: ImmutableAttributes): Boolean

    /**
     * Determine if a candidate value compatible with the requested criteria
     * for a some attribute.
     */
    fun <T> isMatchingValue(attribute: Attribute<T?>, candidate: T?, requested: T?): Boolean

    /**
     * Selects all matches from `candidates` that are compatible with the `requested`
     * criteria attributes. Then, if there is more than one match, performs disambiguation to attempt
     * to reduce the set of matches to a more preferred subset.
     */
    fun <T : AttributeMatchingCandidate?> matchMultipleCandidates(
        candidates: MutableList<out T>,
        requested: ImmutableAttributes
    ): MutableList<T?>?

    // TODO: Merge this with ResolutionCandidateAssessor
    fun describeMatching(candidate: ImmutableAttributes, requested: ImmutableAttributes): MutableList<MatchingDescription<*>>?

    class MatchingDescription<T>(val requested: ImmutableAttributesEntry<T?>, val found: ImmutableAttributesEntry<T?>?, val isMatch: Boolean)
}
