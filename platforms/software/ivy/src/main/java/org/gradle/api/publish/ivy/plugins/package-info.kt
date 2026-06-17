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
 * Plugins for Ivy publishing.
 *
 * @since 1.3
 */
package org.gradle.api.publish.ivy.plugins

import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.api.publish.PublicationArtifact.file
import org.gradle.api.publish.internal.PublicationInternal.DerivedArtifact.create
import org.gradle.api.publish.internal.PublicationInternal.DerivedArtifact.shouldBePublished
import org.gradle.api.specs.Spec.isSatisfiedBy
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.api.publish.PublicationArtifact.builtBy
import org.gradle.api.publish.internal.PublicationFieldValidator.notEmpty
import org.gradle.api.publish.internal.PublicationFieldValidator.validInFileName
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.moduleVersionId
import org.gradle.api.publish.internal.PublicationFieldValidator.optionalNotEmpty
import org.gradle.api.publish.internal.PublicationFieldValidator.doesNotContainSpecialCharacters
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.status
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.AbstractModuleDescriptorParser.parseMetaData
import org.gradle.api.publish.internal.PublicationFieldValidator.notNull
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager.withIvy
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository.createPublisher
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.api.internal.artifacts.repositories.transport.NetworkOperationBackOffAndRetry.withBackoffAndRetry
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver.publish
import org.gradle.api.publish.internal.validation.DuplicatePublicationTracker.checkCanPublish
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.publish.ivy.IvyConfiguration.extend
import org.gradle.api.publish.internal.component.IvyPublishingAwareVariant.isOptional
import org.gradle.api.publish.internal.mapping.DependencyCoordinateResolverFactory.createCoordinateResolvers
import org.gradle.api.publish.internal.mapping.DependencyCoordinateResolverFactory.DependencyResolvers.variantResolver
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport.isTargetingPlatform
import org.gradle.api.publish.internal.validation.VariantWarningCollector.addUnsupported
import org.gradle.api.publish.internal.validation.VariantWarningCollector.addVariantUnsupported
import org.gradle.api.publish.internal.mapping.ResolvedCoordinates.group
import org.gradle.api.publish.internal.mapping.ResolvedCoordinates.name
import org.gradle.api.publish.internal.mapping.ResolvedCoordinates.version
import org.gradle.api.publish.internal.mapping.VariantDependencyResolver.resolveVariantCoordinates
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ExactVersionSelector.Companion.isExact
import org.gradle.api.publish.internal.validation.PublicationWarningsCollector.complete
import org.gradle.api.publish.internal.PublicationInternal.coordinates
import org.gradle.api.publish.internal.PublicationArtifactInternal.shouldBePublished
import org.gradle.api.publish.ivy.IvyPublication.organisation
import org.gradle.api.publish.ivy.IvyPublication.module
import org.gradle.api.publish.ivy.IvyPublication.revision
import org.gradle.api.publish.ivy.IvyExtraInfoSpec.add
import org.gradle.api.internal.attributes.matching.AttributeMatcher.matchMultipleCandidates
import org.gradle.api.publish.internal.versionmapping.DefaultVariantVersionMappingStrategy.setDefaultResolutionConfiguration
import org.gradle.api.internal.attributes.AttributeSchemaServices.schemaFactory
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory.create
import org.gradle.api.internal.attributes.AttributeSchemaServices.getMatcher
import org.gradle.internal.service.ServiceRegistration.addProvider
import org.gradle.internal.service.ServiceRegistration.add
import org.gradle.api.publish.PublishingExtension.publications
import org.gradle.api.publish.PublishingExtension.repositories
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository.setPublication
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository.setRepository
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository.getRepository
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor.setDescriptor
import org.gradle.api.publish.ivy.IvyPublication.descriptor
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor.getDestination
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor.setDestination
import org.gradle.api.publish.tasks.GenerateModuleMetadata.getPublication
import org.gradle.api.publish.tasks.GenerateModuleMetadata.getPublications
import org.gradle.api.publish.tasks.GenerateModuleMetadata.outputFile
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository.hasStandardPattern

