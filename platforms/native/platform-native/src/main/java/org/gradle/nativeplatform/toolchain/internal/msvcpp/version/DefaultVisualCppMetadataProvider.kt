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
package org.gradle.nativeplatform.toolchain.internal.msvcpp.version

import net.rubygrapefruit.platform.MissingRegistryEntryException
import net.rubygrapefruit.platform.WindowsRegistry
import org.apache.commons.io.FileUtils
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.util.internal.VersionNumber
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

class DefaultVisualCppMetadataProvider(private val windowsRegistry: WindowsRegistry) : VisualCppMetadataProvider {
    override fun getVisualCppFromRegistry(version: String?): VisualCppInstallCandidate? {
        for (baseKey in REGISTRY_BASEPATHS) {
            try {
                val visualCppDir = File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_VC, version))
                return DefaultVisualCppMetadata(visualCppDir, VersionNumber.parse(version))
            } catch (e: MissingRegistryEntryException) {
                // Version not found at this base path
            }
        }

        LOGGER!!.debug("No Windows registry values found for version " + version)
        return null
    }

    override fun getVisualCppFromMetadataFile(installDir: File?): VisualCppInstallCandidate? {
        val msvcVersionFile = File(installDir, VS2017_METADATA_FILE_PATH)
        if (!msvcVersionFile.exists() || !msvcVersionFile.isFile()) {
            LOGGER!!.debug("The MSVC version file at {} either does not exist or is not a file.  Cannot determine the MSVC version for this installation.", msvcVersionFile.getAbsolutePath())
            return null
        }
        try {
            val versionString = FileUtils.readFileToString(msvcVersionFile, StandardCharsets.UTF_8).trim { it <= ' ' }
            val visualCppDir = File(installDir, VS2017_COMPILER_PATH_PREFIX + "/" + versionString)
            return DefaultVisualCppMetadata(visualCppDir, VersionNumber.parse(versionString))
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    private class DefaultVisualCppMetadata(private val visualCppDir: File?, private val version: VersionNumber?) : VisualCppInstallCandidate {
        override fun getVisualCppDir(): File? {
            return visualCppDir
        }

        override fun getVersion(): VersionNumber? {
            return version
        }
    }

    companion object {
        private const val VS2017_METADATA_FILE_PATH = "VC/Auxiliary/Build/Microsoft.VCToolsVersion.default.txt"
        private const val VS2017_COMPILER_PATH_PREFIX = "VC/Tools/MSVC"

        private val REGISTRY_BASEPATHS = arrayOf<String?>(
            "SOFTWARE\\",
            "SOFTWARE\\Wow6432Node\\"
        )
        private const val REGISTRY_ROOTPATH_VC = "Microsoft\\VisualStudio\\SxS\\VC7"

        private val LOGGER = getLogger(DefaultVisualCppMetadataProvider::class.java)
    }
}
