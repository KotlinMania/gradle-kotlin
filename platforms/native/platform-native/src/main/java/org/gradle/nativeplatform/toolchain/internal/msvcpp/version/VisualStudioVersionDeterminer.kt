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

import org.gradle.api.specs.Spec
import java.io.File

class VisualStudioVersionDeterminer(
    private val commandLineLocator: VisualStudioVersionLocator,
    private val windowsRegistryLocator: VisualStudioVersionLocator,
    private val visualCppMetadataProvider: VisualCppMetadataProvider
) : VisualStudioMetaDataProvider {
    override fun getVisualStudioMetadataFromInstallDir(installDir: File?): VisualStudioInstallCandidate? {
        // Check the normal metadata first
        val install = getVisualStudioMetadata(object : Spec<VisualStudioInstallCandidate?> {
            override fun isSatisfiedBy(install: VisualStudioInstallCandidate): Boolean {
                return install.getInstallDir() == installDir
            }
        })

        // If we can't discover the version from the normal metadata, make some assumptions
        if (install == null) {
            val visualCppMetadata = visualCppMetadataProvider.getVisualCppFromMetadataFile(installDir)
            if (visualCppMetadata != null) {
                return VisualStudioMetadataBuilder()
                    .installDir(installDir)
                    .visualCppDir(visualCppMetadata.getVisualCppDir())
                    .visualCppVersion(visualCppMetadata.getVersion())
                    .compatibility(VisualStudioInstallCandidate.Compatibility.VS2017_OR_LATER)
                    .build()
            } else {
                val visualCppDir = File(installDir, "VC")
                return VisualStudioMetadataBuilder()
                    .installDir(installDir)
                    .visualCppDir(visualCppDir)
                    .compatibility(VisualStudioInstallCandidate.Compatibility.LEGACY)
                    .build()
            }
        }

        return install
    }

    override fun getVisualStudioMetadataFromCompiler(compilerFile: File): VisualStudioInstallCandidate? {
        // Check the normal metadata first
        val install = getVisualStudioMetadata(object : Spec<VisualStudioInstallCandidate?> {
            override fun isSatisfiedBy(install: VisualStudioInstallCandidate): Boolean {
                if (install.getVersion().getMajor() >= 15) {
                    val compilerRoot: File = getNthParent(compilerFile, 4)
                    return compilerRoot == install.getVisualCppDir()
                } else {
                    var compilerRoot: File = getNthParent(compilerFile, 2)
                    if (compilerRoot == install.getVisualCppDir()) {
                        return true
                    } else {
                        compilerRoot = getNthParent(compilerFile, 3)
                        return compilerRoot == install.getVisualCppDir()
                    }
                }
            }
        })

        // If we can't discover the version from the normal metadata, make some assumptions
        if (install == null) {
            val installDir: File = getNthParent(compilerFile, 8)
            val visualCppMetadata = visualCppMetadataProvider.getVisualCppFromMetadataFile(installDir)
            if (visualCppMetadata != null) {
                val visualCppDir = visualCppMetadata.getVisualCppDir()
                return VisualStudioMetadataBuilder()
                    .installDir(installDir)
                    .visualCppDir(visualCppDir)
                    .visualCppVersion(visualCppMetadata.getVersion())
                    .compatibility(VisualStudioInstallCandidate.Compatibility.VS2017_OR_LATER)
                    .build()
            } else {
                var visualCppDir: File = getNthParent(compilerFile, 2)
                if ("VC" != visualCppDir.getName()) {
                    visualCppDir = getNthParent(compilerFile, 3)
                }
                return VisualStudioMetadataBuilder()
                    .installDir(visualCppDir.getParentFile())
                    .visualCppDir(visualCppDir)
                    .compatibility(VisualStudioInstallCandidate.Compatibility.LEGACY)
                    .build()
            }
        }

        return install
    }

    private fun getVisualStudioMetadata(spec: Spec<VisualStudioInstallCandidate?>): VisualStudioInstallCandidate? {
        var installs = commandLineLocator.getVisualStudioInstalls()
        if (installs.size > 0) {
            val install = findMetadataForInstallDir(spec, installs)
            if (install != null) {
                return install
            }
        } else {
            installs = windowsRegistryLocator.getVisualStudioInstalls()
            val install = findMetadataForInstallDir(spec, installs)
            if (install != null) {
                return install
            }
        }

        return null
    }

    private fun findMetadataForInstallDir(spec: Spec<VisualStudioInstallCandidate?>, installs: MutableList<VisualStudioInstallCandidate?>): VisualStudioInstallCandidate? {
        for (install in installs) {
            if (spec.isSatisfiedBy(install)) {
                return install
            }
        }
        return null
    }

    companion object {
        private fun getNthParent(file: File, n: Int): File {
            var n = n
            if (n == 0) {
                return file
            } else {
                val parent = file.getParentFile()
                if (parent != null) {
                    return getNthParent(parent, --n)
                } else {
                    return file
                }
            }
        }
    }
}
