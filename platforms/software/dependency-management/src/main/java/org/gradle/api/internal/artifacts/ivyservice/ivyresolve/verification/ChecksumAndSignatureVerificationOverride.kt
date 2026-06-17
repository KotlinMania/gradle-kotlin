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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification

import com.google.common.collect.ImmutableMap
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.Queues
import com.google.common.collect.Sets
import org.gradle.api.Action
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.artifacts.verification.DependencyVerificationMode
import org.gradle.api.component.Artifact
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyVerifyingModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.report.DependencyVerificationReportWriter
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.report.VerificationReport
import org.gradle.api.internal.artifacts.verification.exceptions.DependencyVerificationException
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlReader.readFromXml
import org.gradle.api.internal.artifacts.verification.signatures.BuildTreeDefinedKeys
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationService
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationServiceFactory
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier
import org.gradle.api.internal.artifacts.verification.verifier.VerificationFailure
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.Factory
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.resource.local.FileResourceListener
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.net.URI
import java.util.Deque
import java.util.Map
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function

class ChecksumAndSignatureVerificationOverride(
    private val buildOperationExecutor: BuildOperationExecutor,
    gradleUserHome: File,
    verificationsFile: File,
    private val checksumService: ChecksumService,
    signatureVerificationServiceFactory: SignatureVerificationServiceFactory,
    private val verificationMode: DependencyVerificationMode,
    documentationRegistry: DocumentationRegistry,
    reportsDirectory: File,
    gradlePropertiesFactory: Factory<GradleProperties?>,
    private val fileResourceListener: FileResourceListener
) : DependencyVerificationOverride, ArtifactVerificationOperation, Stoppable {
    private val verifier: DependencyVerifier
    private val signatureVerificationService: SignatureVerificationService
    private val verificationQueries: MutableSet<VerificationQuery> = Sets.newConcurrentHashSet<VerificationQuery>()
    private val verificationEvents: Deque<VerificationEvent> = Queues.newArrayDeque<VerificationEvent>()
    private val closed = AtomicBoolean()
    private val reportWriter: DependencyVerificationReportWriter

    // Must hold lock on `failuresLock` to access `failures` or `hasFatalFailure`
    private val failuresLock = Any()
    private val failures: Multimap<ModuleComponentArtifactIdentifier, RepositoryAwareVerificationFailure> =
        LinkedHashMultimap.create<ModuleComponentArtifactIdentifier, RepositoryAwareVerificationFailure>()
    private var hasFatalFailure = false

    init {
        try {
            this.verifier = readFromXml(
                FileInputStream(observed(verificationsFile))
            )
            this.reportWriter = DependencyVerificationReportWriter(
                gradleUserHome.toPath(),
                documentationRegistry,
                verificationsFile,
                verifier.suggestedWriteFlags,
                reportsDirectory,
                gradlePropertiesFactory,
                verifier.configuration.isUseKeyServers
            )
        } catch (e: FileNotFoundException) {
            throw throwAsUncheckedException(e)
        } catch (e: DependencyVerificationException) {
            throw DependencyVerificationException("Unable to read dependency verification metadata from " + verificationsFile, e.cause)
        }
        val localKeyring = BuildTreeDefinedKeys(verificationsFile.getParentFile(), verifier.configuration.keyringFormat)
        this.signatureVerificationService = signatureVerificationServiceFactory.create(localKeyring, keyServers(), verifier.configuration.isUseKeyServers)!!
    }

    private fun keyServers(): MutableList<URI> {
        return DefaultKeyServers.getOrDefaults(verifier.configuration.keyServers)
    }

    override fun onArtifact(
        kind: ArtifactVerificationOperation.ArtifactKind,
        artifact: ModuleComponentArtifactIdentifier,
        mainFile: File,
        signatureFile: Factory<File?>,
        repositoryName: String,
        repositoryId: String
    ) {
        if (verificationQueries.add(VerificationQuery(artifact, repositoryId))) {
            val event = VerificationEvent(kind, artifact, mainFile, signatureFile, repositoryName)
            synchronized(verificationEvents) {
                verificationEvents.add(event)
            }
        }
    }

    private fun verifyConcurrently() {
        synchronized(verificationEvents) {
            if (verificationEvents.isEmpty()) {
                return
            }
        }
        if (closed.get()) {
            LOGGER.debug("Cannot perform verification of all dependencies because the verification service has been shutdown. Under normal circumstances this shouldn't happen unless a user buildFinished was added in an unexpected way.")
            return
        }
        buildOperationExecutor.runAll<RunnableBuildOperation>(Action { queue: BuildOperationQueue<RunnableBuildOperation>? ->
            var event: VerificationEvent?
            synchronized(verificationEvents) {
                while ((verificationEvents.poll().also { event = it }) != null) {
                    val ve = event
                    queue!!.add(object : RunnableBuildOperation {
                        override fun run(context: BuildOperationContext) {
                            verifier.verify(
                                checksumService,
                                signatureVerificationService,
                                ve.kind,
                                ve.artifact,
                                observed(ve.mainFile),
                                observed(ve.signatureFile.create()!!),
                                org.gradle.api.internal.artifacts.verification.verifier.ArtifactVerificationResultBuilder { f: VerificationFailure? ->
                                    synchronized(failuresLock) {
                                        failures.put(ve.artifact, RepositoryAwareVerificationFailure(f!!, ve.repositoryName))
                                        if (f.isFatal) {
                                            hasFatalFailure = true
                                        }
                                    }
                                })
                        }

                        override fun description(): BuildOperationDescriptor.Builder {
                            return@runAll BuildOperationDescriptor.displayName("Dependency verification")
                                .progressDisplayName("Verifying " + ve.artifact)
                        }
                    })
                }
            }
        })
    }

    override fun overrideDependencyVerification(original: ModuleComponentRepository<ExternalModuleComponentGraphResolveState?>): ModuleComponentRepository<ExternalModuleComponentGraphResolveState?> {
        return DependencyVerifyingModuleComponentRepository(original, this, verifier.configuration.isVerifySignatures)
    }

    override fun artifactsAccessed(displayName: String) {
        verifyConcurrently()
        synchronized(failuresLock) {
            if (hasFatalFailure) {
                // There are fatal failures, but not necessarily on all artifacts so we first filter out
                // the artifacts which only have not fatal errors
                val filtered: MutableMap<ModuleComponentArtifactIdentifier, MutableCollection<RepositoryAwareVerificationFailure>> =
                    failures.asMap().entries.stream().filter { entry: MutableMap.MutableEntry<ModuleComponentArtifactIdentifier?, MutableCollection<RepositoryAwareVerificationFailure>>? ->
                        val value = entry!!.value
                        value.stream().anyMatch { wrapper: RepositoryAwareVerificationFailure? -> wrapper!!.getFailure().isFatal }
                    }.collect(
                        ImmutableMap.toImmutableMap<MutableMap.MutableEntry<ModuleComponentArtifactIdentifier, MutableCollection<RepositoryAwareVerificationFailure>>, ModuleComponentArtifactIdentifier, MutableCollection<RepositoryAwareVerificationFailure>>(
                            Function { Map.Entry.key }, Function { Map.Entry.value })
                    )
                val report = reportWriter.generateReport(displayName, filtered, verifier.configuration.isUseKeyServers)
                val errorMessage = buildConsoleErrorMessage(report)
                if (verificationMode == DependencyVerificationMode.LENIENT) {
                    LOGGER.error(errorMessage)
                    // Clear failures to avoid printing this error multiple times.
                    failures.clear()
                    hasFatalFailure = false
                } else {
                    throw DependencyVerificationException(errorMessage)
                }
            }
        }
    }

    fun buildConsoleErrorMessage(report: VerificationReport): String {
        var errorMessage = report.summary
        val htmlReport = ConsoleRenderer().asClickableFileUrl(report.htmlReport)
        errorMessage += "\n\nOpen this report for more details: " + htmlReport
        return errorMessage
    }

    override fun verifiedArtifact(artifact: ResolvedArtifactResult): ResolvedArtifactResult {
        return object : ResolvedArtifactResult {
            override fun getFile(): File {
                artifactsAccessed(artifact.getVariant().getDisplayName())
                return artifact.getFile()
            }

            override fun getVariant(): ResolvedVariantResult {
                return artifact.getVariant()
            }

            override fun getId(): ComponentArtifactIdentifier {
                return artifact.getId()
            }

            override fun getType(): Class<out Artifact> {
                return artifact.getType()
            }
        }
    }

    private fun observed(file: File): File {
        if (file == null) {
            return file
        }
        fileResourceListener.fileObserved(file)
        return file
    }

    override fun stop() {
        closed.set(true)
        signatureVerificationService.stop()
    }

    private class VerificationQuery(private val artifact: ModuleComponentArtifactIdentifier, private val repositoryId: String) {
        private val hashCode: Int

        init {
            this.hashCode = precomputeHashCode(artifact, repositoryId)
        }

        fun precomputeHashCode(artifact: ModuleComponentArtifactIdentifier, repositoryId: String): Int {
            var hashCode = artifact.getComponentIdentifier().hashCode()
            hashCode = 31 * hashCode + artifact.fileName.hashCode()
            hashCode = 31 * hashCode + repositoryId.hashCode()
            return hashCode
        }

        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as VerificationQuery
            if (hashCode != that.hashCode) {
                return false
            }
            if (artifact.getComponentIdentifier() != that.artifact.getComponentIdentifier()) {
                return false
            }
            if (!artifact.fileName.equals(that.artifact.fileName)) {
                return false
            }
            return repositoryId == that.repositoryId
        }

        override fun hashCode(): Int {
            return hashCode
        }
    }

    private class VerificationEvent(
        private val kind: ArtifactVerificationOperation.ArtifactKind,
        private val artifact: ModuleComponentArtifactIdentifier,
        private val mainFile: File,
        private val signatureFile: Factory<File?>,
        private val repositoryName: String
    )

    companion object {
        private val LOGGER: Logger = getLogger(ChecksumAndSignatureVerificationOverride::class.java)!!
    }
}
