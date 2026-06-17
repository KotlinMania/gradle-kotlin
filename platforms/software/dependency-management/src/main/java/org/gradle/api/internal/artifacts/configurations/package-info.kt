/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations

import org.gradle.internal.Factory.create
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet.visitFiles
import org.gradle.internal.logging.text.TreeFormatter.node
import org.gradle.internal.component.resolution.failure.ReportableAsProblem.reportAsProblem
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet.visitArtifacts
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory.create
import org.gradle.StartParameter.isOffline
import org.gradle.api.internal.artifacts.configurations.CachePolicy.setOffline
import org.gradle.StartParameter.isRefreshDependencies
import org.gradle.api.internal.artifacts.configurations.CachePolicy.setRefreshDependencies
import org.gradle.util.internal.CollectionUtils.collect
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor.properties
import org.gradle.internal.lazy.Lazy.Companion.locking
import org.gradle.internal.lazy.Lazy.Factory.of
import org.gradle.api.internal.artifacts.result.ResolvedGraphResult.structure
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure.nodes
import org.gradle.api.internal.artifacts.result.ResolvedGraphResult.getComponent
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure.Nodes.owner
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure.Nodes.root
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider.visitConsumable
import org.gradle.internal.deprecation.DocumentedFailure.builder
import org.gradle.internal.deprecation.DocumentedFailure.Builder.withSummary
import org.gradle.internal.deprecation.DocumentedFailure.Builder.withAdvice
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUserManual
import org.gradle.internal.deprecation.DocumentedFailure.Builder.build
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.getAttributes
import org.gradle.internal.component.external.model.ImmutableCapabilities.Companion.of
import org.gradle.internal.component.external.model.ImmutableCapabilities.hashCode

