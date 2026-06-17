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
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.attributes.AttributeDescriber
import org.gradle.internal.component.resolution.failure.ResolutionCandidateAssessor
import org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure
import org.gradle.internal.exceptions.StyledException
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.TreeFormatter
import java.util.Arrays
import java.util.function.BinaryOperator
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors
import javax.inject.Inject

/**
 * An abstract base class for implementing [ResolutionFailureDescriber]s.
 *
 * This type provides methods for suggesting common resolutions, and doing common formatting
 * of failure messages.
 *
 * @param <FAILURE> The type of [ResolutionFailure] that this describer can describe
</FAILURE> */
abstract class AbstractResolutionFailureDescriber<FAILURE : ResolutionFailure?> : ResolutionFailureDescriber<FAILURE?> {
    @Inject
    protected abstract fun getDocumentationRegistry(): DocumentationRegistry?

    protected open fun buildResolutions(vararg resolutions: String): MutableList<String> {
        return Arrays.asList<String>(*resolutions)
    }

    protected fun suggestReviewAlgorithm(): String {
        return DEFAULT_MESSAGE_PREFIX + getDocumentationRegistry()!!.getDocumentationFor("variant_attributes", "sec:abm_algorithm") + "."
    }

    protected fun suggestSpecificDocumentation(prefix: String, section: String): String {
        return prefix + getDocumentationRegistry()!!.getDocumentationFor("variant_model", section) + "."
    }

    protected fun formatAttributeSection(formatter: TreeFormatter, section: String, values: MutableList<String>) {
        if (!values.isEmpty()) {
            if (values.size > 1) {
                formatter.node(section + "s")
            } else {
                formatter.node(section)
            }
            formatter.startChildren()
            values.forEach(Consumer { text: String? -> formatter.node(text) })
            formatter.endChildren()
        }
    }

    protected fun formatAttributeMatchesForAmbiguity(
        assessedCandidate: ResolutionCandidateAssessor.AssessedCandidate,
        formatter: TreeFormatter,
        describer: AttributeDescriber
    ) {
        // None of the nullability warnings are relevant here because the attribute values are only retrieved from collections that will contain them
        val compatibleAttrs: MutableMap<Attribute<*>, *> = assessedCandidate.getCompatibleAttributes().stream()
            .collect(
                Collectors.toMap(
                    Function { obj: ResolutionCandidateAssessor.AssessedAttribute<*>? -> obj!!.getAttribute() },
                    Function { obj: ResolutionCandidateAssessor.AssessedAttribute<*>? -> obj!!.getProvided() },
                    BinaryOperator { a: String?, b: String? -> a })
            )
        val onlyOnProducer = assessedCandidate.getOnlyOnCandidateAttributes().stream()
            .map<String> { assessedAttribute: ResolutionCandidateAssessor.AssessedAttribute<*>? ->
                "Provides " + describer.describeExtraAttribute(
                    assessedAttribute!!.getAttribute(),
                    assessedAttribute.getProvided()
                ) + " but the consumer didn't ask for it"
            }
            .collect(Collectors.toList())
        val onlyOnConsumer = assessedCandidate.getOnlyOnRequestAttributes().stream()
            .map<String?> { assessedAttribute: ResolutionCandidateAssessor.AssessedAttribute<*>? ->
                "Doesn't say anything about " + describer.describeMissingAttribute(
                    assessedAttribute!!.getAttribute(),
                    assessedAttribute.getRequested()
                )
            }
            .collect(Collectors.toList())

        val other: MutableList<String> = ArrayList<String>(onlyOnProducer.size + onlyOnConsumer.size)
        other.addAll(onlyOnProducer)
        other.addAll(onlyOnConsumer)
        other.sort(Comparator { obj: String?, anotherString: String? -> obj!!.compareTo(anotherString!!) })

        if (!compatibleAttrs.isEmpty()) {
            formatter.append(" declares ").append(StyledException.style(StyledTextOutput.Style.SuccessHeader, describer.describeAttributeSet(compatibleAttrs)))
        }
        formatter.startChildren()
        formatAttributeSection(formatter, "Unmatched attribute", other)
        formatter.endChildren()
    }

    protected fun formatAttributeMatchesForIncompatibility(
        assessedCandidate: ResolutionCandidateAssessor.AssessedCandidate,
        formatter: TreeFormatter,
        describer: AttributeDescriber
    ) {
        // None of the nullability warnings are relevant here because the attribute values are only retrieved from collections that will contain them
        val compatibleAttrs: MutableMap<Attribute<*>, *> = assessedCandidate.getCompatibleAttributes().stream()
            .collect(
                Collectors.toMap(
                    Function { obj: ResolutionCandidateAssessor.AssessedAttribute<*>? -> obj!!.getAttribute() },
                    Function { obj: ResolutionCandidateAssessor.AssessedAttribute<*>? -> obj!!.getProvided() },
                    BinaryOperator { a: String?, b: String? -> a })
            )
        val incompatibleAttrs: MutableMap<Attribute<*>, *> = assessedCandidate.getIncompatibleAttributes().stream()
            .collect(
                Collectors.toMap(
                    Function { obj: ResolutionCandidateAssessor.AssessedAttribute<*>? -> obj!!.getAttribute() },
                    Function { obj: ResolutionCandidateAssessor.AssessedAttribute<*>? -> obj!!.getProvided() },
                    BinaryOperator { a: String?, b: String? -> a })
            )
        val incompatibleConsumerAttrs: MutableMap<Attribute<*>, *> = assessedCandidate.getIncompatibleAttributes().stream()
            .collect(
                Collectors.toMap(
                    Function { obj: ResolutionCandidateAssessor.AssessedAttribute<*>? -> obj!!.getAttribute() },
                    Function { obj: ResolutionCandidateAssessor.AssessedAttribute<*>? -> obj!!.getRequested() },
                    BinaryOperator { a: String?, b: String? -> a })
            )
        val otherValues = assessedCandidate.getOnlyOnRequestAttributes().stream()
            .map<String?> { assessedAttribute: ResolutionCandidateAssessor.AssessedAttribute<*>? ->
                "Doesn't say anything about " + describer.describeMissingAttribute(
                    assessedAttribute!!.getAttribute(),
                    assessedAttribute.getRequested()
                )
            }
            .sorted()
            .collect(Collectors.toList())

        if (!compatibleAttrs.isEmpty()) {
            formatter.append(" declares ").append(StyledException.style(StyledTextOutput.Style.SuccessHeader, describer.describeAttributeSet(compatibleAttrs)))
        }
        formatter.startChildren()
        if (!incompatibleAttrs.isEmpty()) {
            formatter.node(
                "Incompatible because this component declares " + StyledException.style(
                    StyledTextOutput.Style.FailureHeader,
                    describer.describeAttributeSet(incompatibleAttrs)
                ) + " and the consumer needed " + StyledException.style(
                    StyledTextOutput.Style.FailureHeader, describer.describeAttributeSet(incompatibleConsumerAttrs)
                )
            )
        }
        formatAttributeSection(formatter, "Other compatible attribute", otherValues)
        formatter.endChildren()
    }

    companion object {
        private const val DEFAULT_MESSAGE_PREFIX = "Review the variant matching algorithm at "
    }
}
