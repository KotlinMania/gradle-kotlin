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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import com.google.common.io.Files
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ArtifactVerificationOperation
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMetadataFileSource
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost
import org.gradle.api.internal.component.ArtifactType
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.Factory
import org.gradle.internal.action.InstantiatingAction
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.DefaultIvyArtifactName.Companion.forFileName
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.ModuleDescriptorArtifactMetadata
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableArtifactFileResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult
import java.io.File
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Function

class DependencyVerifyingModuleComponentRepository(
    private val delegate: ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>,
    private val operation: ArtifactVerificationOperation,
    verifySignatures: Boolean
) : ModuleComponentRepository<ExternalModuleComponentGraphResolveState?> {
    private val localAccess: ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?>
    private val remoteAccess: ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?>

    init {
        this.localAccess = DependencyVerifyingModuleComponentRepository.VerifyingModuleComponentRepositoryAccess(delegate.getLocalAccess(), verifySignatures)
        this.remoteAccess = DependencyVerifyingModuleComponentRepository.VerifyingModuleComponentRepositoryAccess(delegate.getRemoteAccess(), verifySignatures)
    }

    override fun getId(): String? {
        return delegate.getId()
    }

    override fun getName(): String? {
        return delegate.getName()
    }

    override fun getLocalAccess(): ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?> {
        return localAccess
    }

    override fun getRemoteAccess(): ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?> {
        return remoteAccess
    }

    override fun getArtifactCache(): MutableMap<ComponentArtifactIdentifier?, ResolvableArtifact?>? {
        return delegate.getArtifactCache()
    }

    override fun getComponentMetadataSupplier(): InstantiatingAction<ComponentMetadataSupplierDetails?>? {
        return delegate.getComponentMetadataSupplier()
    }

    override fun isContinueOnConnectionFailure(): Boolean {
        return delegate.isContinueOnConnectionFailure()
    }

    override fun isRepositoryDisabled(): Boolean {
        return delegate.isRepositoryDisabled()
    }

    private inner class VerifyingModuleComponentRepositoryAccess(
        private val delegate: ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?>,
        private val verifySignatures: Boolean
    ) : ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState?> {
        override fun listModuleVersions(selector: ModuleComponentSelector?, overrideMetadata: ComponentOverrideMetadata?, result: BuildableModuleVersionListingResolveResult?) {
            delegate.listModuleVersions(selector, overrideMetadata, result)
        }

        fun hasUsableResult(result: BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?>): Boolean {
            return result.hasResult() && result.state === BuildableModuleComponentMetaDataResolveResult.State.Resolved
        }

        override fun resolveComponentMetaData(
            moduleComponentIdentifier: ModuleComponentIdentifier?,
            requestMetaData: ComponentOverrideMetadata,
            result: BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?>?
        ) {
            // For metadata, because the local file can be deleted we have to proceed in two steps
            // First resolve with a tmp result, and if it's found and that the file is still present
            // we can perform verification. If it's missing, then we do nothing so that it's downloaded
            // and verified later.
            val tmp: BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?> =
                DefaultBuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?>()
            delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, tmp)
            val ignore = AtomicBoolean()
            if (hasUsableResult(tmp)) {
                val sources: ModuleSources = tmp.metaData!!.prepareForArtifactResolution()!!.getArtifactMetadata()!!.getSources()!!
                sources.withSources<DefaultMetadataFileSource?>(DefaultMetadataFileSource::class.java, Consumer { metadataFileSource: DefaultMetadataFileSource? ->
                    val artifact = metadataFileSource!!.getArtifactId()
                    if (isExternalArtifactId(artifact)) {
                        sources.withSource<ModuleDescriptorHashModuleSource?, Any?>(ModuleDescriptorHashModuleSource::class.java, Function { hashSource: Optional<ModuleDescriptorHashModuleSource?>? ->
                            if (hashSource!!.isPresent()) {
                                val changingModule = requestMetaData.isChanging() || hashSource.get().isChangingModule
                                if (!changingModule) {
                                    val artifactFile = metadataFileSource.getArtifactFile()
                                    if (artifactFile != null && artifactFile.exists()) {
                                        // it's possible that the file is null if it has been removed from the cache for example
                                        val signatureFileFactory: Factory<File?> = org.gradle.internal.Factory { maybeFetchComponentMetadataSignatureFile(sources, artifact) }
                                        operation.onArtifact(ArtifactVerificationOperation.ArtifactKind.METADATA, artifact, artifactFile, signatureFileFactory, getName()!!, getId()!!)
                                    } else {
                                        ignore.set(true)
                                    }
                                }
                            }
                            null
                        })
                    }
                })
            }

            if (!ignore.get()) {
                delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result)
            }
        }

        fun maybeFetchComponentMetadataSignatureFile(moduleSources: ModuleSources?, artifact: ModuleComponentArtifactIdentifier): File? {
            val signatureArtifactId: ModuleComponentArtifactIdentifier?
            if (artifact is DefaultModuleComponentArtifactIdentifier) {
                signatureArtifactId = createSignatureArtifactIdFromIvyArtifactName(artifact.getComponentIdentifier(), artifact.name)
            } else {
                signatureArtifactId = ModuleComponentFileArtifactIdentifier(artifact.getComponentIdentifier(), artifact.fileName + ".asc")
            }
            val signatureArtifactMetadata: SignatureArtifactMetadata = VerifyingModuleComponentRepositoryAccess.SignatureArtifactMetadata(signatureArtifactId)
            return maybeFetchSignatureFile(moduleSources, signatureArtifactMetadata)
        }

        fun maybeFetchArtifactSignatureFile(moduleSources: ModuleSources?, artifact: ModuleComponentArtifactIdentifier, ivyArtifactName: IvyArtifactName): File? {
            val signatureArtifactId = createSignatureArtifactIdFromIvyArtifactName(artifact.getComponentIdentifier(), ivyArtifactName)
            val signatureArtifactMetadata: SignatureArtifactMetadata = VerifyingModuleComponentRepositoryAccess.SignatureArtifactMetadata(signatureArtifactId)
            return maybeFetchSignatureFile(moduleSources, signatureArtifactMetadata)
        }

        fun createSignatureArtifactIdFromIvyArtifactName(moduleComponentIdentifier: ModuleComponentIdentifier, ivyArtifactName: IvyArtifactName): ModuleComponentArtifactIdentifier {
            val extension = if (ivyArtifactName.extension != null) ivyArtifactName.extension else ivyArtifactName.type
            return DefaultModuleComponentArtifactIdentifier(moduleComponentIdentifier, ivyArtifactName.name!!, "asc", extension + ".asc", ivyArtifactName.classifier)
        }

        fun maybeFetchSignatureFile(moduleSources: ModuleSources?, signatureArtifactMetadata: SignatureArtifactMetadata?): File? {
            if (!verifySignatures) {
                return null
            }
            val signatureResult = SignatureFileDefaultBuildableArtifactResolveResult()
            getLocalAccess().resolveArtifact(signatureArtifactMetadata, moduleSources, signatureResult)
            if (signatureResult.hasResult()) {
                if (signatureResult.isSuccessful) {
                    return signatureResult.getResult()
                }
                return null
            } else {
                getRemoteAccess().resolveArtifact(signatureArtifactMetadata, moduleSources, signatureResult)
            }
            if (signatureResult.hasResult() && signatureResult.isSuccessful) {
                return signatureResult.getResult()
            }
            return null
        }

        override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata?, artifactType: ArtifactType?, result: BuildableArtifactSetResolveResult?) {
            delegate.resolveArtifactsWithType(component, artifactType, result)
        }

        override fun resolveArtifact(artifact: ComponentArtifactMetadata, moduleSources: ModuleSources, result: BuildableArtifactFileResolveResult) {
            delegate.resolveArtifact(artifact, moduleSources, result)
            if (result.hasResult() && result.isSuccessful) {
                val id = artifact.getId()
                if (isExternalArtifactId(id) && isNotChanging(moduleSources)) {
                    val mcai = id as ModuleComponentArtifactIdentifier
                    val artifactKind = determineArtifactKind(artifact)
                    if (result !is SignatureFileDefaultBuildableArtifactResolveResult) {
                        // signature files are fetched using resolveArtifact, but are checked alongside the main artifact
                        val signatureFileFactory: Factory<File?> = org.gradle.internal.Factory { maybeFetchArtifactSignatureFile(moduleSources, mcai, artifact.getName()!!) }
                        operation.onArtifact(artifactKind, mcai, result.getResult()!!, signatureFileFactory, getName()!!, getId()!!)
                    }
                }
            }
        }

        fun determineArtifactKind(artifact: ComponentArtifactMetadata?): ArtifactVerificationOperation.ArtifactKind {
            var artifactKind = ArtifactVerificationOperation.ArtifactKind.REGULAR
            if (artifact is ModuleDescriptorArtifactMetadata) {
                artifactKind = ArtifactVerificationOperation.ArtifactKind.METADATA
            }
            return artifactKind
        }

        fun isNotChanging(moduleSources: ModuleSources): Boolean {
            return moduleSources.withSource<ModuleDescriptorHashModuleSource?, Boolean?>(
                org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource::class.java,
                java.util.function.Function { source: java.util.Optional<org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource?>? ->
                    source.map<kotlin.Boolean?>(java.util.function.Function { cachingModuleSource: org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource? -> !cachingModuleSource.isChangingModule })
                        .orElse(true)
                })!!
        }

        fun isExternalArtifactId(id: ComponentArtifactIdentifier?): Boolean {
            return id is ModuleComponentArtifactIdentifier
        }

        override fun estimateMetadataFetchingCost(moduleComponentIdentifier: ModuleComponentIdentifier?): MetadataFetchingCost? {
            return delegate.estimateMetadataFetchingCost(moduleComponentIdentifier)
        }

        private inner class SignatureArtifactMetadata(private val artifactIdentifier: ModuleComponentArtifactIdentifier) : ModuleComponentArtifactMetadata {
            private val moduleComponentIdentifier: ModuleComponentIdentifier

            init {
                this.moduleComponentIdentifier = artifactIdentifier.getComponentIdentifier()
            }

            override fun getId(): ModuleComponentArtifactIdentifier {
                return artifactIdentifier
            }

            override fun getComponentId(): ComponentIdentifier {
                return moduleComponentIdentifier
            }

            override fun getName(): IvyArtifactName? {
                if (artifactIdentifier is DefaultModuleComponentArtifactIdentifier) {
                    return artifactIdentifier.name
                }
                // This is a bit hackish but the mapping from file names to ivy artifact names is completely broken
                var fileName = artifactIdentifier.fileName!!.replace("-" + artifactIdentifier.getComponentIdentifier().getVersion(), "")
                fileName = Files.getNameWithoutExtension(fileName) // removes the .asc
                val base: IvyArtifactName = forFileName(fileName, null)
                return DefaultIvyArtifactName(
                    base.name!!,
                    "asc",
                    base.extension + ".asc"
                )
            }

            override fun getBuildDependencies(): TaskDependency {
                return DefaultTaskDependency()
            }
        }
    }

    private class SignatureFileDefaultBuildableArtifactResolveResult : DefaultBuildableArtifactFileResolveResult()
}
