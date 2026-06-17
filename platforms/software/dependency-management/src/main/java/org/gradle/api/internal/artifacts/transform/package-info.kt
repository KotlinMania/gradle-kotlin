/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.internal.component.external.model.ImmutableCapabilities.asSet
import org.gradle.api.internal.attributes.AttributeDesugaring.desugar
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.resolve.ModuleVersionResolveException.getMessage
import org.gradle.internal.Try.map
import org.gradle.internal.Deferrable.map
import org.gradle.internal.Try.getOrMapFailure
import org.gradle.internal.Try.Companion.failure
import org.gradle.internal.Deferrable.completeAndGet
import org.gradle.internal.Try.get
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.file.PathTraversalChecker.safePathName
import org.gradle.internal.service.ServiceLookup.get
import org.gradle.api.problems.internal.ProblemSpecInternal.id
import org.gradle.api.problems.internal.GradleCoreProblemGroup.validation
import org.gradle.api.problems.internal.GradleCoreProblemGroup.ValidationProblemGroup.property
import org.gradle.api.problems.internal.ProblemSpecInternal.documentedAt
import org.gradle.internal.deprecation.Documentation.Companion.userManual
import org.gradle.api.problems.internal.ProblemSpecInternal.contextualLabel
import org.gradle.api.problems.internal.ProblemSpecInternal.details
import org.gradle.api.problems.internal.ProblemSpecInternal.solution
import org.gradle.internal.service.ServiceLookup.find
import org.gradle.internal.logging.text.TreeFormatter.node
import org.gradle.internal.logging.text.TreeFormatter.appendValue
import org.gradle.internal.logging.text.TreeFormatter.append
import org.gradle.internal.logging.text.TreeFormatter.appendType
import org.gradle.internal.logging.text.TreeFormatter.toString
import org.gradle.operations.dependencies.variants.Capability.group
import org.gradle.operations.dependencies.variants.Capability.name
import org.gradle.operations.dependencies.variants.Capability.version
import org.gradle.internal.Try.flatMap
import org.gradle.api.internal.attributes.matching.AttributeMatchingCandidate.attributes
import org.gradle.internal.lazy.Lazy.Companion.locking
import org.gradle.internal.lazy.Lazy.Factory.of
import org.gradle.api.internal.attributes.AttributeSchemaServices.schemaFactory
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory.create
import org.gradle.api.internal.attributes.AttributeSchemaServices.getMatcher
import org.gradle.api.internal.attributes.matching.AttributeMatcher.isMatchingCandidate
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.internal.Try.mapFailure
import org.gradle.api.internal.attributes.matching.AttributeMatcher.matchMultipleCandidates
import org.gradle.internal.lazy.Lazy.Companion.unsafe
import org.gradle.internal.component.resolution.failure.transform.TransformedVariantConverter.convert
import org.gradle.internal.component.resolution.failure.transform.TransformationChainData.fingerprint
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler.ambiguousArtifactTransformsFailure
import org.gradle.internal.Deferrable.flatMap
import org.gradle.internal.Try.ifSuccessfulOrElse
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler.unknownArtifactVariantSelectionFailure
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler.ambiguousArtifactsFailure
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler.noCompatibleArtifactFailure
import org.gradle.operations.execution.FilePropertyVisitor.VisitState.propertyHashBytes
import org.gradle.operations.execution.FilePropertyVisitor.VisitState.propertyAttributes
import org.gradle.operations.dependencies.transforms.ExecutePlannedTransformStepBuildOperationType.Details.plannedTransformStepIdentity
import org.gradle.operations.dependencies.transforms.ExecutePlannedTransformStepBuildOperationType.Details.transformActionClass

