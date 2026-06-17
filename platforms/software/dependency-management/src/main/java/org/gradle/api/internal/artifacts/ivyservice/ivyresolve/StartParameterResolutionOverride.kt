/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.StartParameter
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.verification.DependencyVerificationMode
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ChecksumAndSignatureVerificationOverride
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer.WriteDependencyVerificationFile
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.ExternalResourceCachePolicy
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost
import org.gradle.api.internal.artifacts.verification.exceptions.DependencyVerificationException
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationServiceFactory
import org.gradle.api.internal.component.ArtifactType
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.resources.ResourceException
import org.gradle.internal.Factory
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.ModuleSources
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.resolve.ArtifactResolveException
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ReadableContent
import org.gradle.internal.resource.local.FileResourceListener
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.internal.resource.transfer.ExternalResourceConnector
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.internal.BuildCommencedTimeProvider
import java.io.File

@ServiceScope(Scope.BuildTree::class)
class StartParameterResolutionOverride(private val startParameter: StartParameter, private val gradleDir: File) {
    fun overrideModuleVersionRepository(original: ModuleComponentRepository<ModuleComponentResolveMetadata>): ModuleComponentRepository<ModuleComponentResolveMetadata> {
        if (startParameter.isOffline()) {
            return OfflineModuleComponentRepository(original)
        }
        return original
    }

    fun dependencyVerificationOverride(
        buildOperationExecutor: BuildOperationExecutor,
        checksumService: ChecksumService,
        signatureVerificationServiceFactory: SignatureVerificationServiceFactory,
        documentationRegistry: DocumentationRegistry,
        timeProvider: BuildCommencedTimeProvider,
        gradlePropertiesFactory: Factory<GradleProperties?>,
        fileResourceListener: FileResourceListener
    ): DependencyVerificationOverride {
        val checksums: MutableList<String>? = startParameter.getWriteDependencyVerifications()
        val verificationsFile = DependencyVerificationOverride.dependencyVerificationsFile(gradleDir)
        fileResourceListener.fileObserved(verificationsFile)

        if (!checksums!!.isEmpty() || startParameter.isExportKeys()) {
            return WriteDependencyVerificationFile(
                verificationsFile,
                buildOperationExecutor,
                checksums,
                checksumService,
                signatureVerificationServiceFactory,
                startParameter.isDryRun(),
                startParameter.isExportKeys()
            )
        }

        if (!verificationsFile.exists() ||
            startParameter.dependencyVerificationMode == DependencyVerificationMode.OFF
        ) {
            return DependencyVerificationOverride.NO_VERIFICATION
        }

        try {
            val sessionReportDir = computeReportDirectory(timeProvider)
            return ChecksumAndSignatureVerificationOverride(
                buildOperationExecutor,
                startParameter.getGradleUserHomeDir()!!,
                verificationsFile,
                checksumService,
                signatureVerificationServiceFactory,
                startParameter.dependencyVerificationMode!!,
                documentationRegistry,
                sessionReportDir,
                gradlePropertiesFactory,
                fileResourceListener
            )
        } catch (e: Exception) {
            return FailureVerificationOverride(e)
        }
    }

    private fun computeReportDirectory(timeProvider: BuildCommencedTimeProvider): File {
        // TODO: This is not quite correct: we're using the "root project" build directory
        // but technically speaking, this can be changed _after_ this service is created.
        // There's currently no good way to figure that out.
        val buildDir = File(gradleDir.getParentFile(), "build")
        val reportsDirectory = File(buildDir, "reports")
        val verifyReportsDirectory = File(reportsDirectory, "dependency-verification")
        return File(verifyReportsDirectory, "at-" + timeProvider.getCurrentTime())
    }

    private class OfflineModuleComponentRepository(original: ModuleComponentRepository<ModuleComponentResolveMetadata>) : BaseModuleComponentRepository<ModuleComponentResolveMetadata>(original) {
        private val failedRemoteAccess = FailedRemoteAccess()

        override fun getRemoteAccess(): ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> {
            return failedRemoteAccess
        }
    }

    private class FailedRemoteAccess : ModuleComponentRepositoryAccess<ModuleComponentResolveMetadata> {
        override fun toString(): String {
            return "offline remote"
        }

        override fun listModuleVersions(selector: ModuleComponentSelector, overrideMetadata: ComponentOverrideMetadata, result: BuildableModuleVersionListingResolveResult) {
            result.failed(ModuleVersionResolveException(selector, org.gradle.internal.Factory { String.format("No cached version listing for %s available for offline mode.", selector) }))
        }

        override fun resolveComponentMetaData(
            moduleComponentIdentifier: ModuleComponentIdentifier,
            requestMetaData: ComponentOverrideMetadata,
            result: BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata?>
        ) {
            result.failed(
                ModuleVersionResolveException(
                    moduleComponentIdentifier,
                    org.gradle.internal.Factory { String.format("No cached version of %s available for offline mode.", moduleComponentIdentifier.getDisplayName()) })
            )
        }

        override fun resolveArtifactsWithType(component: ComponentArtifactResolveMetadata, artifactType: ArtifactType, result: BuildableArtifactSetResolveResult) {
            result.failed(ArtifactResolveException(component.getId()!!, "No cached version available for offline mode"))
        }

        override fun resolveArtifact(artifact: ComponentArtifactMetadata, moduleSources: ModuleSources, result: BuildableArtifactFileResolveResult) {
            result.failed(ArtifactResolveException(artifact.getId()!!, "No cached version available for offline mode"))
        }

        override fun estimateMetadataFetchingCost(moduleComponentIdentifier: ModuleComponentIdentifier): MetadataFetchingCost {
            return MetadataFetchingCost.CHEAP
        }
    }

    fun overrideExternalResourceCachePolicy(original: ExternalResourceCachePolicy): ExternalResourceCachePolicy {
        if (startParameter.isOffline()) {
            return org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.ExternalResourceCachePolicy { ageMillis: Long -> false }
        }
        return original
    }

    fun overrideExternalResourceConnector(original: ExternalResourceConnector): ExternalResourceConnector {
        if (startParameter.isOffline()) {
            return OfflineExternalResourceConnector()
        }
        return original
    }

    private class OfflineExternalResourceConnector : ExternalResourceConnector {
        @Throws(ResourceException::class)
        override fun <T> withContent(location: ExternalResourceName, revalidate: Boolean, action: ExternalResource.ContentAndMetadataAction<T?>): T? {
            throw offlineResource(location)
        }

        @Throws(ResourceException::class)
        override fun getMetaData(location: ExternalResourceName, revalidate: Boolean): ExternalResourceMetaData? {
            throw offlineResource(location)
        }

        @Throws(ResourceException::class)
        override fun list(parent: ExternalResourceName): MutableList<String>? {
            throw offlineResource(parent)
        }

        override fun upload(resource: ReadableContent, destination: ExternalResourceName) {
            throw ResourceException(destination.getUri(), String.format("Cannot upload to '%s' in offline mode.", destination.getUri()))
        }

        fun offlineResource(source: ExternalResourceName): ResourceException {
            return ResourceException(source.getUri(), String.format("No cached resource '%s' available for offline mode.", source.getUri()))
        }
    }

    private class FailureVerificationOverride(private val error: Exception) : DependencyVerificationOverride {
        override fun overrideDependencyVerification(original: ModuleComponentRepository<ExternalModuleComponentGraphResolveState>): ModuleComponentRepository<ExternalModuleComponentGraphResolveState>? {
            throw DependencyVerificationException("Dependency verification cannot be performed", error)
        }
    }
}
