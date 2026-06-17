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
package org.gradle.api.internal.artifacts.transform

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.matching.AttributeMatcher
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler
import org.gradle.internal.component.resolution.failure.transform.TransformationChainData
import org.gradle.internal.component.resolution.failure.transform.TransformedVariantConverter
import org.gradle.internal.lazy.Lazy
import org.gradle.internal.lazy.Lazy.Companion.unsafe
import java.util.Optional
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Disambiguates a set of related transformation chains that all satisfy an attribute matching request.
 *
 *
 * This class accomplishes this goal by internally assessing the given chains to separate truly
 * distinct chains with unique fingerprints using a lazily computed field ([.preferredChainsByFingerprint]).
 * This internal assessment can be used to determine whether there is any ambiguity in the given chains.
 *
 *
 * It reports any ambiguity failures to the given [ResolutionFailureHandler].
 *
 *
 * Requirements:
 *
 *  * All chains to assess **MUST**> produce results that are compatible with the target attributes of the request.
 *  * Chains may or may not be **MUTUALLY COMPATIBLE** with each other.
 *  * If more than one chain is present, they **may or may NOT** be truly distinct (by fingerprint)
 *  * All chains to assess **MUST** be of equal length.
 *
 */
/* package */
internal class TransformationChainsDisambiguator(
    private val failureHandler: ResolutionFailureHandler,
    private val producer: ResolvedVariantSet,
    private val targetAttributes: ImmutableAttributes,
    attributeMatcher: AttributeMatcher,
    chainsToAssess: MutableList<TransformedVariant>
) {
    /**
     * The preferred matching chains are the best matches of the chains supplied to the constructor for assessment.
     * Those chains are all **COMPATIBLE** with the target attributes, but may not all be **EXACT** matches,
     * which would be preferred.
     */
    private val preferredChains: MutableList<TransformedVariant>

    /**
     * Each value in this lazily computed map contains an arbitrary representative of each group of transformation chains
     * within the preferred chains that produce the same fingerprint.  Each set thus represents the **SAME**
     * transformations applied in **ANY** sequence.
     *
     *
     * Fingerprinting is an expensive operation, so this map is computed only when needed.
     */
    private val preferredChainsByFingerprint: Lazy<LinkedHashMap<TransformationChainData.TransformationChainFingerprint, TransformedVariant>?>

    /**
     * Create an assessment of a given list of transformation chains by fingerprinting those chains
     * to disambiguate which are mutually compatible and which are truly distinct.
     *
     *
     * All chains must be of the same length, and should all be compatible with a single set
     * of target attributes (which is not supplied to this class).  These requirements are
     * **NOT** verified by this constructor.
     *
     * @param failureHandler resolution failure handler to use to report any unresolvable ambiguity
     * @param producer the resolved variant set that produced the chains to assess
     * @param targetAttributes the attributes we are aiming to match via a transformation chain
     * @param attributeMatcher the attribute matcher to use to determine compatible results
     * @param chainsToAssess the candidate transformation chains to disambiguate
     */
    init {
        this.preferredChains = attributeMatcher.matchMultipleCandidates<TransformedVariant?>(chainsToAssess, targetAttributes)
        this.preferredChainsByFingerprint = unsafe().of<LinkedHashMap<TransformationChainData.TransformationChainFingerprint, TransformedVariant>?>(Supplier {
            val transformedVariantConverter = TransformedVariantConverter()
            // Fingerprint all preferred chains to build a map from each unique fingerprint -> all preferred chains with that fingerprint
            val result = LinkedHashMap<TransformationChainData.TransformationChainFingerprint, TransformedVariant>(preferredChains.size)
            preferredChains.forEach(Consumer { chain: TransformedVariant? ->
                val fingerprint = transformedVariantConverter.convert(chain!!).fingerprint()
                result.putIfAbsent(fingerprint, chain)
            })
            result
        })
    }

    /**
     * Given a set of potential transformation chains, attempts to reduce the set to a single, unambiguous, compatible candidate.
     *
     *
     * If this isn't possible because there are multiple compatible matches, this checks if they are truly distinct,
     * and not just re-sequencings of the same chain.  If they are **NOT** distinct, it arbitrarily
     * returns one.
     *
     *
     * If multiple, compatible, truly distinct matches exist, we'll warn (for now), but this behavior is
     * deprecated.  As of Gradle 9.0, this will also fail.
     *
     * @return single, unambiguous, preferred chain for use, selected as described above
     */
    fun disambiguate(): Optional<TransformedVariant> {
        // If only a single preferred chain was found, then the ambiguity
        // was due to multiple compatible chains, containing only one EXACT match.  We will use the exact match
        // and can return early, skipping any fingerprinting work.
        if (preferredChains.size == 1) {
            return Optional.of<TransformedVariant>(preferredChains.get(0))
        }

        if (preferredChainsByFingerprint.get()!!.size > 1) {
            // Multiple unique fingerprints in the preferred chains is a problem.
            // This now fails the build.  The error message should report one representative of each
            // distinct chain, so that the author can understand what's happening here and correct it.
            throw failureHandler.ambiguousArtifactTransformsFailure(producer, targetAttributes, this.distinctPreferredChainRepresentatives)
        } else {
            return this.arbitraryPreferredMatchingChain
        }
    }

    private val arbitraryPreferredMatchingChain: Optional<TransformedVariant>
        /**
         * Return an arbitrary preferred chain, if one exists.
         *
         *
         * It remains important to use the **LAST** compatible match, as this was the previous behavior,
         * and is tested in `DisambiguateArtifactTransformIntegrationTest`.
         *
         * @return first preferred transformation chain in this result set if one exists; else [Optional.empty]
         */
        get() = if (!preferredChains.isEmpty()) Optional.of<TransformedVariant>(preferredChains.get(preferredChains.size - 1)) else Optional.empty<TransformedVariant>()

    private val distinctPreferredChainRepresentatives: MutableCollection<TransformedVariant>
        /**
         * Return a representative of each fingerprint group.
         *
         *
         * For example, if, A, B, C and D each represent distinct sets of attributes, then chains
         * of A -> B -> C -> D and A -> C -> B -> D are merely re-sequencings of the same transformations and
         * are **NOT** truly distinct.  This is fine, as Gradle can just arbitrarily pick one,
         * since the different order that steps are run is **PROBABLY** not meaningful - the
         * **SAME** work will be done (though the order may still have efficiency implications).
         * These chains have the same fingerprint.
         *
         *
         * However, chains of A -> B -> C and A -> D -> C are **NOT** the same!  Even though they end up
         * producing a C with the same exact attributes, they represent **DIFFERENT** work being done, and Gradle
         * has no way to determine which path is better to select.  This
         * choice likely **WILL** have an impact, as different transforms could have very different performance
         * characteristics, and because the author likely expects one path to be taken, but won't know if
         * it was or wasn't.  These chains have different fingerprints.
         *
         *
         * So within [.preferredChains], each unique fingerprint is associated with a list containing
         * potentially multiple chains.  The method will (arbitrarily) select the first such chain with a particular
         * fingerprint encountered within that list.  As each group of chains with the same fingerprint produces
         * the same result, all chains in that group are all necessarily mutually compatible.
         *
         *
         * This triggers fingerprinting.
         *
         * @return one arbitrary chain from each distinct set of chains with an identical fingerprint within the preferred chains
         */
        get() = preferredChainsByFingerprint.get()!!.values
}
