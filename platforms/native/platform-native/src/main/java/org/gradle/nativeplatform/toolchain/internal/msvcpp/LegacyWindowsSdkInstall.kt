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

import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.util.internal.VersionNumber
import java.io.File
import java.util.Arrays

class LegacyWindowsSdkInstall(val baseDir: File?, private val version: VersionNumber?, private val name: String?) : WindowsSdkInstall {
    override fun getName(): String? {
        return name
    }

    override fun getVersion(): VersionNumber? {
        return version
    }

    override fun forPlatform(platform: NativePlatformInternal): WindowsSdk {
        if (platform.getArchitecture().isAmd64()) {
            return LegacyWindowsSdkInstall.LegacyPlatformWindowsSdk(BINPATHS_AMD64, LIBPATHS_AMD64)
        }
        if (platform.getArchitecture().isIa64()) {
            return LegacyWindowsSdkInstall.LegacyPlatformWindowsSdk(BINPATHS_IA64, LIBPATHS_IA64)
        }
        if (platform.getArchitecture().isArm()) {
            return LegacyWindowsSdkInstall.LegacyPlatformWindowsSdk(BINPATHS_ARM, LIBPATHS_ARM)
        }
        if (platform.getArchitecture().isI386()) {
            return LegacyWindowsSdkInstall.LegacyPlatformWindowsSdk(BINPATHS_X86, LIBPATHS_X86)
        }
        throw UnsupportedOperationException(String.format("Unsupported %s for %s.", platform.getArchitecture().displayName, toString()))
    }

    private inner class LegacyPlatformWindowsSdk(private val binPaths: Array<String>, private val libPaths: Array<String>) : WindowsSdk {
        val implementationVersion: VersionNumber?
            get() = version

        override fun getSdkVersion(): VersionNumber? {
            return version
        }

        val includeDirs: MutableList<File>
            get() {
                val includesSdk8 = Arrays.asList<File?>(
                    File(baseDir, "Include/shared"),
                    File(baseDir, "Include/um")
                )
                for (file in includesSdk8) {
                    if (!file.isDirectory()) {
                        return mutableListOf<File?>(File(baseDir, "Include"))
                    }
                }
                return includesSdk8
            }

        val libDirs: MutableList<File?>
            get() = mutableListOf<File?>(getAvailableFile(*libPaths))

        val preprocessorMacros: MutableMap<String?, String?>
            get() = mutableMapOf<String?, String?>()

        override fun getResourceCompiler(): File {
            return File(this.binDir, "rc.exe")
        }

        val path: MutableList<File?>
            get() = mutableListOf<File?>(this.binDir)

        val binDir: File
            get() = getAvailableFile(*binPaths)

        fun getAvailableFile(vararg candidates: String): File {
            for (candidate in candidates) {
                val file = File(baseDir, candidate)
                if (file.isDirectory()) {
                    return file
                }
            }

            return File(baseDir, candidates[0])
        }
    }

    companion object {
        private val BINPATHS_X86 = arrayOf<String>(
            "bin/x86",
            "Bin"
        )
        private val BINPATHS_AMD64 = arrayOf<String>(
            "bin/x64"
        )
        private val BINPATHS_IA64 = arrayOf<String>(
            "bin/IA64"
        )
        private val BINPATHS_ARM = arrayOf<String>(
            "bin/arm"
        )
        private const val LIBPATH_SDK8 = "Lib/win8/um/"
        private const val LIBPATH_SDK81 = "Lib/winv6.3/um/"
        private val LIBPATHS_X86 = arrayOf<String>(
            LIBPATH_SDK81 + "x86",
            LIBPATH_SDK8 + "x86",
            "lib"
        )
        private val LIBPATHS_AMD64 = arrayOf<String>(
            LIBPATH_SDK81 + "x64",
            LIBPATH_SDK8 + "x64",
            "lib/x64"
        )
        private val LIBPATHS_IA64 = arrayOf<String>(
            "lib/IA64"
        )
        private val LIBPATHS_ARM = arrayOf<String>(
            LIBPATH_SDK81 + "arm",
            LIBPATH_SDK8 + "arm"
        )
    }
}
