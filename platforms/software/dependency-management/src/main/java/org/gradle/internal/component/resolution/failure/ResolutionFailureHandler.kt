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
package org.gradle.internal.component.resolution.failure

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState
import org.gradle.api.internal.artifacts.transform.TransformedVariant
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.matching.AttributeMatcher
import org.gradle.api.internal.catalog.problems.ResolutionFailureProblemId
import org.gradle.api.problems.AdditionalData
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory
import org.gradle.api.problems.internal.DefaultResolutionFailureData
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.api.problems.internal.ResolutionFailureData
import org.gradle.api.problems.internal.ResolutionFailureDataSpec
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.GraphSelectionCandidates
import org.gradle.internal.component.model.VariantGraphResolveMetadata
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.component.resolution.failure.describer.ResolutionFailureDescriber
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException
import org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure
import org.gradle.internal.component.resolution.failure.transform.TransformedVariantConverter
import org.gradle.internal.component.resolution.failure.type.AmbiguousArtifactTransformsFailure
import org.gradle.internal.component.resolution.failure.type.AmbiguousArtifactsFailure
import org.gradle.internal.component.resolution.failure.type.AmbiguousVariantsFailure
import org.gradle.internal.component.resolution.failure.type.ConfigurationDoesNotExistFailure
import org.gradle.internal.component.resolution.failure.type.ConfigurationNotCompatibleFailure
import org.gradle.internal.component.resolution.failure.type.IncompatibleMultipleNodesValidationFailure
import org.gradle.internal.component.resolution.failure.type.ModuleRejectedFailure
import org.gradle.internal.component.resolution.failure.type.NoCompatibleArtifactFailure
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure
import org.gradle.internal.component.resolution.failure.type.NoVariantsWithMatchingCapabilitiesFailure
import org.gradle.internal.component.resolution.failure.type.UnknownArtifactSelectionFailure
import org.gradle.internal.instantiation.InstanceGenerator
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Stream

/**
 * Provides a central location for handling failures encountered during
 * each stage of the variant selection process during dependency resolution.
 *
 * All resolution failures encountered during selection by the [GraphVariantSelector] or
 * [AttributeMatchingArtifactVariantSelector]
 * should be routed through this class.
 *
 * This class is responsible for packaging [ResolutionFailure] data into the appropriate failure types,
 * selecting the appropriate [ResolutionFailureDescriber] to describe that failure,
 * returning the result of running the describer.  It maintains a registry of **default**
 * describers for each failure type; but will first consult the [AttributesSchemaInternal] for
 * any custom describers registered on that schema for a given failure type.
 *
 * Class is non-`final` for testing via stubbing.
 *
 * @implNote The methods for reporting failures via this class are ordered to match the stages of the variant selection process and
 * within those stages alphabetically, with names aligned with the failure type hierarchy.  This makes it much easier to navigate.
 */
@ServiceScope(Scope.Project::class)
class ResolutionFailureHandler(instanceGenerator: InstanceGenerator, problemsService: ProblemsInternal, private val transformedVariantConverter: TransformedVariantConverter) {
    private val defaultFailureDescribers: ResolutionFailureDescriberRegistry
    private val customFailureDescribers: ResolutionFailureDescriberRegistry

    init {
        this.defaultFailureDescribers = ResolutionFailureDescriberRegistry.Companion.standardRegistry(instanceGenerator)
        this.customFailureDescribers = ResolutionFailureDescriberRegistry.Companion.emptyRegistry(instanceGenerator)

        configureAdditionalDataBuilder(problemsService.infrastructure!!.additionalDataBuilderFactory)
    }

    // region Component Selection failures
    // TODO: Route more of these failures through this handler in order to standardize their description logic
    fun componentRejected(component: ComponentState, conflictResolutions: MutableList<String>): AbstractResolutionFailureException {
        val assessedSelection = SelectionReasonAssessor.assessSelection(component.getModule())
        val legacyErrorMsg = component.getRejectedErrorMessage()
        val failure = ModuleRejectedFailure(ResolutionFailureProblemId.NO_VERSION_SATISFIES, assessedSelection, conflictResolutions, legacyErrorMsg)
        return describeFailure<ModuleRejectedFailure>(failure)
    }

    fun nodeRejected(node: NodeState): AbstractResolutionFailureException {
        val assessedSelection = SelectionReasonAssessor.assessSelection(node.getComponent().getModule())
        val legacyErrorMsg = node.getRejectedErrorMessage()
        val failure = ModuleRejectedFailure(ResolutionFailureProblemId.NO_VERSION_SATISFIES, assessedSelection, mutableListOf<String>(), legacyErrorMsg)
        return describeFailure<ModuleRejectedFailure>(failure)
    }

    // endregion Component Selection failures
    // region Variant Selection failures
    fun configurationNotCompatibleFailure(
        matcher: AttributeMatcher,
        targetComponent: ComponentGraphResolveState,
        targetConfiguration: VariantGraphResolveState,
        requestedAttributes: AttributeContainerInternal,
        targetConfigurationCapabilities: ImmutableCapabilities
    ): AbstractResolutionFailureException {
        val resolutionCandidateAssessor = ResolutionCandidateAssessor(requestedAttributes, matcher)
        val assessedCandidates = mutableListOf<ResolutionCandidateAssessor.AssessedCandidate>(
            resolutionCandidateAssessor.assessCandidate(
                targetConfiguration.getName(),
                targetConfigurationCapabilities,
                targetConfiguration.getAttributes()
            )
        )
        val failure = ConfigurationNotCompatibleFailure(targetComponent.getId(), targetConfiguration.getName(), requestedAttributes, assessedCandidates)
        return describeFailure<ConfigurationNotCompatibleFailure>(failure)
    }

    fun configurationDoesNotExistFailure(
        targetComponent: ComponentGraphResolveState,
        targetConfigurationName: String
    ): AbstractResolutionFailureException {
        val failure = ConfigurationDoesNotExistFailure(targetComponent.getId(), targetConfigurationName)
        return describeFailure<ConfigurationDoesNotExistFailure>(failure)
    }

    fun ambiguousVariantsFailure(
        matcher: AttributeMatcher,
        targetComponent: ComponentGraphResolveState,
        requestedAttributes: AttributeContainerInternal,
        requestedCapabilities: MutableSet<CapabilitySelector>,
        matchingVariants: MutableList<out VariantGraphResolveState>
    ): AbstractResolutionFailureException {
        val resolutionCandidateAssessor = ResolutionCandidateAssessor(requestedAttributes, matcher)
        val assessedCandidates = resolutionCandidateAssessor.assessResolvedVariantStates(matchingVariants, targetComponent.getDefaultCapability())
        val failure = AmbiguousVariantsFailure(targetComponent.getId(), requestedAttributes, ImmutableSet.copyOf<CapabilitySelector>(requestedCapabilities), assessedCandidates)
        return describeFailure<AmbiguousVariantsFailure>(failure)
    }

    // TODO: This is the same logic as the noCompatibleVariantsFailure case for now.  We want to split the NoCompatibleVariantsFailureDescriber into
    // separate describers, with separate failures, for these different types.
    fun noVariantsFailure(
        targetComponent: ComponentGraphResolveState,
        requestedAttributes: AttributeContainerInternal
    ): AbstractResolutionFailureException {
        val assessedCandidates = mutableListOf<ResolutionCandidateAssessor.AssessedCandidate>()
        val failure = NoCompatibleVariantsFailure(targetComponent.getId(), requestedAttributes, ImmutableSet.of<CapabilitySelector>(), assessedCandidates)
        return describeFailure<NoCompatibleVariantsFailure>(failure)
    }

    fun noCompatibleVariantsFailure(
        matcher: AttributeMatcher,
        targetComponent: ComponentGraphResolveState,
        requestedAttributes: AttributeContainerInternal,
        requestedCapabilities: MutableSet<CapabilitySelector>,
        candidates: GraphSelectionCandidates
    ): AbstractResolutionFailureException {
        val resolutionCandidateAssessor = ResolutionCandidateAssessor(requestedAttributes, matcher)
        val assessedCandidates = resolutionCandidateAssessor.assessGraphSelectionCandidates(candidates)
        val failure = NoCompatibleVariantsFailure(targetComponent.getId(), requestedAttributes, ImmutableSet.copyOf<CapabilitySelector>(requestedCapabilities), assessedCandidates)
        return describeFailure<NoCompatibleVariantsFailure>(failure)
    }

    fun noVariantsWithMatchingCapabilitiesFailure(
        matcher: AttributeMatcher,
        targetComponent: ComponentGraphResolveState,
        requestedAttributes: ImmutableAttributes,
        requestedCapabilities: MutableSet<CapabilitySelector>,
        candidates: MutableList<out VariantGraphResolveState>
    ): AbstractResolutionFailureException {
        val resolutionCandidateAssessor = ResolutionCandidateAssessor(requestedAttributes, matcher)
        val assessedCandidates = resolutionCandidateAssessor.assessResolvedVariantStates(candidates, targetComponent.getDefaultCapability())
        val failure = NoVariantsWithMatchingCapabilitiesFailure(targetComponent.getId(), requestedAttributes, ImmutableSet.copyOf<CapabilitySelector>(requestedCapabilities), assessedCandidates)
        return describeFailure<NoVariantsWithMatchingCapabilitiesFailure>(failure)
    }

    // endregion Variant Selection failures
    // region Graph Validation failures
    fun incompatibleMultipleNodesValidationFailure(
        matcher: AttributeMatcher,
        selectedComponent: ComponentGraphResolveMetadata,
        incompatibleNodes: MutableSet<VariantGraphResolveMetadata>
    ): AbstractResolutionFailureException {
        val resolutionCandidateAssessor = ResolutionCandidateAssessor(ImmutableAttributes.EMPTY, matcher)
        val assessedCandidates = resolutionCandidateAssessor.assessNodeMetadatas(incompatibleNodes)
        val failure = IncompatibleMultipleNodesValidationFailure(selectedComponent, incompatibleNodes, assessedCandidates)
        return describeFailure<IncompatibleMultipleNodesValidationFailure>(failure)
    }

    // endregion Graph Validation failures
    // region Artifact Selection failures
    fun ambiguousArtifactTransformsFailure(
        targetVariantSet: ResolvedVariantSet,
        requestedAttributes: ImmutableAttributes,
        transformedVariants: MutableCollection<TransformedVariant>
    ): AbstractResolutionFailureException {
        val transformationChainDatas = transformedVariantConverter.convert(transformedVariants)
        val failure = AmbiguousArtifactTransformsFailure(targetVariantSet.componentIdentifier, targetVariantSet.asDescribable().getDisplayName(), requestedAttributes, transformationChainDatas)
        return describeFailure<AmbiguousArtifactTransformsFailure>(failure)
    }

    fun noCompatibleArtifactFailure(
        matcher: AttributeMatcher,
        targetVariantSet: ResolvedVariantSet,
        requestedAttributes: ImmutableAttributes
    ): AbstractResolutionFailureException {
        val resolutionCandidateAssessor = ResolutionCandidateAssessor(requestedAttributes, matcher)
        val assessedCandidates = resolutionCandidateAssessor.assessResolvedVariants(targetVariantSet.candidates)
        val failure = NoCompatibleArtifactFailure(targetVariantSet.componentIdentifier, targetVariantSet.asDescribable().getDisplayName(), requestedAttributes, assessedCandidates)
        return describeFailure<NoCompatibleArtifactFailure>(failure)
    }

    fun ambiguousArtifactsFailure(
        matcher: AttributeMatcher,
        targetVariantSet: ResolvedVariantSet,
        requestedAttributes: ImmutableAttributes,
        matchingVariants: MutableList<out ResolvedVariant>
    ): AbstractResolutionFailureException {
        val resolutionCandidateAssessor = ResolutionCandidateAssessor(requestedAttributes, matcher)
        val assessedCandidates = resolutionCandidateAssessor.assessResolvedVariants(matchingVariants)
        val failure = AmbiguousArtifactsFailure(targetVariantSet.componentIdentifier, targetVariantSet.asDescribable().getDisplayName(), requestedAttributes, assessedCandidates)
        return describeFailure<AmbiguousArtifactsFailure>(failure)
    }

    fun unknownArtifactVariantSelectionFailure(
        targetVariantSet: ResolvedVariantSet,
        requestAttributes: ImmutableAttributes,
        cause: Exception
    ): AbstractResolutionFailureException {
        val failure = UnknownArtifactSelectionFailure(targetVariantSet.componentIdentifier, targetVariantSet.asDescribable().getDisplayName(), requestAttributes, cause)
        return describeFailure<UnknownArtifactSelectionFailure>(failure)
    }

    // endregion Artifact Selection failures
    /**
     * Adds a [ResolutionFailureDescriber] for the given failure type to the custom describers
     * registered on this failure handler.
     *
     * If variant selection failures occur, these describers will be available to describe the failures.
     *
     * @param failureType The type of failure to describe
     * @param describerType A describer that can potentially describe failures of the given type
     *
     * @param <FAILURE> The type of failure to describe
    </FAILURE> */
    fun <FAILURE : ResolutionFailure?> addFailureDescriber(failureType: Class<FAILURE?>, describerType: Class<out ResolutionFailureDescriber<FAILURE?>>) {
        customFailureDescribers.registerDescriber<FAILURE?>(failureType, describerType)
    }

    private fun <FAILURE : ResolutionFailure?> describeFailure(failure: FAILURE?): AbstractResolutionFailureException {
        val failureType = failure!!.javaClass as Class<FAILURE?>
        return Stream.concat<ResolutionFailureDescriber<FAILURE?>>(
            customFailureDescribers.getDescribers<FAILURE?>(failureType).stream(),
            defaultFailureDescribers.getDescribers<FAILURE?>(failureType).stream()
        )
            .filter { describer: ResolutionFailureDescriber<FAILURE?>? -> describer!!.canDescribeFailure(failure) }
            .findFirst()
            .map<AbstractResolutionFailureException>(Function { describer: ResolutionFailureDescriber<FAILURE?>? -> describer!!.describeFailure(failure) })
            .orElseThrow<IllegalStateException>(Supplier { IllegalStateException("No describer found for failure: " + failure) }) // TODO: a default describer at the end of the list that catches everything instead?
    }

    companion object {
        const val DEFAULT_MESSAGE_PREFIX: String = "Review the variant matching algorithm at "

        private fun configureAdditionalDataBuilder(additionalDataBuilderFactory: AdditionalDataBuilderFactory) {
            if (!additionalDataBuilderFactory.hasProviderForSpec<ResolutionFailureDataSpec?>(ResolutionFailureDataSpec::class.java)) {
                additionalDataBuilderFactory.registerAdditionalDataProvider(
                    ResolutionFailureDataSpec::class.java,
                    com.google.common.base.Function { data: AdditionalData? -> DefaultResolutionFailureData.builder(data as ResolutionFailureData) }
                )
            }
        }
    }
}
