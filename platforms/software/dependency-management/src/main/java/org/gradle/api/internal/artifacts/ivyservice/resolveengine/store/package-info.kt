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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.store

import org.gradle.internal.serialize.Decoder.readBoolean
import org.gradle.internal.serialize.Decoder.readString
import org.gradle.internal.serialize.Encoder.writeBoolean
import org.gradle.internal.serialize.Encoder.writeString
import org.gradle.internal.serialize.Decoder.readNullableString
import org.gradle.internal.serialize.Encoder.writeNullableString
import org.gradle.api.internal.attributes.AttributeDesugaring.desugar
import org.gradle.internal.component.external.model.ImmutableCapabilities.isEmpty
import org.gradle.internal.component.external.model.DefaultImmutableCapability.Companion.defaultCapabilityForComponent
import org.gradle.internal.serialize.Decoder.readByte
import org.gradle.api.internal.artifacts.ModuleComponentSelectorSerializer.read
import org.gradle.internal.serialize.Decoder.readSmallInt
import org.gradle.internal.serialize.Encoder.writeSmallInt
import org.gradle.internal.serialize.Encoder.writeByte
import org.gradle.api.internal.artifacts.ModuleComponentSelectorSerializer.write
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector.getProjectIdentity
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector.getAttributes
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector.getCapabilitySelectors
import org.gradle.internal.serialize.Decoder.readLong
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.getGroup
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.getModule
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.getVersion
import org.gradle.internal.serialize.Encoder.writeLong
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier.file
import org.gradle.internal.component.external.model.ImmutableCapabilities.asSet
import org.gradle.internal.serialize.ListSerializer.read
import org.gradle.internal.serialize.ListSerializer.write
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getAttributes
import org.gradle.internal.serialize.Encoder.writeSmallLong
import org.gradle.internal.component.model.ComponentGraphResolveState.isAdHoc
import org.gradle.internal.serialize.Serializer.write
import org.gradle.internal.component.model.ComponentGraphResolveState.getId
import org.gradle.internal.component.model.ComponentGraphResolveState.getMetadata
import org.gradle.internal.component.model.ComponentGraphResolveMetadata.getModuleVersionId
import org.gradle.internal.component.model.ComponentGraphResolveState.getCandidatesForGraphVariantSelection
import org.gradle.internal.component.model.GraphSelectionCandidates.getVariantsForAttributeMatching
import org.gradle.internal.component.model.VariantGraphResolveState.prepareForArtifactResolution
import org.gradle.internal.component.model.VariantArtifactResolveState.getArtifactVariants
import org.gradle.internal.component.model.VariantResolveMetadata.capabilities
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getName
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getCapabilities
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.time.Time.startTimer
import org.gradle.internal.serialize.Decoder.readSmallLong
import org.gradle.internal.serialize.Serializer.read
import org.gradle.internal.component.model.VariantGraphResolveState.getMetadata
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector.Companion.newSelector
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal.getSelectionReason
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult.getRequested
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult.getSelected
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedDependencyResult.getRequested
import org.gradle.internal.component.model.ComponentGraphResolveState.getInstanceId
import org.gradle.internal.component.model.VariantGraphResolveState.getInstanceId
import org.gradle.internal.serialize.Decoder.readInt
import org.gradle.internal.serialize.Encoder.writeInt
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.time.TimeFormatting.formatDurationVerbose
import org.gradle.internal.serialize.kryo.StringDeduplicatingKryoBackedEncoder.getWritePosition
import org.gradle.internal.serialize.kryo.StringDeduplicatingKryoBackedEncoder.done
import org.gradle.internal.serialize.kryo.StringDeduplicatingKryoBackedEncoder.flush
import org.gradle.internal.serialize.kryo.StringDeduplicatingKryoBackedEncoder.close

