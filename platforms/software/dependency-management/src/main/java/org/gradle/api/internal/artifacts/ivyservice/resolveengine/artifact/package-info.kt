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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import org.gradle.internal.component.local.model.LocalFileDependencyMetadata.componentId
import org.gradle.api.specs.Spec.isSatisfiedBy
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata.files
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry.mapAttributesFor
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector.select
import org.gradle.internal.component.model.DefaultIvyArtifactName.Companion.forFile
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode.incomingEdges
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge.isTransitive
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphDependency.isConstraint
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode.owner
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphComponent.resolveState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant.resolveState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode.isRoot
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode.outgoingFileEdges
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant.id
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge.attributes
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge.dependencyMetadata
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge.exclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec.mayExcludeArtifacts
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple.DefaultExcludeFactory.nothing
import org.gradle.internal.component.model.VariantGraphResolveState.getInstanceId
import org.gradle.internal.component.model.ComponentGraphResolveState.getId
import org.gradle.internal.component.model.ComponentGraphResolveState.getMetadata
import org.gradle.internal.component.model.ComponentGraphResolveMetadata.getAttributesSchema
import org.gradle.internal.component.model.ComponentGraphResolveState.prepareForArtifactResolution
import org.gradle.internal.component.model.ComponentArtifactResolveState.getArtifactMetadata
import org.gradle.internal.component.model.VariantGraphResolveState.prepareForArtifactResolution
import org.gradle.internal.component.model.VariantArtifactResolveState.getAdhocArtifacts
import org.gradle.internal.resolve.resolver.VariantArtifactResolver.resolveAdhocVariant
import org.gradle.internal.component.model.VariantGraphResolveState.getMetadata
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getId
import org.gradle.internal.component.model.GraphVariantSelector.selectByAttributeMatchingLenient
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata.getModuleVersionId
import org.gradle.internal.component.model.VariantArtifactResolveState.getArtifactVariants
import org.gradle.internal.resolve.resolver.VariantArtifactResolver.resolveVariantArtifactSet

