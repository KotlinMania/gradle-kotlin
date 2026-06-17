/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.internal.jvm.inspection

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.GradleException
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier
import org.gradle.jvm.toolchain.internal.CurrentInstallationSupplier
import org.gradle.jvm.toolchain.internal.EnvironmentVariableJavaHomeInstallationSupplier
import org.gradle.jvm.toolchain.internal.EnvironmentVariableListInstallationSupplier
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.gradle.jvm.toolchain.internal.InstallationSupplier
import org.gradle.jvm.toolchain.internal.JdkCacheDirectory
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration
import org.jspecify.annotations.NullMarked
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Collectors
import javax.inject.Inject

@NullMarked
class DefaultJavaInstallationRegistry @VisibleForTesting protected constructor(
    toolchainConfiguration: ToolchainConfiguration,
    suppliers: MutableList<InstallationSupplier>,
    optionalSuppliers: MutableList<InstallationSupplier>,
    private val metadataDetector: JvmMetadataDetector,
    private val logger: Logger,
    private val buildOperationRunner: BuildOperationRunner?,
    os: OperatingSystem,
    progressLoggerFactory: ProgressLoggerFactory?,
    problemReporter: JvmInstallationProblemReporter
) : JavaInstallationRegistry {
    private val installations: Installations
    private val os: OperatingSystem
    private val progressLoggerFactory: ProgressLoggerFactory?
    private val problemReporter: JvmInstallationProblemReporter

    @Inject
    constructor(
        toolchainConfiguration: ToolchainConfiguration,
        suppliers: MutableList<InstallationSupplier>,
        metadataDetector: JvmMetadataDetector,
        buildOperationRunner: BuildOperationRunner?,
        os: OperatingSystem,
        progressLoggerFactory: ProgressLoggerFactory?,
        fileResolver: FileResolver,
        jdkCacheDirectory: JdkCacheDirectory,
        problemReporter: JvmInstallationProblemReporter
    ) : this(
        toolchainConfiguration,
        builtInSuppliers(toolchainConfiguration, fileResolver, jdkCacheDirectory),
        suppliers,
        metadataDetector,
        getLogger(JavaInstallationRegistry::class.java)!!,
        buildOperationRunner,
        os,
        progressLoggerFactory,
        problemReporter
    )

    init {
        val allSuppliers: MutableList<InstallationSupplier> = ArrayList<InstallationSupplier>(suppliers)
        if (toolchainConfiguration.isAutoDetectEnabled()) {
            allSuppliers.addAll(optionalSuppliers)
        }
        this.installations = Installations(Supplier { maybeCollectInBuildOperation(allSuppliers) })
        this.os = os
        this.progressLoggerFactory = progressLoggerFactory
        this.problemReporter = problemReporter
    }

    private fun maybeCollectInBuildOperation(suppliers: MutableList<InstallationSupplier>): MutableSet<InstallationLocation> {
        if (buildOperationRunner == null) {
            return collectInstallations(suppliers)
        }
        return buildOperationRunner.call<MutableSet<InstallationLocation>>(ToolchainDetectionBuildOperation(Callable { collectInstallations(suppliers) }))
    }

    @VisibleForTesting
    protected fun listInstallations(): MutableSet<InstallationLocation> {
        return installations.get()
    }

    override fun toolchains(): MutableList<JvmToolchainMetadata> {
        if (progressLoggerFactory != null) {
            val progressLogger = progressLoggerFactory.newOperation(JavaInstallationRegistry::class.java)!!.start("Discovering toolchains", "Discovering toolchains")
            val result = listInstallations()
                .parallelStream()
                .peek { location: InstallationLocation? -> progressLogger!!.progress("Extracting toolchain metadata from " + location!!.getDisplayName()) }
                .map<JvmToolchainMetadata> { location: InstallationLocation? -> this.resolveMetadata(location!!) }
                .collect(Collectors.toList())
            progressLogger!!.completed()
            return result
        } else {
            return listInstallations()
                .parallelStream()
                .map<JvmToolchainMetadata> { location: InstallationLocation? -> this.resolveMetadata(location!!) }
                .collect(Collectors.toList())
        }
    }

    private fun resolveMetadata(location: InstallationLocation): JvmToolchainMetadata {
        val metadata = metadataDetector.getMetadata(location)
        return JvmToolchainMetadata(metadata, location)
    }

    override fun addInstallation(installation: InstallationLocation) {
        installations.add(installation)
    }

    private fun collectInstallations(suppliers: MutableList<InstallationSupplier>): MutableSet<InstallationLocation> {
        return suppliers.parallelStream()
            .peek { x: InstallationSupplier? -> logger.debug("Discovering toolchains provided via {}", x!!.getSourceName()) }
            .map<MutableSet<InstallationLocation>> { obj: InstallationSupplier? -> obj!!.get() }
            .flatMap<InstallationLocation> { obj: MutableSet<InstallationLocation?>? -> obj!!.stream() }
            .filter { installationLocation: InstallationLocation? -> this.installationExists(installationLocation!!) }
            .map<InstallationLocation> { location: InstallationLocation? -> this.canonicalize(location!!) }
            .map<InstallationLocation> { location: InstallationLocation? -> this.maybeGetEnclosedInstallation(location!!) }
            .filter { installationLocation: InstallationLocation? -> this.installationHasExecutable(installationLocation!!) }
            .filter(Companion.distinctByKey<InstallationLocation>(Function { obj: InstallationLocation -> obj.getLocation() }))
            .collect(Collectors.toSet())
    }

    protected fun installationExists(installationLocation: InstallationLocation): Boolean {
        val file = installationLocation.getLocation()
        if (!file.exists()) {
            problemReporter.reportProblemIfNeeded(logger, installationLocation, "Directory " + installationLocation.getDisplayName() + " used for java installations does not exist")
            return false
        }
        if (!file.isDirectory()) {
            problemReporter.reportProblemIfNeeded(logger, installationLocation, "Path for java installation " + installationLocation.getDisplayName() + " points to a file, not a directory")
            return false
        }
        return true
    }

    protected fun installationHasExecutable(installationLocation: InstallationLocation): Boolean {
        if (!hasJavaExecutable(installationLocation.getLocation())) {
            problemReporter.reportProblemIfNeeded(logger, installationLocation, "Path for java installation " + installationLocation.getDisplayName() + " does not contain a java executable")
            return false
        }
        return true
    }

    private fun canonicalize(location: InstallationLocation): InstallationLocation {
        val file = location.getLocation()
        try {
            val canonicalFile = file.getCanonicalFile()
            val javaHome = findJavaHome(canonicalFile)
            return location.withLocation(javaHome)
        } catch (e: IOException) {
            throw GradleException(String.format("Could not canonicalize path to java installation: %s.", file), e)
        }
    }

    private fun maybeGetEnclosedInstallation(location: InstallationLocation): InstallationLocation {
        val home = location.getLocation()
        val parentPath = home.getParentFile()
        val isEmbeddedJre = home.getName().equals("jre", ignoreCase = true)
        if (isEmbeddedJre && hasJavaExecutable(parentPath)) {
            return location.withLocation(parentPath)
        }
        return location
    }

    private fun findJavaHome(potentialHome: File): File {
        if (os.isMacOsX && File(potentialHome, "Contents/Home").exists()) {
            return File(potentialHome, "Contents/Home")
        }
        val standaloneJre = File(potentialHome, "jre")
        if (!hasJavaExecutable(potentialHome) && hasJavaExecutable(standaloneJre)) {
            return standaloneJre
        }
        return potentialHome
    }

    private fun hasJavaExecutable(potentialHome: File): Boolean {
        return File(potentialHome, os.getExecutableName("bin/java")).exists()
    }

    private class ToolchainDetectionBuildOperation(private val detectionStrategy: Callable<MutableSet<InstallationLocation>>) : CallableBuildOperation<MutableSet<InstallationLocation>> {
        @Throws(Exception::class)
        override fun call(context: BuildOperationContext): MutableSet<InstallationLocation> {
            return detectionStrategy.call()
        }

        override fun description(): BuildOperationDescriptor.Builder {
            return BuildOperationDescriptor
                .displayName("Toolchain detection")
                .progressDisplayName("Detecting local java toolchains")
        }
    }

    private class Installations(private val initializer: Supplier<MutableSet<InstallationLocation>>) {
        private var locations: MutableSet<InstallationLocation>? = null

        @Synchronized
        fun get(): MutableSet<InstallationLocation> {
            initIfNeeded()
            return locations!!
        }

        @Synchronized
        fun add(location: InstallationLocation) {
            initIfNeeded()
            locations!!.add(location)
        }

        fun initIfNeeded() {
            if (locations == null) {
                locations = initializer.get()
            }
        }
    }

    companion object {
        private fun builtInSuppliers(toolchainConfiguration: ToolchainConfiguration, fileResolver: FileResolver, jdkCacheDirectory: JdkCacheDirectory): MutableList<InstallationSupplier> {
            val allSuppliers: MutableList<InstallationSupplier> = ArrayList<InstallationSupplier>()
            allSuppliers.add(EnvironmentVariableListInstallationSupplier(toolchainConfiguration, fileResolver))
            allSuppliers.add(EnvironmentVariableJavaHomeInstallationSupplier(toolchainConfiguration))
            allSuppliers.add(LocationListInstallationSupplier(toolchainConfiguration, fileResolver))
            allSuppliers.add(CurrentInstallationSupplier())
            allSuppliers.add(AutoInstalledInstallationSupplier(toolchainConfiguration, jdkCacheDirectory))
            return allSuppliers
        }

        fun <T> distinctByKey(keyExtractor: Function<in T?, *>): Predicate<T?> {
            val seen: MutableSet<Any> = ConcurrentHashMap.newKeySet<Any>()
            return Predicate { t: T? -> seen.add(keyExtractor.apply(t)) }
        }
    }
}
