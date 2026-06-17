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
package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.internal.notations.DependencyMetadataNotationParser.parser
import org.gradle.api.internal.notations.ComponentIdentifierParserFactory.create
import org.gradle.internal.rules.RuleActionAdapter.createFromAction
import org.gradle.internal.rules.RuleActionAdapter.createFromClosure
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBeRemovedInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.internal.rules.RuleActionAdapter.createFromRuleSource
import org.gradle.internal.component.external.model.ivy.RealisedIvyModuleResolveMetadata.Companion.transform
import org.gradle.internal.component.external.model.maven.RealisedMavenModuleResolveMetadata.Companion.transform
import org.gradle.internal.serialize.Serializer.write
import org.gradle.internal.serialize.Serializer.read
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata.withDerivationStrategy
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata.asMutable
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.asImmutable
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata.getId
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata.isComponentMetadataRuleCachingEnabled
import org.gradle.internal.resolve.caching.CrossBuildCachingRuleExecutor.execute
import org.gradle.api.specs.Spec.isSatisfiedBy
import org.gradle.internal.rules.RuleAction.execute
import org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainer.addRepository
import org.gradle.api.internal.artifacts.BaseRepositoryFactory.createFlatDirRepository
import org.gradle.internal.deprecation.DeprecationMessageBuilder.withAdvice
import org.gradle.util.internal.CollectionUtils.flattenCollections
import org.gradle.api.internal.artifacts.BaseRepositoryFactory.createGradlePluginPortal
import org.gradle.api.internal.artifacts.BaseRepositoryFactory.createMavenCentralRepository
import org.gradle.api.internal.artifacts.BaseRepositoryFactory.createMavenLocalRepository
import org.gradle.api.internal.artifacts.BaseRepositoryFactory.createGoogleRepository
import org.gradle.api.internal.artifacts.BaseRepositoryFactory.createMavenRepository
import org.gradle.api.internal.artifacts.BaseRepositoryFactory.createIvyRepository
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector.Companion.newSelector
import org.gradle.internal.deprecation.DeprecationLogger.deprecateAction
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBecomeAnErrorInGradle10
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata.variantDerivationStrategy

