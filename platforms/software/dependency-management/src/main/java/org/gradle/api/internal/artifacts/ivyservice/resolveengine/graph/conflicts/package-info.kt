/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts

import org.gradle.internal.component.local.model.LocalVariantGraphResolveState.files
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getDisplayName
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints.contains
import org.gradle.internal.component.model.DependencyMetadata.overrideVariantSelection
import org.gradle.internal.component.model.ComponentGraphResolveState.getCandidatesForGraphVariantSelection
import org.gradle.internal.component.model.GraphSelectionCandidates.getVariantsForAttributeMatching
import org.gradle.internal.component.model.GraphVariantSelector.selectByAttributeMatching
import org.gradle.internal.component.model.DependencyMetadata.selectLegacyVariants
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.of
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal.withDescription
import org.gradle.internal.component.model.VariantGraphResolveState.getMetadata
import org.gradle.internal.component.model.VariantGraphResolveMetadata.isTransitive
import org.gradle.internal.component.model.VariantGraphResolveMetadata.isExternalVariant
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getId
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState.componentId
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints.equals
import org.gradle.internal.component.model.VariantGraphResolveState.getDependencies
import org.gradle.internal.Try.get
import org.gradle.internal.component.model.DependencyMetadata.withTarget
import org.gradle.internal.component.model.DependencyMetadata.withTargetAndArtifacts
import org.gradle.internal.component.model.ComponentGraphResolveMetadata.getPlatformOwners
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector.Companion.newSelector
import org.gradle.api.specs.Spec.isSatisfiedBy
import org.gradle.api.internal.artifacts.ComponentSelectorConverter.getModuleVersionId
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints.intersect
import org.gradle.internal.component.model.VariantGraphResolveState.getExcludes
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints.minus
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints.union
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getCapabilities
import org.gradle.internal.component.external.model.ImmutableCapabilities.isEmpty
import org.gradle.internal.component.external.model.ImmutableCapabilities.asSet
import org.gradle.internal.logging.text.TreeFormatter.node
import org.gradle.internal.logging.text.TreeFormatter.startChildren
import org.gradle.internal.logging.text.TreeFormatter.endChildren
import org.gradle.internal.logging.text.TreeFormatter.toString
import org.gradle.internal.component.model.DependencyMetadata.withReason
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState.moduleVersionId
import org.gradle.internal.component.model.ComponentGraphResolveState.getId
import org.gradle.api.internal.attributes.matching.AttributeMatchingCandidate.attributes
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState.getMetadata
import org.gradle.internal.component.local.model.LocalComponentGraphResolveMetadata.getAttributesSchema
import org.gradle.internal.component.model.ComponentIdGenerator.nextGraphNodeId
import org.gradle.api.internal.attributes.AttributeDesugaring.desugarSelector
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult.failed
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata.Companion.forDependency
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver.resolve
import org.gradle.internal.resolve.result.ComponentIdResolveResult.getFailure
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal.hasCustomDescription
import org.gradle.internal.resolve.RejectedByAttributesVersion.describeTo
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState.getRepositoryName
import org.gradle.internal.component.model.ComponentGraphResolveState.getMetadata
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver.resolve
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult.getFailure
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult.getState
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult.getGraphState
import org.gradle.internal.component.model.ComponentIdGenerator.nextComponentId
import org.gradle.internal.component.model.ComponentIdGenerator.nextVariantId
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.root
import org.gradle.internal.component.model.ComponentGraphResolveState.getDefaultCapability
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal.isEquivalentToForce
import org.gradle.internal.component.model.ForcingDependencyMetadata.isForce
import org.gradle.internal.component.model.LocalOriginDependencyMetadata.isFromLock
import org.gradle.internal.deprecation.DeprecationLogger.deprecateAction
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withAdvice
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBecomeAnErrorInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver.isFetchingMetadataCheap
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState.isRejected
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler.componentRejected
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler.nodeRejected
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint.accepts
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint.canBeStable
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState.metadataOrNull
import org.gradle.internal.component.model.ComponentGraphResolveMetadata.isChanging
import org.gradle.api.internal.artifacts.ivyservice.ResolutionParameters.FailureResolutions.forVersionConflict
import org.gradle.api.internal.attributes.AttributeSchemaServices.getMatcher
import org.gradle.internal.component.model.ComponentGraphResolveMetadata.getAttributesSchema
import org.gradle.api.internal.attributes.matching.AttributeMatcher.areMutuallyCompatible
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getAttributes
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler.incompatibleMultipleNodesValidationFailure
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal.hasCustomDescriptions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal.getDescriptions
import org.gradle.internal.component.model.ComponentGraphResolveMetadata.getId
import org.gradle.internal.component.model.AbstractComponentGraphResolveState.getMetadata
import org.gradle.internal.component.model.ComponentGraphResolveMetadata.getModuleVersionId
import org.gradle.internal.component.model.ImmutableModuleSources.Companion.of
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getName
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.internal.logging.text.TreeFormatter.append
import org.gradle.api.internal.DocumentationRegistry.getDocumentationFor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedDependencyGraph.requestAttributes
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedDependencyGraph.graphSource
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedDependencyGraph.availableVariantsByComponent
import org.gradle.api.internal.notations.ComponentIdentifierParserFactory.create
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver.select
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails.hasFailure
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState.addCause
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal.describable
import org.gradle.internal.resolve.result.ComponentIdResolveResult.mark
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState.reject
import org.gradle.internal.resolve.ModuleVersionResolveException.withIncomingPaths
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.IncompatibleDependencyAttributesException

