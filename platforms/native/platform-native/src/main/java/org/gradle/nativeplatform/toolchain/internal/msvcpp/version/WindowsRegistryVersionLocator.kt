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
import org.gradle.internal.FileUtils
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.internal.VersionNumber
import java.io.File

@ServiceScope(Scope.BuildSession::class)
class WindowsRegistryVersionLocator(private val windowsRegistry: WindowsRegistry) : AbstractVisualStudioVersionLocator(), VisualStudioVersionLocator {
    override fun locateInstalls(): MutableList<VisualStudioInstallCandidate?> {
        val installs: MutableList<VisualStudioInstallCandidate?> = ArrayList<VisualStudioInstallCandidate?>()
        for (baseKey in REGISTRY_BASEPATHS) {
            locateInstallsInRegistry(installs, baseKey)
        }
        return installs
    }

    override fun getSource(): String {
        return "windows registry"
    }

    private fun locateInstallsInRegistry(installs: MutableList<VisualStudioInstallCandidate?>, baseKey: String?) {
        val visualCppVersions: MutableList<String>
        try {
            visualCppVersions = windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_VC)
        } catch (e: MissingRegistryEntryException) {
            // No Visual Studio information available in the registry
            return
        }

        for (versionString in visualCppVersions) {
            if (!versionString.matches("\\d+\\.\\d+".toRegex())) {
                // Ignore the other values
                continue
            }
            var visualCppDir = File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_VC, versionString))
            visualCppDir = FileUtils.canonicalize(visualCppDir)
            val visualStudioDir = visualCppDir.getParentFile()
            val version = VersionNumber.parse(versionString)
            installs.add(
                VisualStudioMetadataBuilder()
                    .installDir(visualStudioDir)
                    .visualCppDir(visualCppDir)
                    .version(version)
                    .visualCppVersion(version)
                    .build()
            )
        }
    }

    companion object {
        val REGISTRY_BASEPATHS: Array<String?> = arrayOf<String?>(
            "SOFTWARE\\",
            "SOFTWARE\\Wow6432Node\\"
        )
        const val REGISTRY_ROOTPATH_VC: String = "Microsoft\\VisualStudio\\SxS\\VC7"
    }
}
