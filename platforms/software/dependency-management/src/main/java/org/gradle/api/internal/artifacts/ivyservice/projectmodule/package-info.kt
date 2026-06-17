/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule

import org.gradle.internal.event.ListenerManager.getBroadcaster
import org.gradle.internal.deprecation.DeprecationLogger.deprecateAction
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withContext
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withAdvice
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBecomeAnErrorInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.internal.logging.text.TreeFormatter.node
import org.gradle.internal.logging.text.TreeFormatter.startChildren
import org.gradle.internal.logging.text.TreeFormatter.endChildren
import org.gradle.internal.logging.text.TreeFormatter.toString
import org.gradle.internal.lazy.Lazy.Companion.locking
import org.gradle.internal.lazy.Lazy.Factory.of
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.component.model.ComponentArtifactMetadata.getId
import org.gradle.internal.component.model.ComponentArtifactMetadata.getComponentId
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata.file
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata.getModuleVersionId
import org.gradle.internal.component.model.ComponentArtifactMetadata.getName
import org.gradle.internal.component.model.ComponentArtifactMetadata.getBuildDependencies
import org.gradle.internal.resolve.result.BuildableTypedResolveResult.resolved
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult.notFound
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector.toIdentifier
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState.moduleVersionId
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult.rejected
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult.resolved
import org.gradle.internal.resolve.result.BuildableComponentResolveResult.resolved
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata.getId

