/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries
import org.gradle.util.internal.VersionNumber
import java.io.File

class UcrtInstall(baseDir: File?, version: VersionNumber?, name: String?) : WindowsKitInstall(baseDir, version, name) {
    /**
     * Returns the C runtime for the given platform.
     */
    fun getCRuntime(platform: NativePlatformInternal): SystemLibraries {
        if (platform.getArchitecture().isAmd64()) {
            return UcrtInstall.UcrtSystemLibraries("x64")
        }
        if (platform.getArchitecture().isArm64()) {
            return UcrtInstall.UcrtSystemLibraries("arm64")
        }
        if (platform.getArchitecture().isArm32()) {
            return UcrtInstall.UcrtSystemLibraries("arm")
        }
        if (platform.getArchitecture().isI386()) {
            return UcrtInstall.UcrtSystemLibraries("x86")
        }
        throw UnsupportedOperationException(String.format("Supported %s for %s.", platform.getArchitecture().displayName, toString()))
    }

    private inner class UcrtSystemLibraries(private val platformDirName: String?) : SystemLibraries {
        val includeDirs: MutableList<File?>
            get() = mutableListOf<File?>(File(getBaseDir(), "Include/" + getVersion() + "/ucrt"))

        val libDirs: MutableList<File?>
            get() = mutableListOf<File?>(File(getBaseDir(), "Lib/" + getVersion() + "/ucrt/" + platformDirName))

        val preprocessorMacros: MutableMap<String?, String?>
            get() = mutableMapOf<String?, String?>()
    }
}
