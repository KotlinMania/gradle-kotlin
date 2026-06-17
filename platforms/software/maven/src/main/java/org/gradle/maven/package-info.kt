/*
 * Copyright 2014 the original author or authors.
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
 * Component types for Maven modules.
 */
package org.gradle.maven

import org.gradle.internal.serialization.Transient.Companion.varOf
import org.gradle.internal.serialization.Transient.get
import org.gradle.internal.serialization.Transient.Var.set
import org.gradle.internal.serialization.Cached.get
import org.gradle.api.publish.internal.PublishOperation.run
import org.gradle.api.publish.internal.PublicationInternal.publishableArtifacts
import org.gradle.api.publish.internal.PublicationArtifactSet.files
import org.gradle.api.internal.artifacts.repositories.AbstractAuthenticationSupportedRepository.getConfiguredCredentials
import org.gradle.api.internal.artifacts.repositories.AbstractArtifactRepository.getName
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository.getUrl
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository.isAllowInsecureProtocol
import org.gradle.api.internal.artifacts.repositories.AbstractAuthenticationSupportedRepository.getConfiguredAuthentication
import org.gradle.internal.service.ServiceRegistry.get
import org.gradle.api.internal.artifacts.BaseRepositoryFactory.createMavenRepository
import org.gradle.api.internal.artifacts.repositories.AbstractResolutionAwareArtifactRepository.setName
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository.setUrl
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository.setAllowInsecureProtocol
import org.gradle.api.internal.artifacts.repositories.AbstractAuthenticationSupportedRepository.setConfiguredCredentials
import org.gradle.api.internal.artifacts.repositories.AbstractAuthenticationSupportedRepository.authentication
import org.gradle.api.publish.PublishingExtension.publications
import org.gradle.api.publish.PublishingExtension.repositories
import org.gradle.api.publish.tasks.GenerateModuleMetadata.getPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata.getPublications
import org.gradle.api.publish.tasks.GenerateModuleMetadata.outputFile
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.api.publish.internal.PublicationInternal.DerivedArtifact.create
import org.gradle.api.publish.internal.PublicationArtifactInternal.shouldBePublished
import org.gradle.api.publish.internal.PublicationInternal.DerivedArtifact.shouldBePublished
import org.gradle.api.publish.PublicationArtifact.file
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.api.publish.PublicationArtifact.builtBy
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory.createFileTransport
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository.getTransport
import org.gradle.internal.resource.ExternalResourceReadResult.result
import org.gradle.internal.Factory.create
import org.gradle.api.internal.artifacts.repositories.transport.NetworkOperationBackOffAndRetry.withBackoffAndRetry
import org.gradle.internal.resource.ExternalResourceRepository.resource
import org.gradle.internal.resource.ExternalResource.withContentIfPresent
import org.gradle.internal.resource.ExternalResourceName.getDisplayName
import org.gradle.internal.resource.ExternalResourceRepository.withProgressLogging
import org.gradle.internal.resource.ExternalResourceName.shortDisplayName
import org.gradle.internal.resource.ExternalResourceName.path
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver.Companion.disableExtraChecksums
import org.gradle.internal.resource.ExternalResourceName.append
import org.gradle.internal.resource.ExternalResource.put
import org.gradle.api.publish.internal.PublicationFieldValidator.notEmpty
import org.gradle.api.publish.internal.PublicationFieldValidator.validInFileName
import org.gradle.api.publish.internal.PublicationFieldValidator.notNull
import org.gradle.api.publish.internal.PublicationFieldValidator.optionalNotEmpty
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.api.publish.internal.validation.DuplicatePublicationTracker.checkCanPublish
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.publish.internal.mapping.DependencyCoordinateResolverFactory.createCoordinateResolvers
import org.gradle.api.publish.internal.component.MavenPublishingAwareVariant.ScopeMapping.scope
import org.gradle.api.publish.internal.component.MavenPublishingAwareVariant.ScopeMapping.isOptional
import org.gradle.api.publish.internal.mapping.DependencyCoordinateResolverFactory.DependencyResolvers.variantResolver
import org.gradle.api.publish.internal.mapping.DependencyCoordinateResolverFactory.DependencyResolvers.componentResolver
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport.isTargetingPlatform
import org.gradle.api.publish.internal.validation.VariantWarningCollector.addIncompatible
import org.gradle.api.publish.internal.validation.VariantWarningCollector.addVariantUnsupported
import org.gradle.api.publish.internal.validation.VariantWarningCollector.addUnsupported
import org.gradle.api.publish.internal.mapping.ComponentDependencyResolver.resolveComponentCoordinates
import org.gradle.api.publish.internal.mapping.ResolvedCoordinates.version
import org.gradle.api.publish.internal.mapping.VariantDependencyResolver.resolveVariantCoordinates
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme.Companion.isSubVersion
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme.Companion.isLatestVersion
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.MavenVersionSelectorScheme.Companion.isSubstituableLatest
import org.gradle.api.publish.internal.mapping.ResolvedCoordinates.group
import org.gradle.api.publish.internal.mapping.ResolvedCoordinates.name
import org.gradle.api.publish.internal.PublicationInternal.coordinates
import org.gradle.api.publish.internal.validation.PublicationWarningsCollector.complete
import org.gradle.util.internal.CollectionUtils.filter
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MavenVersionUtils.inferStatusFromVersionNumber
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme.renderSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme.parseSelector
import org.gradle.internal.service.ServiceRegistration.addProvider
import org.gradle.internal.service.ServiceRegistration.add

