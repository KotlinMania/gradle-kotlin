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

import com.google.common.collect.Ordering
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.internal.component.resolution.failure.exception.ArtifactSelectionException
import org.gradle.internal.component.resolution.failure.transform.SourceVariantData
import org.gradle.internal.component.resolution.failure.transform.TransformData
import org.gradle.internal.component.resolution.failure.transform.TransformationChainData
import org.gradle.internal.component.resolution.failure.type.AmbiguousArtifactTransformsFailure
import org.gradle.internal.logging.text.TreeFormatter
import java.util.TreeMap
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors

/**
 * A [ResolutionFailureDescriber] that describes an [AmbiguousArtifactTransformsFailure].
 */
abstract class AmbiguousArtifactTransformsFailureDescriber : AbstractResolutionFailureDescriber<AmbiguousArtifactTransformsFailure>() {
    override fun describeFailure(failure: AmbiguousArtifactTransformsFailure): ArtifactSelectionException {
        val message = buildFailureMsg(failure)
        val resolutions = buildResolutions(
            REMOVE_TRANSFORMATIONS_SUGGESTION,
            ARTIFACT_TRANSFORMS_REPORT_SUGGESTION,
            suggestSpecificDocumentation(AMBIGUOUS_TRANSFORMATION_PREFIX, AMBIGUOUS_TRANSFORMATION_SECTION),
            suggestReviewAlgorithm()
        )
        return ArtifactSelectionException(message, failure, resolutions)
    }

    private fun buildFailureMsg(failure: AmbiguousArtifactTransformsFailure): String {
        val formatter = TreeFormatter(true)

        formatter.node("Found multiple transformation chains that produce a variant of '" + failure.describeRequestTarget() + "' with requested attributes")
        formatSortedAttributes(formatter, failure.getRequestedAttributes())
        formatter.node("Found the following transformation chains")

        val variantDataComparator =
            Comparator.comparing<TransformationChainData, String>(Function { obj: TransformationChainData -> obj.summarizeTransformations() })
                .thenComparing<String>(Function { x: TransformationChainData -> x.getFinalAttributes().toString() })

        val transformationPaths: MutableMap<SourceVariantData, MutableList<TransformationChainData>> = failure.getPotentialVariants().stream()
            .collect(
                Collectors.groupingBy(
                    Function { obj: TransformationChainData? -> obj!!.getInitialVariant() },
                    Supplier { TreeMap<SourceVariantData?, MutableList<TransformationChainData?>?>(Comparator.comparing<SourceVariantData, String>(Function { obj: SourceVariantData -> obj.getVariantDisplayName() })) },
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        Function { list: MutableList<TransformationChainData?>? -> list!!.stream().sorted(variantDataComparator).collect(Collectors.toList()) })
                )
            )

        formatter.startChildren()
        transformationPaths.forEach { (root: SourceVariantData?, transformations: MutableList<TransformationChainData?>?) ->
            formatSourceVariant(root!!, formatter)
            formatter.node("Candidate transformation chains")
            formatter.startChildren()
            transformations!!.forEach(Consumer { transformationChainData: TransformationChainData? ->
                formatter.node("Transformation chain: " + transformationChainData!!.summarizeTransformations())
                formatter.startChildren()
                transformationChainData.getSteps().forEach(Consumer { step: TransformData? ->
                    formatter.node("'" + step!!.getTransformName() + "'")
                    formatter.startChildren()
                    formatter.node("Converts from attributes")
                    formatSortedAttributes(formatter, step.getFromAttributes())
                    formatter.node("To attributes")
                    formatSortedAttributes(formatter, step.getToAttributes())
                    formatter.endChildren()
                })
                formatter.endChildren()
            })
            formatter.endChildren()
            formatter.endChildren() // End formatSourceVariant children
        }
        formatter.endChildren()

        return formatter.toString()
    }

    private fun formatSourceVariant(sourceVariantData: SourceVariantData, formatter: TreeFormatter) {
        formatter.node("From " + sourceVariantData.getVariantDisplayName())
        formatter.startChildren()
        formatter.node("With source attributes")
        formatSortedAttributes(formatter, sourceVariantData.getAttributes())
    }

    private fun formatSortedAttributes(formatter: TreeFormatter, attributes: AttributeContainer) {
        formatter.startChildren()
        for (attribute in Ordering.usingToString().sortedCopy<Attribute<*>>(attributes.keySet())) {
            formatter.node(attribute.getName() + " '" + attributes.getAttribute(attribute) + "'")
        }
        formatter.endChildren()
    }

    companion object {
        private const val ARTIFACT_TRANSFORMS_REPORT_SUGGESTION = "Run the :artifactTransforms report to see the available artifact transforms."
        private const val AMBIGUOUS_TRANSFORMATION_PREFIX = "Transformation failures are explained in more detail at "
        private const val AMBIGUOUS_TRANSFORMATION_SECTION = "sub:transform-ambiguity"
        private const val REMOVE_TRANSFORMATIONS_SUGGESTION =
            "Remove one or more registered transforms, or add additional attributes to them to ensure only a single valid transformation chain exists."
    }
}
