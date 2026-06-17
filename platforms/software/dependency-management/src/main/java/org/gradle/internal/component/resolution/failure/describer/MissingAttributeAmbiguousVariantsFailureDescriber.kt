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
package org.gradle.internal.component.resolution.failure.describer

import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.AttributeDescriber
import org.gradle.api.internal.attributes.AttributeDescriberRegistry
import org.gradle.internal.component.model.AttributeDescriberSelector
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor
import org.gradle.internal.component.resolution.failure.type.AmbiguousVariantsFailure
import org.gradle.internal.logging.text.TreeFormatter
import java.util.IdentityHashMap
import java.util.Objects
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.stream.Collectors
import javax.inject.Inject

/**
 * A [ResolutionFailureDescriber] that describes an [AmbiguousVariantsFailure] where
 * there is a single differing attribute between all available variants that is missing from the request.
 *
 *
 * In this situation, we can provide a very brief message pointing to the exact solution needed.
 */
abstract class MissingAttributeAmbiguousVariantsFailureDescriber @Inject constructor(
    attributeDescribers: AttributeDescriberRegistry
) : AmbiguousVariantsFailureDescriber(attributeDescribers) {
    /**
     * Map from failure to name of attribute that would distinguish each candidate.
     * This map exists to avoid re-discovering the unrequested attributes between the calls to `canDescribeFailure` and `describeFailure`.
     * Each failure will be added once (by identity), then removed during failure description.
     */
    private val suggestableDistinctAttributes = IdentityHashMap<AmbiguousVariantsFailure, String>()

    override fun canDescribeFailure(failure: AmbiguousVariantsFailure): Boolean {
        // Map from name of attribute -> set of attribute names for each candidate
        val unrequestedAttributesWithValues: MutableMap<String, MutableSet<String>> = HashMap<String, MutableSet<String>>()

        failure.getCandidates().forEach(Consumer { candidate: ResolutionCandidateAssessor.AssessedCandidate? ->
            candidate!!.getOnlyOnCandidateAttributes().forEach(Consumer { candidateAttribute: ResolutionCandidateAssessor.AssessedAttribute<*>? ->
                val attribute: Attribute<*> = candidateAttribute!!.getAttribute()
                val unrequestedValuesForAttribute = unrequestedAttributesWithValues.computeIfAbsent(attribute.getName()) { name: String? -> HashSet<String?>() }
                unrequestedValuesForAttribute.add(Objects.requireNonNull<String?>(candidateAttribute.getProvided()).toString())
            })
        })

        // List of map entries where there is a distinct attribute value for every available candidate
        val attributesDistinctlyIdentifyingCandidates = unrequestedAttributesWithValues.entries.stream()
            .filter { entry: MutableMap.MutableEntry<String?, MutableSet<String?>?>? -> entry!!.value!!.size == failure.getCandidates().size }
            .collect(Collectors.toList())

        if (attributesDistinctlyIdentifyingCandidates.size == 1) {
            suggestableDistinctAttributes.put(failure, attributesDistinctlyIdentifyingCandidates.get(0).key)
            return true
        } else {
            return false
        }
    }

    override fun buildFailureMsg(failure: AmbiguousVariantsFailure, attributeDescribers: MutableList<AttributeDescriber>): String {
        val distinguishingAttribute = checkNotNull(suggestableDistinctAttributes.remove(failure))
        val describer = AttributeDescriberSelector.selectDescriber(failure.getRequestedAttributes(), attributeDescribers)
        val formatter = TreeFormatter()
        summarizeAmbiguousVariants(failure, describer, formatter, false)
        buildSpecificAttributeSuggestionMsg(failure, distinguishingAttribute, formatter)
        return formatter.toString()
    }

    override fun buildResolutions(vararg resolutions: String): MutableList<String> {
        val result: MutableList<String> = ArrayList<String>(super.buildResolutions(*resolutions))
        result.add(suggestDependencyInsight())
        return result
    }

    private fun buildSpecificAttributeSuggestionMsg(failure: AmbiguousVariantsFailure, distinguishingAttribute: String, formatter: TreeFormatter) {
        formatter.node("The only attribute distinguishing these variants is '" + distinguishingAttribute + "'. Add this attribute to the consumer's configuration to resolve the ambiguity:")
        formatter.startChildren()
        failure.getCandidates().forEach(Consumer { candidate: ResolutionCandidateAssessor.AssessedCandidate? ->
            formatter.node(
                "Value: '" + attributeValueForCandidate(
                    candidate!!,
                    distinguishingAttribute
                ) + "' selects variant: '" + candidate.getDisplayName() + "'"
            )
        })
        formatter.endChildren()
    }

    private fun attributeValueForCandidate(candidate: ResolutionCandidateAssessor.AssessedCandidate, distinguishingAttribute: String): String {
        return candidate.getOnlyOnCandidateAttributes().stream()
            .filter { attribute: ResolutionCandidateAssessor.AssessedAttribute<*>? -> attribute!!.getAttribute().getName() == distinguishingAttribute }
            .map<String> { assessedAttribute: ResolutionCandidateAssessor.AssessedAttribute<*>? -> Objects.requireNonNull<String?>(assessedAttribute!!.getProvided()).toString() }
            .findFirst().orElseThrow<java.lang.IllegalStateException>(Supplier { IllegalStateException() })
    }

    private fun suggestDependencyInsight(): String {
        return "Use the dependencyInsight report with the --all-variants option to view all variants of the ambiguous dependency.  This report is described at " + getDocumentationRegistry().getDocumentationFor(
            "viewing_debugging_dependencies",
            "sec:identifying_reason_dependency_selection"
        ) + "."
    }
}
