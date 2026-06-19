/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.jvm.toolchain.internal

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import org.gradle.api.GradleException
import org.gradle.api.internal.file.FileFactory
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.internal.deprecation.DocumentedFailure.builder
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.jvm.inspection.JavaInstallationCapability
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmInstallationMetadataComparator
import org.gradle.internal.jvm.inspection.JvmMetadataDetector
import org.gradle.internal.jvm.inspection.JvmToolchainMetadata
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.internal.InstallationLocation.Companion.autoDetected
import org.gradle.jvm.toolchain.internal.InstallationLocation.Companion.autoProvisioned
import org.gradle.jvm.toolchain.internal.InstallationLocation.Companion.userDefined
import org.gradle.jvm.toolchain.internal.install.JavaToolchainProvisioningService
import org.gradle.jvm.toolchain.internal.install.JvmInstallationMetadataMatcher
import java.io.File
import java.util.Objects
import java.util.Optional
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import javax.inject.Inject

@ServiceScope(Scope.Build::class)
class JavaToolchainQueryService @VisibleForTesting internal constructor(
    detector: JvmMetadataDetector,
    fileFactory: FileFactory,
    provisioningService: JavaToolchainProvisioningService,
    registry: JavaInstallationRegistry,
    fallbackToolchainSpec: JavaToolchainSpec?,
    currentJavaHome: File
) {
    private class ToolchainLookupKey(specKey: JavaToolchainSpecInternal.Key?, requiredCapabilities: MutableSet<JavaInstallationCapability>) {
        private val specKey: JavaToolchainSpecInternal.Key?
        private val requiredCapabilities: Set<JavaInstallationCapability>

        init {
            this.specKey = specKey
            this.requiredCapabilities = Sets.immutableEnumSet<JavaInstallationCapability>(requiredCapabilities)
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as ToolchainLookupKey
            return specKey == that.specKey && requiredCapabilities == that.requiredCapabilities
        }

        override fun hashCode(): Int {
            return Objects.hash(specKey, requiredCapabilities)
        }

        override fun toString(): String {
            return "ToolchainLookupKey{" +
                    "specKey=" + specKey +
                    ", requiredCapabilities=" + requiredCapabilities +
                    '}'
        }
    }

    private val fileFactory: FileFactory
    private val detector: JvmMetadataDetector
    private val installService: JavaToolchainProvisioningService

    // Map values are either `JavaToolchain` or `Exception`
    private val matchingToolchains: ConcurrentMap<ToolchainLookupKey?, Any?>
    private val fallbackToolchainSpec: JavaToolchainSpec?
    private val currentJavaHome: File
    private val registry: JavaInstallationRegistry

    @Inject
    constructor(
        detector: JvmMetadataDetector,
        fileFactory: FileFactory,
        provisioningService: JavaToolchainProvisioningService,
        registry: JavaInstallationRegistry,
        fallbackToolchainSpec: CurrentJvmToolchainSpec?
    ) : this(detector, fileFactory, provisioningService, registry, fallbackToolchainSpec, Jvm.current().getJavaHome())

    init {
        this.detector = detector
        this.fileFactory = fileFactory
        this.installService = provisioningService
        this.matchingToolchains = ConcurrentHashMap<ToolchainLookupKey?, Any?>()
        this.fallbackToolchainSpec = fallbackToolchainSpec
        this.currentJavaHome = currentJavaHome
        this.registry = registry
    }

    @JvmOverloads
    fun findMatchingToolchain(
        filter: JavaToolchainSpec?,
        requiredCapabilities: MutableSet<JavaInstallationCapability> = mutableSetOf<JavaInstallationCapability>()
    ): ProviderInternal<JavaToolchain> {
        val filterInternal = Objects.requireNonNull<JavaToolchainSpec?>(filter) as JavaToolchainSpecInternal
        return DefaultProvider<JavaToolchain>(Callable { resolveToolchain(filterInternal, requiredCapabilities) })
    }

    @Throws(Exception::class)
    private fun resolveToolchain(requestedSpec: JavaToolchainSpecInternal, requiredCapabilities: MutableSet<JavaInstallationCapability>): JavaToolchain {
        requestedSpec.finalizeProperties()

        if (!requestedSpec.isValid) {
            throw builder()
                .withSummary("Using toolchain specifications without setting a language version is not supported.")
                .withAdvice("Consider configuring the language version.")
                .withUpgradeGuideSection(7, "invalid_toolchain_specification_deprecation")!!
                .build()
        }

        val useFallback = !requestedSpec.isConfigured
        val actualSpec = (if (useFallback) fallbackToolchainSpec else requestedSpec)!!
        // We can't use the key of the fallback toolchain spec, because it is a spec that can match configured requests as well
        val actualSpecKey = if (useFallback) FALLBACK_TOOLCHAIN_KEY else requestedSpec.toKey()
        val actualKey = ToolchainLookupKey(actualSpecKey, requiredCapabilities)

        // TODO: We could optimize here by reusing results which have capabilities that are supersets of the required capabilities
        // Currently this issues a new query for each required capability set, which usually means at least 2 queries for a normal Java project (compiler + tests or application)
        val resolutionResult = matchingToolchains.computeIfAbsent(actualKey) { key: ToolchainLookupKey? ->
            try {
                return@computeIfAbsent query(actualSpec, transformCapabilities(actualSpec, requiredCapabilities)!!, useFallback)
            } catch (e: Exception) {
                return@computeIfAbsent e
            }
        }

        if (resolutionResult is Exception) {
            throw resolutionResult
        } else {
            return resolutionResult as JavaToolchain
        }
    }

    private fun transformCapabilities(actualSpec: JavaToolchainSpec, requiredCapabilities: MutableSet<JavaInstallationCapability>): Set<JavaInstallationCapability> {
        if (actualSpec.nativeImageCapable.getOrElse(false)) {
            val capabilityBuilder = ImmutableSet.Builder<JavaInstallationCapability>()
            capabilityBuilder.addAll(requiredCapabilities)
            capabilityBuilder.add(JavaInstallationCapability.NATIVE_IMAGE)
            return capabilityBuilder.build()
        } else {
            return requiredCapabilities
        }
    }

    private fun query(spec: JavaToolchainSpec, requiredCapabilities: Set<JavaInstallationCapability>, isFallback: Boolean): JavaToolchain {
        if (spec is CurrentJvmToolchainSpec) {
            return asToolchainOrThrow(autoDetected(currentJavaHome, "current JVM"), spec, requiredCapabilities, isFallback)
        }

        if (spec is SpecificInstallationToolchainSpec) {
            return asToolchainOrThrow(userDefined(spec.javaHome!!, "specific installation"), spec, requiredCapabilities, false)
        }

        if (spec is SpecificExecutableToolchainSpec) {
            return asToolchainOrThrow(userDefined(spec.javaHome!!, "specific executable"), spec, requiredCapabilities, false)
        }

        return findInstalledToolchain(spec, requiredCapabilities).orElseGet(Supplier { downloadToolchain(spec, requiredCapabilities) })
    }

    private fun findInstalledToolchain(spec: JavaToolchainSpec, requiredCapabilities: Set<JavaInstallationCapability>): Optional<JavaToolchain> {
        val matcher = JvmInstallationMetadataMatcher(spec, requiredCapabilities)

        return registry.toolchains()!!.stream()
            .filter { result: JvmToolchainMetadata? -> result!!.metadata!!.isValidInstallation }
            .filter { result: JvmToolchainMetadata? -> matcher.test(result!!.metadata!!) }
            .min(Comparator.comparing<JvmToolchainMetadata, JvmInstallationMetadata>(Function { result: JvmToolchainMetadata -> result.metadata!! }, JvmInstallationMetadataComparator(currentJavaHome)))
            .map<JavaToolchain>(Function { result: JvmToolchainMetadata? ->
                warnIfAutoProvisionedToolchainUsedWithoutRepositoryDefinitions(result!!)
                JavaToolchain(result.metadata!!, fileFactory, JavaToolchainInput(spec), false)
            })
    }

    private fun warnIfAutoProvisionedToolchainUsedWithoutRepositoryDefinitions(candidate: JvmToolchainMetadata) {
        val javaHome = candidate.location
        val autoDetectedToolchain = javaHome!!.isAutoProvisioned
        if (autoDetectedToolchain && installService.isAutoDownloadEnabled && !installService.hasConfiguredToolchainRepositories()) {
            org.gradle.internal.deprecation.DeprecationLogger.deprecateBehaviour(
                java.lang.String.format(
                    "Using toolchain '%s' installed via auto-provisioning without toolchain repositories.",
                    candidate.metadata!!.displayName
                )
            )
                .withAdvice("Add toolchain repositories to this build.")!!
                .withContext("Builds may fail when this toolchain is not available in other environments.")!!
                .willBecomeAnErrorInGradle10()
                .withUserManual("toolchains", "sub:download_repositories")!!
                .nagUser()
        }
    }

    private fun downloadToolchain(spec: JavaToolchainSpec, requiredCapabilities: Set<JavaInstallationCapability>): JavaToolchain {
        val installation = installService.tryInstall(spec)
        val downloadedInstallation = autoProvisioned(installation!!, "provisioned toolchain")
        val downloadedToolchain = asToolchainOrThrow(downloadedInstallation, spec, requiredCapabilities, false)
        registry.addInstallation(downloadedInstallation)
        return downloadedToolchain
    }

    private fun asToolchainOrThrow(javaHome: InstallationLocation, spec: JavaToolchainSpec, requiredCapabilities: Set<JavaInstallationCapability>, isFallback: Boolean): JavaToolchain {
        val metadata = detector.getMetadata(javaHome)

        val cannotProbeSpecificExecutable = (spec is SpecificExecutableToolchainSpec) && !metadata!!.isValidInstallation

        if (!metadata!!.isValidInstallation && !cannotProbeSpecificExecutable) {
            throw GradleException("Toolchain installation '" + javaHome.location + "' could not be probed: " + metadata.errorMessage, metadata.errorCause)
        }
        if (!metadata.capabilities!!.containsAll(requiredCapabilities)) {
            throw GradleException("Toolchain installation '" + javaHome.location + "' does not provide the required capabilities: " + requiredCapabilities)
        }
        if (cannotProbeSpecificExecutable) {
            return SpecificExecutableJavaToolchain(metadata, fileFactory, JavaToolchainInput(spec), isFallback, spec.javaExecutable)
        } else {
            return JavaToolchain(metadata, fileFactory, JavaToolchainInput(spec), isFallback)
        }
    }

    companion object {
        // A key that matches only the fallback toolchain
        private val FALLBACK_TOOLCHAIN_KEY: JavaToolchainSpecInternal.Key = object : JavaToolchainSpecInternal.Key {
            override fun toString(): kotlin.String {
                return "FallbackToolchainSpecKey"
            }
        }
    }
}
