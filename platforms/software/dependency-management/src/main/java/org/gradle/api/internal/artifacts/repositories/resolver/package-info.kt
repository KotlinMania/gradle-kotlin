/*
 * Copyright 2017 the original author or authors.
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
 * Implementation classes of API declaring and using artifacts and artifact dependencies.
 */
package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.internal.resolve.result.ResourceAwareResolveResult.attempted
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult.listed
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor.getResource
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.sources
import org.gradle.internal.component.model.ComponentOverrideMetadata.getArtifact
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.id
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.isChanging
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier.getComponentIdentifier
import org.gradle.internal.serialize.Encoder.writeString
import org.gradle.internal.serialize.Encoder.writeBinary
import org.gradle.internal.serialize.Decoder.readString
import org.gradle.internal.serialize.Decoder.readBinary
import org.gradle.api.internal.filestore.ArtifactIdentifierFileStore.whereIs
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.Companion.newId
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory.module
import org.gradle.internal.component.external.model.PreferJavaRuntimeVariant.schema
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory.moduleWithVersion
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.isMissing
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult.redirectToGradleMetadata
import org.gradle.internal.component.model.MutableModuleSources.add
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata.getId
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.moduleVersionId
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata.getId
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.mutableVariants
import org.gradle.internal.component.external.model.MutableComponentVariant.files
import org.gradle.internal.component.external.model.MutableComponentVariant.removeFile
import org.gradle.internal.component.external.model.MutableComponentVariant.addFile
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult.shouldUseGradleMetatada
import org.gradle.internal.component.external.model.ivy.IvyComponentArtifactResolveMetadata.getConfigurationArtifacts
import org.gradle.internal.resolve.result.BuildableTypedResolveResult.resolved
import org.gradle.internal.component.model.MutableModuleSources.Companion.of
import org.gradle.internal.component.model.ModuleSources.withSource
import org.gradle.internal.component.model.ComponentArtifactMetadata.getComponentId
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.variantMetadataRules
import org.gradle.internal.component.external.model.VariantMetadataRules.addDependencyAction
import org.gradle.internal.component.external.model.VariantMetadataRules.addDependencyConstraintAction
import org.gradle.internal.component.external.model.VariantMetadataRules.addCapabilitiesAction
import org.gradle.internal.component.external.model.VariantMetadataRules.addVariantFilesAction
import org.gradle.internal.component.external.model.VariantMetadataRules.addAttributesAction
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.attributesFactory
import org.gradle.internal.component.external.model.VariantMetadataRules.getAttributes
import org.gradle.internal.component.model.ComponentArtifactMetadata.getName
import org.gradle.internal.resolve.result.ResolveResult.hasResult
import org.gradle.util.internal.CollectionUtils.filter
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult.resolved
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.asImmutable
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult.missing
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.isComponentMetadataRuleCachingEnabled
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata.getId
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata.getSources
import org.gradle.util.internal.CollectionUtils.collect
import org.gradle.internal.resolve.result.ErroringResolveResult.failed
import org.gradle.internal.component.model.ComponentArtifactMetadata.isOptionalArtifact
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult.notFound
import org.gradle.internal.component.model.ComponentArtifactMetadata.getId
import org.gradle.internal.component.model.ComponentArtifactMetadata.getAlternativeArtifact
import org.gradle.internal.resolve.result.DefaultResourceAwareResolveResult.getAttempted
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.status
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.statusScheme
import org.gradle.internal.component.external.model.VariantMetadataRules.addVariant
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.belongsTo
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.attributes
import org.gradle.internal.component.external.model.ModuleDependencyMetadata.withEndorseStrictVersions
import org.gradle.internal.component.external.model.GradleDependencyMetadata.dependencyArtifact
import org.gradle.internal.component.external.model.ModuleDependencyMetadata.selector
import org.gradle.internal.component.external.model.ModuleDependencyMetadata.withRequestedVersion
import org.gradle.internal.component.external.model.ModuleDependencyMetadata.withReason
import org.gradle.internal.component.model.DependencyMetadata.withTarget
import org.gradle.internal.component.model.ForcingDependencyMetadata.forced
import org.gradle.internal.resolve.caching.ImplicitInputRecorder.register
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.hashCode
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.equals
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.getGroup
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.getModule
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.getVersion
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.getModuleIdentifier
import org.gradle.internal.component.external.model.UrlBackedArtifactMetadata.getId
import org.gradle.internal.component.external.model.UrlBackedArtifactMetadata.getComponentId
import org.gradle.internal.component.external.model.UrlBackedArtifactMetadata.relativeUrl
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver.resolve
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver.resolve
import org.gradle.internal.resolve.resolver.ArtifactResolver.resolveArtifactsWithType
import org.gradle.internal.resolve.resolver.ArtifactResolver.resolveArtifact
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult.getResult
import org.gradle.internal.component.model.MutableModuleSources.withSources
import org.gradle.internal.exceptions.DefaultMultiCauseException.getCauses
import org.gradle.internal.authentication.AuthenticationInternal.requiresCredentials
import org.gradle.internal.authentication.AuthenticationInternal.supports
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.authentication.AuthenticationSchemeRegistry.getRegisteredSchemes
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.deprecation.DeprecationMessageBuilder.willBeRemovedInGradle10
import org.gradle.internal.deprecation.Documentation.AbstractBuilder.withUpgradeGuideSection
import org.gradle.internal.deprecation.DeprecationMessageBuilder.WithDocumentation.nagUser
import org.gradle.util.internal.CollectionUtils.addAll
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.artifact
import org.gradle.internal.authentication.AuthenticationInternal.addHost
import org.gradle.internal.Cast.cast

