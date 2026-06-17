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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.report

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.RepositoryAwareVerificationFailure
import org.gradle.api.internal.artifacts.verification.verifier.ChecksumVerificationFailure
import org.gradle.api.internal.artifacts.verification.verifier.DeletedArtifact
import org.gradle.api.internal.artifacts.verification.verifier.InvalidSignature
import org.gradle.api.internal.artifacts.verification.verifier.InvalidSignatureFile
import org.gradle.api.internal.artifacts.verification.verifier.MissingChecksums
import org.gradle.api.internal.artifacts.verification.verifier.MissingSignature
import org.gradle.api.internal.artifacts.verification.verifier.OnlyIgnoredKeys
import org.gradle.api.internal.artifacts.verification.verifier.SignatureVerificationFailure
import org.gradle.api.internal.artifacts.verification.verifier.VerificationFailure
import org.gradle.api.internal.properties.GradleProperties
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.Factory
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import java.io.File
import java.nio.file.Path
import java.util.function.Function

class DependencyVerificationReportWriter(
    private val gradleUserHome: Path,
    documentationRegistry: DocumentationRegistry,
    verificationFile: File,
    writeFlags: MutableList<String>,
    htmlReportOutputDirectory: File,
    gradlePropertiesProvider: Factory<GradleProperties?>,
    useKeyServers: Boolean
) {
    private var rendererInitializer: Runnable
    private var summaryRenderer: AbstractTextDependencyVerificationReportRenderer? = null
    private var htmlRenderer: HtmlDependencyVerificationReportRenderer? = null

    init {
        this.rendererInitializer = Runnable {
            this.summaryRenderer = createConsoleRenderer(gradleUserHome, documentationRegistry, gradlePropertiesProvider.create()!!)
            this.htmlRenderer = HtmlDependencyVerificationReportRenderer(documentationRegistry, verificationFile, writeFlags, htmlReportOutputDirectory, useKeyServers)
        }
    }

    private fun createConsoleRenderer(gradleUserHome: Path, documentationRegistry: DocumentationRegistry, gradleProperties: GradleProperties): AbstractTextDependencyVerificationReportRenderer {
        val verboseConsoleReport: Boolean = isVerboseConsoleReport(gradleProperties)
        if (verboseConsoleReport) {
            return TextDependencyVerificationReportRenderer(gradleUserHome, documentationRegistry)
        }
        return SimpleTextDependencyVerificationReportRenderer(gradleUserHome, documentationRegistry)
    }

    fun generateReport(
        displayName: String,
        failuresByArtifact: MutableMap<ModuleComponentArtifactIdentifier, MutableCollection<RepositoryAwareVerificationFailure>>,
        useKeyServers: Boolean
    ): VerificationReport {
        assertInitialized()
        // We need at least one fatal failure: if it's only "warnings" we don't care
        // but of there's a fatal failure AND a warning we want to show both
        doRender(displayName, failuresByArtifact, summaryRenderer!!, useKeyServers)
        doRender(displayName, failuresByArtifact, htmlRenderer!!, useKeyServers)
        val htmlReport = htmlRenderer!!.writeReport()
        return VerificationReport(summaryRenderer!!.render(), htmlReport)
    }

    @Synchronized
    fun assertInitialized() {
        if (rendererInitializer != null) {
            rendererInitializer.run()
            rendererInitializer = null
        }
    }

    fun doRender(
        displayName: String,
        failuresByArtifact: MutableMap<ModuleComponentArtifactIdentifier, MutableCollection<RepositoryAwareVerificationFailure>>,
        renderer: DependencyVerificationReportRenderer,
        useKeyServers: Boolean
    ) {
        val reportState = ReportState()
        if (!useKeyServers) {
            reportState.keyServersAreDisabled()
        }
        renderer.startNewSection(displayName)
        renderer.startArtifactErrors(Runnable {
            // Sorting entries so that error messages are always displayed in a reproducible order
            failuresByArtifact
                .entries
                .stream()
                .sorted(DELETED_LAST.thenComparing(MISSING_LAST).thenComparing(BY_MODULE_ID))
                .forEachOrdered { entry: MutableMap.MutableEntry<ModuleComponentArtifactIdentifier, MutableCollection<RepositoryAwareVerificationFailure>>? ->
                    val key = entry!!.key
                    val failures = entry.value
                    onArtifactFailure(renderer, reportState, key, failures)
                }
        })
        renderer.finish(reportState)
    }

    private fun onArtifactFailure(
        renderer: DependencyVerificationReportRenderer,
        state: ReportState,
        key: ModuleComponentArtifactIdentifier,
        failures: MutableCollection<RepositoryAwareVerificationFailure>
    ) {
        failures.stream()
            .map<VerificationFailure>(RepositoryAwareVerificationFailure::getFailure)
            .map<String> { f: VerificationFailure? -> this.extractFailedFilePaths(f!!) }
            .forEach { file: String? -> state.addAffectedFile(file!!) }
        renderer.startNewArtifact(key, Runnable {
            if (failures.size == 1) {
                val firstFailure = failures.iterator().next()
                explainSingleFailure(renderer, state, firstFailure)
            } else {
                explainMultiFailure(renderer, state, failures)
            }
        })
    }

    private fun extractFailedFilePaths(f: VerificationFailure): String {
        val shortenPath = shortenPath(f.filePath)
        val signatureFile: File = f.signatureFile
        if (signatureFile != null) {
            return shortenPath + " (signature: " + shortenPath(signatureFile) + ")"
        }
        return shortenPath
    }

    // Shortens the path for display the user
    private fun shortenPath(file: File): String {
        val path = file.toPath()
        try {
            val relativize = gradleUserHome.relativize(path)
            return "GRADLE_USER_HOME" + File.separator + relativize
        } catch (e: IllegalArgumentException) {
            return file.getAbsolutePath()
        }
    }

    private fun explainMultiFailure(renderer: DependencyVerificationReportRenderer, state: ReportState, failures: MutableCollection<RepositoryAwareVerificationFailure>) {
        renderer.reportAsMultipleErrors(Runnable {
            for (failure in failures) {
                explainSingleFailure(renderer, state, failure)
            }
        })
    }

    private fun explainSingleFailure(renderer: DependencyVerificationReportRenderer, state: ReportState, wrapper: RepositoryAwareVerificationFailure) {
        val failure = wrapper.failure
        if (failure is MissingChecksums) {
            state.hasMissing()
        } else if (failure is SignatureVerificationFailure) {
            state.failedSignatures()
            if (failure.errors.values.stream().map<SignatureVerificationFailure.FailureKind>(SignatureVerificationFailure.SignatureError::kind)
                    .noneMatch { kind: SignatureVerificationFailure.FailureKind? -> kind == SignatureVerificationFailure.FailureKind.PASSED_NOT_TRUSTED }
            ) {
                state.maybeCompromised()
            } else {
                state.hasUntrustedKeys()
            }
        } else if (failure is InvalidSignature) {
            state.failedSignatures()
            state.maybeCompromised()
        } else if (failure is InvalidSignatureFile) {
            state.failedSignatures()
            state.maybeCompromised()
        } else if (failure is DeletedArtifact || failure is ChecksumVerificationFailure || failure is OnlyIgnoredKeys || failure is MissingSignature) {
            state.maybeCompromised()
        } else {
            // should never happen, just to make sure we don't miss any new failure type
            throw IllegalArgumentException("Unknown failure type: " + failure.javaClass)
        }
        renderer.reportFailure(wrapper)
    }

    companion object {
        private val LOGGER: Logger = getLogger(DependencyVerificationReportWriter::class.java)!!

        private val DELETED_LAST: Comparator<MutableMap.MutableEntry<ModuleComponentArtifactIdentifier, MutableCollection<RepositoryAwareVerificationFailure>>> =
            Comparator.comparing<MutableMap.MutableEntry<ModuleComponentArtifactIdentifier, MutableCollection<RepositoryAwareVerificationFailure>>, Int>(
                Function { e: MutableMap.MutableEntry<ModuleComponentArtifactIdentifier, MutableCollection<RepositoryAwareVerificationFailure>> ->
                    if (e.value.stream().anyMatch { f: RepositoryAwareVerificationFailure? -> f!!.failure is DeletedArtifact }) 1 else 0
                })
        private val MISSING_LAST: Comparator<MutableMap.MutableEntry<ModuleComponentArtifactIdentifier, MutableCollection<RepositoryAwareVerificationFailure>>> =
            Comparator.comparing<MutableMap.MutableEntry<ModuleComponentArtifactIdentifier, MutableCollection<RepositoryAwareVerificationFailure>>, Int>(
                Function { e: MutableMap.MutableEntry<ModuleComponentArtifactIdentifier, MutableCollection<RepositoryAwareVerificationFailure>> ->
                    if (e.value.stream().anyMatch { f: RepositoryAwareVerificationFailure? -> f!!.failure is MissingChecksums }) 1 else 0
                })
        private val BY_MODULE_ID: Comparator<MutableMap.MutableEntry<ModuleComponentArtifactIdentifier, MutableCollection<RepositoryAwareVerificationFailure>>> =
            Comparator.comparing<MutableMap.MutableEntry<ModuleComponentArtifactIdentifier, MutableCollection<RepositoryAwareVerificationFailure>>, String>(
                Function { e: MutableMap.MutableEntry<ModuleComponentArtifactIdentifier, MutableCollection<RepositoryAwareVerificationFailure>> -> e.key.getDisplayName() })
        const val VERBOSE_CONSOLE: String = "org.gradle.dependency.verification.console"
        const val VERBOSE_VALUE: String = "verbose"

        private fun isVerboseConsoleReport(gradleProperties: GradleProperties): Boolean {
            try {
                val param = gradleProperties.find(VERBOSE_CONSOLE)
                return VERBOSE_VALUE == param
            } catch (e: IllegalStateException) {
                // Gradle properties are not loaded yet, which can happen in init scripts
                // let's return a default value
                LOGGER.warn("Gradle properties are not loaded yet, any customization to dependency verification will be ignored until the main build script is loaded.")
                return false
            }
        }
    }
}
