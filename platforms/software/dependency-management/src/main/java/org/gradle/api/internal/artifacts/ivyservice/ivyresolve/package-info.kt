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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.internal.serialize.Encoder.writeBinary
import org.gradle.internal.serialize.Encoder.writeBoolean
import org.gradle.internal.serialize.Decoder.readBinary
import org.gradle.internal.serialize.Decoder.readBoolean
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult.failed
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata.getSources
import org.gradle.internal.resolve.result.ResolveResult.hasResult
import org.gradle.internal.component.model.ComponentArtifactMetadata.getId
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata.getModuleVersionId
import org.gradle.internal.component.model.ComponentArtifactMetadata.getName
import org.gradle.internal.component.model.ComponentArtifactMetadata.getBuildDependencies
import org.gradle.internal.resolve.result.BuildableTypedResolveResult.resolved
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult.getResult
import org.gradle.internal.component.model.ModuleSources.getSource
import org.gradle.internal.resolve.result.BuildableComponentResolveResult.applyTo
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost.isFast
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost.isExpensive
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState.getId
import org.gradle.internal.resolve.result.BuildableComponentResolveResult.resolved
import org.gradle.internal.resolve.result.BuildableComponentResolveResult.failed
import org.gradle.internal.resolve.result.BuildableComponentResolveResult.notFound
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult.getFailure
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult.rejected
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult.resolved
import org.gradle.StartParameter.isOffline
import org.gradle.StartParameter.getWriteDependencyVerifications
import org.gradle.StartParameter.isExportKeys
import org.gradle.StartParameter.isDryRun
import org.gradle.StartParameter.dependencyVerificationMode
import org.gradle.StartParameter.getGradleUserHomeDir
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult.failed
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult.failed
import org.gradle.internal.resolve.result.ErroringResolveResult.failed
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata.getId

