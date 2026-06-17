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
package org.gradle.internal.component.external.model

import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier.getComponentIdentifier
import org.gradle.internal.component.model.ComponentIdGenerator.nextComponentId
import org.gradle.internal.component.model.DelegatingDependencyMetadata.getSelector
import org.gradle.internal.component.model.DependencyMetadata.withTarget
import org.gradle.internal.component.model.DependencyMetadata.withReason
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getName
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getId
import org.gradle.internal.component.model.VariantGraphResolveMetadata.isTransitive
import org.gradle.internal.component.model.ConfigurationGraphResolveMetadata.isVisible
import org.gradle.internal.component.model.ConfigurationGraphResolveMetadata.getHierarchy
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getAttributes
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getCapabilities
import org.gradle.internal.component.model.VariantGraphResolveMetadata.isExternalVariant
import org.gradle.internal.component.model.VariantResolveMetadata.asDescribable
import org.gradle.internal.component.model.VariantResolveMetadata.capabilities
import org.gradle.internal.component.model.VariantResolveMetadata.isExternalVariant
import org.gradle.internal.component.model.VariantResolveMetadata.isEligibleForCaching
import org.gradle.internal.component.model.ComponentArtifactMetadata.getName
import org.gradle.internal.component.model.VariantAttributesRules.execute
import org.gradle.internal.component.model.DependencyMetadataRules.execute
import org.gradle.internal.component.model.VariantFilesRules.executeForArtifacts
import org.gradle.internal.component.model.VariantFilesRules.executeForFiles
import org.gradle.internal.component.model.DependencyMetadataRules.addDependencyAction
import org.gradle.internal.component.model.DependencyMetadataRules.addDependencyConstraintAction
import org.gradle.internal.component.model.VariantAttributesRules.addAttributesAction
import org.gradle.internal.component.model.VariantFilesRules.addFilesAction

