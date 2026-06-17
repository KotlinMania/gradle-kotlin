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
package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.Factory.create
import org.gradle.StartParameter.isBuildProjectDependencies
import org.gradle.api.internal.attributes.matching.AttributeMatchingCandidate.attributes
import org.gradle.internal.locking.DependencyLockingGraphVisitor.collectLockingFailures
import org.gradle.internal.locking.DependencyLockingGraphVisitor.writeLocks
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState.getMetadata
import org.gradle.internal.component.local.model.LocalComponentGraphResolveMetadata.getAttributesSchema
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector.Companion.newSelector
import org.gradle.api.internal.attributes.AttributeDesugaring.desugar
import org.gradle.internal.component.external.model.ImmutableCapabilities.asSet
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState.files
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState.getDependencies
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState.moduleVersionId
import org.gradle.internal.component.model.ComponentGraphResolveState.getId
import org.gradle.internal.component.model.VariantGraphResolveState.getMetadata
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getAttributes
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getCapabilities
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getName
import org.gradle.internal.exceptions.DefaultMultiCauseException.getResolutions
import org.gradle.internal.versionedcache.UnusedVersionsCacheCleanup.Companion.create
import org.gradle.internal.time.TimestampSuppliers.daysAgo

