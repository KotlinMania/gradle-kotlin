/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform.toolchain.internal.msvcpp

import net.rubygrapefruit.platform.SystemInfo
import org.gradle.nativeplatform.platform.Architecture
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioInstallCandidate
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetaDataProvider
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioVersionLocator
import org.gradle.platform.base.internal.toolchain.ComponentFound
import org.gradle.platform.base.internal.toolchain.ComponentNotFound
import org.gradle.platform.base.internal.toolchain.SearchResult
import org.gradle.util.internal.CollectionUtils.collect
import org.gradle.util.internal.CollectionUtils.sort
import org.gradle.util.internal.VersionNumber
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.function.Function

class DefaultVisualStudioLocator(
    private val commandLineLocator: VisualStudioVersionLocator,
    private val windowsRegistryLocator: VisualStudioVersionLocator,
    private val systemPathLocator: VisualStudioVersionLocator,
    private val versionDeterminer: VisualStudioMetaDataProvider,
    private val systemInfo: SystemInfo
) : VisualStudioLocator {
    private val foundInstalls: MutableMap<File?, VisualStudioInstall> = HashMap<File?, VisualStudioInstall>()
    private val brokenInstalls: MutableSet<File?> = LinkedHashSet<File?>()
    private var initialised = false

    override fun locateAllComponents(): MutableList<out VisualStudioInstall?> {
        initializeVisualStudioInstalls()

        return sort<VisualStudioInstall?>(foundInstalls.values, object : Comparator<VisualStudioInstall?> {
            override fun compare(o1: VisualStudioInstall, o2: VisualStudioInstall): Int {
                return o2.getVersion().compareTo(o1.getVersion())
            }
        })
    }

    override fun locateComponent(candidate: File?): SearchResult<VisualStudioInstall?> {
        initializeVisualStudioInstalls()

        if (candidate != null) {
            return locateUserSpecifiedInstall(candidate)
        }

        return determineDefaultInstall()
    }

    private fun initializeVisualStudioInstalls() {
        if (!initialised) {
            locateInstallsWith(commandLineLocator)
            locateInstallsWith(windowsRegistryLocator)
            if (foundInstalls.isEmpty()) {
                locateInstallsWith(systemPathLocator)
            }

            initialised = true
        }
    }

    private fun locateInstallsWith(versionLocator: VisualStudioVersionLocator) {
        val installs = versionLocator.visualStudioInstalls

        for (install in installs) {
            addInstallIfValid(install, versionLocator.source)
        }
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private fun addInstallIfValid(install: VisualStudioInstallCandidate, source: String?): Boolean {
        val visualCppDir = install.visualCppDir
        val visualStudioDir = install.installDir

        if (foundInstalls.containsKey(visualStudioDir)) {
            return true
        }
        if (brokenInstalls.contains(visualStudioDir)) {
            return false
        }

        if (isValidInstall(install) && install.visualCppVersion !== VersionNumber.UNKNOWN) {
            LOGGER.debug("Found Visual C++ {} at {}", install.visualCppVersion, visualCppDir)
            val visualStudioVersion = install.version
            val visualStudioDisplayVersion: String? = if (install.version === VersionNumber.UNKNOWN) "from " + source else install.version.toString()
            val visualCpp = buildVisualCppInstall("Visual C++ " + install.visualCppVersion, visualStudioDir, visualCppDir, install.visualCppVersion, install.compatibility)
            val visualStudio = VisualStudioInstall("Visual Studio " + visualStudioDisplayVersion, visualStudioDir, visualStudioVersion, visualCpp)
            foundInstalls.put(visualStudioDir, visualStudio)
            return true
        } else {
            LOGGER.debug("Ignoring candidate Visual C++ directory {} as it does not look like a Visual C++ installation.", visualCppDir)
            brokenInstalls.add(visualStudioDir)
            return false
        }
    }

    private fun locateUserSpecifiedInstall(candidate: File?): SearchResult<VisualStudioInstall?> {
        val install = versionDeterminer.getVisualStudioMetadataFromInstallDir(candidate)

        if (install != null && addInstallIfValid(install, "user provided path")) {
            return ComponentFound<VisualStudioInstall?>(foundInstalls.get(install.installDir))
        } else {
            LOGGER.debug("Ignoring candidate Visual C++ install for {} as it does not look like a Visual C++ installation.", candidate)
            return ComponentNotFound<VisualStudioInstall?>(String.format("The specified installation directory '%s' does not appear to contain a Visual Studio installation.", candidate))
        }
    }

    private fun buildVisualCppInstall(name: String?, vsPath: File?, basePath: File?, version: VersionNumber?, compatibility: VisualStudioInstallCandidate.Compatibility): VisualCppInstall {
        when (compatibility) {
            VisualStudioInstallCandidate.Compatibility.LEGACY -> return buildLegacyVisualCppInstall(name, vsPath, basePath, version)
            VisualStudioInstallCandidate.Compatibility.VS2017_OR_LATER -> return buildVisualCppInstall(name, vsPath, basePath, version)
            else -> throw IllegalArgumentException("Cannot build VisualCpp install for unknown compatibility level: " + compatibility)
        }
    }

    private fun buildLegacyVisualCppInstall(name: String?, vsPath: File?, basePath: File?, version: VersionNumber?): VisualCppInstall {
        val architectureDescriptorBuilders: MutableList<ArchitectureDescriptorBuilder> = ArrayList<ArchitectureDescriptorBuilder>()

        architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.LEGACY_X86_ON_X86)
        architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.LEGACY_AMD64_ON_X86)
        architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.LEGACY_IA64_ON_X86)
        architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.LEGACY_ARM_ON_X86)

        val isNativeAmd64 = systemInfo.getArchitecture() == SystemInfo.Architecture.amd64
        if (isNativeAmd64) {
            // Prefer 64-bit tools when building on a 64-bit OS
            architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.LEGACY_AMD64_ON_AMD64)
            architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.LEGACY_X86_ON_AMD64)
            architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.LEGACY_ARM_ON_AMD64)
        }

        // populates descriptors, last descriptor in wins for a given architecture
        val descriptors: MutableMap<Architecture?, ArchitectureSpecificVisualCpp?> = HashMap<Architecture?, ArchitectureSpecificVisualCpp?>()
        for (architectureDescriptorBuilder in architectureDescriptorBuilders) {
            val descriptor = architectureDescriptorBuilder.buildDescriptor(version, basePath, vsPath)
            if (descriptor.isInstalled()) {
                descriptors.put(architectureDescriptorBuilder.architecture, descriptor)
            }
        }

        return VisualCppInstall(name, version, descriptors)
    }

    private fun buildVisualCppInstall(name: String?, vsPath: File?, basePath: File?, version: VersionNumber?): VisualCppInstall {
        val architectureDescriptorBuilders: MutableList<ArchitectureDescriptorBuilder> = ArrayList<ArchitectureDescriptorBuilder>()

        architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.X86_ON_X86)
        architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.AMD64_ON_X86)
        architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.ARM_ON_X86)
        architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.ARM64_ON_X86)

        val isNativeAmd64 = systemInfo.getArchitecture() == SystemInfo.Architecture.amd64
        if (isNativeAmd64) {
            // Prefer 64-bit tools when building on a 64-bit OS
            architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.AMD64_ON_AMD64)
            architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.X86_ON_AMD64)
            architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.ARM_ON_AMD64)
            architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.ARM64_ON_AMD64)
        }

        // populates descriptors, last descriptor in wins for a given architecture
        val descriptors: MutableMap<Architecture?, ArchitectureSpecificVisualCpp?> = HashMap<Architecture?, ArchitectureSpecificVisualCpp?>()
        for (architectureDescriptorBuilder in architectureDescriptorBuilders) {
            val descriptor = architectureDescriptorBuilder.buildDescriptor(version, basePath, vsPath)
            if (descriptor.isInstalled()) {
                descriptors.put(architectureDescriptorBuilder.architecture, descriptor)
            }
        }

        return VisualCppInstall(name, version, descriptors)
    }

    private fun determineDefaultInstall(): SearchResult<VisualStudioInstall?> {
        var candidate: VisualStudioInstall? = null

        for (visualStudio in foundInstalls.values) {
            if (candidate == null || visualStudio.getVersion().compareTo(candidate.getVersion()) > 0) {
                candidate = visualStudio
            }
        }

        if (candidate != null) {
            return ComponentFound<VisualStudioInstall?>(candidate)
        }
        if (brokenInstalls.isEmpty()) {
            return ComponentNotFound<VisualStudioInstall?>("Could not locate a Visual Studio installation, using the command line tool, Windows registry or system path.")
        }
        return ComponentNotFound<VisualStudioInstall?>(
            "Could not locate a Visual Studio installation. None of the following locations contain a valid installation",
            collect<String?, File?, ArrayList<String?>?>(brokenInstalls, ArrayList<String?>(), Function { obj: File? -> obj!!.getAbsolutePath() })
        )
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultVisualStudioLocator::class.java)
        private const val PATH_COMMON = "Common7/"
        private const val PATH_BIN = "bin/"
        private const val LEGACY_COMPILER_FILENAME = "cl.exe"
        private const val VS2017_COMPILER_FILENAME = "HostX86/x86/cl.exe"

        private fun isValidInstall(install: VisualStudioInstallCandidate): Boolean {
            when (install.compatibility) {
                VisualStudioInstallCandidate.Compatibility.LEGACY -> return File(install.installDir, PATH_COMMON).isDirectory()
                        && isLegacyVisualCpp(install.visualCppDir)

                VisualStudioInstallCandidate.Compatibility.VS2017_OR_LATER -> return File(install.installDir, PATH_COMMON).isDirectory()
                        && isVS2017VisualCpp(install.visualCppDir)

                else -> throw IllegalArgumentException("Cannot determine valid install for unknown compatibility: " + install.compatibility)
            }
        }

        private fun isLegacyVisualCpp(candidate: File?): Boolean {
            return File(candidate, PATH_BIN + LEGACY_COMPILER_FILENAME).isFile()
        }

        private fun isVS2017VisualCpp(candidate: File?): Boolean {
            return File(candidate, PATH_BIN + VS2017_COMPILER_FILENAME).isFile()
        }
    }
}
