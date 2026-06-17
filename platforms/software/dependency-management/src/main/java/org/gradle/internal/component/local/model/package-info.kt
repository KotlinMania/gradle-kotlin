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
package org.gradle.internal.component.local.model

import org.gradle.internal.component.model.AbstractComponentGraphResolveState.getMetadata
import org.gradle.internal.component.model.ComponentGraphResolveMetadata.getModuleVersionId
import org.gradle.internal.component.model.VariantGraphResolveState.getMetadata
import org.gradle.internal.component.model.ComponentGraphResolveMetadata.getId
import org.gradle.internal.component.model.ImmutableModuleSources.Companion.of
import org.gradle.internal.component.model.ComponentGraphResolveMetadata.getAttributesSchema
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getId
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getName
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getAttributes
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getCapabilities
import org.gradle.internal.component.model.VariantArtifactResolveState.getArtifactVariants
import org.gradle.internal.component.model.ComponentArtifactMetadata.getName
import org.gradle.internal.component.model.DependencyMetadata.withTarget
import org.gradle.internal.component.model.DependencyMetadata.withTargetAndArtifacts
import org.gradle.internal.component.model.ForcingDependencyMetadata.isForce
import org.gradle.internal.component.model.ForcingDependencyMetadata.forced
import org.gradle.internal.component.model.ComponentIdGenerator.nextComponentId
import org.gradle.internal.component.model.ComponentIdGenerator.nextVariantId
import org.gradle.internal.component.model.DefaultIvyArtifactName.Companion.forPublishArtifact

