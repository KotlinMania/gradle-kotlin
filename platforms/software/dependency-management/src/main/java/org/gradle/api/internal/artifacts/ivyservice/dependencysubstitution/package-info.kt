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
package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution

import org.gradle.api.internal.artifacts.DependencySubstitutionInternal.configuredTargetSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory.newDescriptor
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal.configuredArtifactSelectors
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal.ruleDescriptors
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal.withDescription
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal.markAsEquivalentToForce
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal.useTarget
import org.gradle.api.internal.artifacts.ComponentSelectorConverter.getModuleVersionId
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector.Companion.withAttributes
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory.module
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector.Companion.newSelector

