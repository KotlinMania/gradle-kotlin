/*
 * Copyright 2012 the original author or authors.
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
/**
 * Publishing plugin.
 *
 * @since 1.3
 */
package org.gradle.api.publish.plugins

import org.gradle.internal.serialization.Transient.get
import org.gradle.internal.Try.get
import org.gradle.internal.serialization.Cached.get
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.api.internal.artifacts.ArtifactPublicationServices.createRepositoryHandler
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry.registerPublication
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver.resolveComponent
import org.gradle.internal.component.local.model.ProjectComponentSelectorInternal.identityPath
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory.moduleWithVersion
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver.resolveVariant
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory.module
import org.gradle.api.internal.attributes.AttributeDesugaring.desugar
import org.gradle.internal.service.ServiceRegistration.add
import org.gradle.internal.service.ServiceRegistration.addProvider
import org.gradle.api.internal.DocumentationRegistry.getDocumentationRecommendationFor
import org.gradle.internal.logging.text.TreeFormatter.node
import org.gradle.internal.logging.text.TreeFormatter.startChildren
import org.gradle.internal.logging.text.TreeFormatter.endChildren
import org.gradle.internal.logging.text.TreeFormatter.toString
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint.Companion.of
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.runDependencyActions
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.markAsObserved
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.markDependenciesObserved
import org.gradle.internal.deprecation.DeprecationLogger.deprecateBehaviour
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withContext
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withAdvice
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBecomeAnErrorInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.undocumented
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal.getAttributes
import org.gradle.internal.deprecation.Documentation.Companion.upgradeMinorGuide
import org.gradle.internal.deprecation.Documentation.getConsultDocumentationMessage
import org.gradle.api.internal.artifacts.configurations.Configurations.collectCapabilities
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.logging.Logger.lifecycle
import org.gradle.api.internal.attributes.matching.AttributeMatcher.matchMultipleCandidates
import org.gradle.api.internal.attributes.AttributeSchemaServices.schemaFactory
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory.create
import org.gradle.api.internal.attributes.AttributeSchemaServices.getMatcher

