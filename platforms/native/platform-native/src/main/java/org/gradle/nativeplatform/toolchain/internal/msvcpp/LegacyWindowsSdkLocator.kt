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

import com.google.common.collect.Lists
import net.rubygrapefruit.platform.MissingRegistryEntryException
import net.rubygrapefruit.platform.WindowsRegistry
import org.apache.commons.lang3.StringUtils
import org.gradle.internal.FileUtils
import org.gradle.internal.os.OperatingSystem
import org.gradle.platform.base.internal.toolchain.ComponentFound
import org.gradle.platform.base.internal.toolchain.ComponentNotFound
import org.gradle.platform.base.internal.toolchain.SearchResult
import org.gradle.util.internal.VersionNumber
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

//TODO: Simplify this class by busting it up into a locator for legacy SDKs and locator(s) for Windows 8 kits
class LegacyWindowsSdkLocator(private val os: OperatingSystem, private val windowsRegistry: WindowsRegistry) : WindowsSdkLocator {
    private val foundSdks: MutableMap<File?, WindowsSdkInstall> = HashMap<File?, WindowsSdkInstall>()
    private var pathSdk: WindowsSdkInstall? = null
    private var initialised = false

    override fun locateComponent(candidate: File?): SearchResult<WindowsSdkInstall?> {
        initializeWindowsSdks()

        if (candidate != null) {
            return locateUserSpecifiedSdk(candidate)
        }

        return locateDefaultSdk()
    }

    override fun locateAllComponents(): MutableList<out WindowsSdkInstall?> {
        initializeWindowsSdks()
        return Lists.newArrayList<WindowsSdkInstall?>(foundSdks.values)
    }

    private fun initializeWindowsSdks() {
        if (!initialised) {
            locateSdksInRegistry()
            locateKitsInRegistry()
            locateSdkInPath()
            initialised = true
        }
    }

    private fun locateSdksInRegistry() {
        for (baseKey in REGISTRY_BASEPATHS!!) {
            locateSdksInRegistry(baseKey)
        }
    }

    private fun locateSdksInRegistry(baseKey: String?) {
        try {
            val subkeys = windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_SDK)
            for (subkey in subkeys) {
                try {
                    val basePath = baseKey + REGISTRY_ROOTPATH_SDK + "\\" + subkey
                    val sdkDir = FileUtils.canonicalize(File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, basePath, REGISTRY_FOLDER)))
                    val version: String = formatVersion(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, basePath, REGISTRY_VERSION))
                    val name = windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, basePath, REGISTRY_NAME)

                    if (isWindowsSdk(sdkDir)) {
                        LOGGER.debug("Found Windows SDK {} at {}", version, sdkDir)
                        addSdk(sdkDir, version, name)
                    } else {
                        LOGGER.debug("Ignoring candidate Windows SDK directory {} as it does not look like a Windows SDK installation.", sdkDir)
                    }
                } catch (e: MissingRegistryEntryException) {
                    // Ignore the subkey if it doesn't have a folder and version
                }
            }
        } catch (e: MissingRegistryEntryException) {
            // No SDK information available in the registry
        }
    }

    private fun locateKitsInRegistry() {
        for (baseKey in REGISTRY_BASEPATHS!!) {
            locateKitsInRegistry(baseKey)
        }
    }

    private fun locateKitsInRegistry(baseKey: String?) {
        val versions = arrayOf<String?>(
            VERSION_KIT_8,
            VERSION_KIT_81
        )
        val keys = arrayOf<String?>(
            REGISTRY_KIT_8,
            REGISTRY_KIT_81
        )

        for (i in keys.indices) {
            try {
                val kitDir = FileUtils.canonicalize(File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_KIT, keys[i])))
                if (isWindowsSdk(kitDir)) {
                    LOGGER.debug("Found Windows Kit {} at {}", versions[i], kitDir)
                    addSdk(kitDir, versions[i], NAME_KIT + " " + versions[i])
                } else {
                    LOGGER.debug("Ignoring candidate Windows Kit directory {} as it does not look like a Windows Kit installation.", kitDir)
                }
            } catch (e: MissingRegistryEntryException) {
                // Ignore the version if the string cannot be read
            }
        }
    }

    private fun locateSdkInPath() {
        val resourceCompiler = os.findInPath(RESOURCE_FILENAME)
        if (resourceCompiler == null) {
            LOGGER.debug("Could not find Windows resource compiler in system path.")
            return
        }
        var sdkDir: File? = FileUtils.canonicalize(resourceCompiler.getParentFile().getParentFile())
        if (!isWindowsSdk(sdkDir)) {
            sdkDir = sdkDir!!.getParentFile()
            if (!isWindowsSdk(sdkDir)) {
                LOGGER.debug("Ignoring candidate Windows SDK for {} as it does not look like a Windows SDK installation.", resourceCompiler)
            }
        }
        LOGGER.debug("Found Windows SDK {} using system path", sdkDir)

        if (!foundSdks.containsKey(sdkDir)) {
            addSdk(sdkDir, "path", "Path-resolved Windows SDK")
        }
        pathSdk = foundSdks.get(sdkDir)
    }

    private fun locateUserSpecifiedSdk(candidate: File): SearchResult<WindowsSdkInstall?> {
        val sdkDir = FileUtils.canonicalize(candidate)
        if (!isWindowsSdk(sdkDir)) {
            return ComponentNotFound<WindowsSdkInstall?>(String.format("The specified installation directory '%s' does not appear to contain a Windows SDK installation.", candidate))
        }

        if (!foundSdks.containsKey(sdkDir)) {
            addSdk(sdkDir, VERSION_USER, NAME_USER)
        }
        return ComponentFound<WindowsSdkInstall?>(foundSdks.get(sdkDir))
    }

    private fun locateDefaultSdk(): SearchResult<WindowsSdkInstall?> {
        if (pathSdk != null) {
            return ComponentFound<WindowsSdkInstall?>(pathSdk)
        }

        var candidate: WindowsSdkInstall? = null
        for (windowsSdk in foundSdks.values) {
            if (candidate == null || windowsSdk.getVersion().compareTo(candidate.getVersion()) > 0) {
                candidate = windowsSdk
            }
        }
        return if (candidate == null) ComponentNotFound<WindowsSdkInstall?>("Could not locate a Windows SDK installation, using the Windows registry and system path.") else ComponentFound<WindowsSdkInstall?>(
            candidate
        )
    }

    private fun addSdk(path: File?, version: String?, name: String?) {
        foundSdks.put(path, LegacyWindowsSdkInstall(path, VersionNumber.parse(version), name))
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(LegacyWindowsSdkLocator::class.java)
        private val REGISTRY_BASEPATHS: Array<String?>? = arrayOf<String?>(
            "SOFTWARE\\",
            "SOFTWARE\\Wow6432Node\\"
        )
        private const val REGISTRY_ROOTPATH_SDK = "Microsoft\\Microsoft SDKs\\Windows"
        private const val REGISTRY_ROOTPATH_KIT = "Microsoft\\Windows Kits\\Installed Roots"
        private const val REGISTRY_FOLDER = "InstallationFolder"
        private const val REGISTRY_VERSION = "ProductVersion"
        private const val REGISTRY_NAME = "ProductName"
        private const val REGISTRY_KIT_8 = "KitsRoot"
        private const val REGISTRY_KIT_81 = "KitsRoot81"
        private const val VERSION_KIT_8 = "8.0"
        private const val VERSION_KIT_81 = "8.1"
        private const val VERSION_USER = "user"

        private const val NAME_USER = "User-provided Windows SDK"
        private const val NAME_KIT = "Windows Kit"

        private val RESOURCE_PATHS: Array<String?>? = arrayOf<String?>(
            "bin/x86/",
            "bin/"
        )

        private val KERNEL32_PATHS: Array<String?>? = arrayOf<String?>(
            "lib/winv6.3/um/x86/",
            "lib/win8/um/x86/",
            "lib/"
        )

        private const val RESOURCE_FILENAME = "rc.exe"
        private const val KERNEL32_FILENAME = "kernel32.lib"

        private fun isWindowsSdk(candidate: File?): Boolean {
            var hasResourceCompiler = false
            var hasKernel32Lib = false

            for (path in RESOURCE_PATHS!!) {
                if (File(candidate, path + RESOURCE_FILENAME).isFile()) {
                    hasResourceCompiler = true
                    break
                }
            }

            for (path in KERNEL32_PATHS!!) {
                if (File(candidate, path + KERNEL32_FILENAME).isFile()) {
                    hasKernel32Lib = true
                    break
                }
            }

            return hasResourceCompiler && hasKernel32Lib
        }

        private fun formatVersion(version: String): String {
            var version = version
            val index = StringUtils.ordinalIndexOf(version, ".", 2)

            if (index != -1) {
                version = version.substring(0, index)
            }

            return version
        }
    }
}
