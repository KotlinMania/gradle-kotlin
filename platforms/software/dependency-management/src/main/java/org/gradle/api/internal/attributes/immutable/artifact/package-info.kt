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
package org.gradle.api.internal.attributes.immutable.artifact

import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder.Companion.logging
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder.noCandidates
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder.singleMatch
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder.candidateDoesNotMatchAttributes
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder.candidateIsSuperSetOfAllOthers
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder.candidateAttributeMissing
import org.gradle.internal.component.model.AttributeMatchingExplanationBuilder.candidateAttributeDoesNotMatch
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.component.model.DefaultMultipleCandidateResult.hasResult
import org.gradle.internal.component.model.DefaultMultipleCandidateResult.getMatches
import org.gradle.internal.component.model.ComponentArtifactMetadata.getName
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector.getAttributes
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.api.problems.internal.ProblemSpecInternal.details
import org.gradle.api.problems.internal.ProblemSpecInternal.solution
import org.gradle.api.problems.internal.ProblemSpecInternal.id
import org.gradle.api.problems.internal.GradleCoreProblemGroup.versionCatalog
import org.gradle.api.problems.internal.ProblemSpecInternal.contextualLabel
import org.gradle.api.problems.internal.ProblemSpecInternal.documentedAt
import org.gradle.internal.deprecation.Documentation.Companion.userManual
import org.gradle.internal.management.VersionCatalogBuilderInternal.build
import org.gradle.util.internal.IncubationLogger.incubatingFeatureUsed
import org.gradle.internal.Try.get
import org.gradle.internal.logging.text.TreeFormatter.node
import org.gradle.internal.logging.text.TreeFormatter.startChildren
import org.gradle.internal.logging.text.TreeFormatter.endChildren
import org.gradle.internal.logging.text.TreeFormatter.toString
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.lazy.Lazy.Companion.unsafe
import org.gradle.internal.lazy.Lazy.Factory.of
import org.gradle.api.problems.ProblemSpec.solution

