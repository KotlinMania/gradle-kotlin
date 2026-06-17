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
package org.gradle.nativeplatform.toolchain.internal.msvcpp

import net.rubygrapefruit.platform.SystemInfo
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.util.internal.VersionNumber
import org.jspecify.annotations.NullMarked
import java.io.File
import java.util.Arrays

@NullMarked
class WindowsKitSdkInstall(baseDir: File, version: VersionNumber, private val binDir: File, name: String, private val systemInfo: SystemInfo) : WindowsKitInstall(baseDir, version, name),
    WindowsSdkInstall {
    override fun forPlatform(platform: NativePlatformInternal): WindowsSdk {
        val host: String?
        when (systemInfo.getArchitecture()) {
            SystemInfo.Architecture.i386 -> host = "x86"
            SystemInfo.Architecture.amd64 -> host = "x64"
            SystemInfo.Architecture.aarch64 -> host = "arm64"
            else -> throw UnsupportedOperationException(String.format("Unsupported host for %s", toString()))
        }

        if (platform.getArchitecture().isAmd64()) {
            return WindowsKitSdkInstall.WindowsKitBackedSdk("x64", host)
        }
        if (platform.getArchitecture().isArm64()) {
            return WindowsKitSdkInstall.WindowsKitBackedSdk("arm64", host)
        }
        if (platform.getArchitecture().isArm32()) {
            return WindowsKitSdkInstall.WindowsKitBackedSdk("arm", host)
        }
        if (platform.getArchitecture().isI386()) {
            return WindowsKitSdkInstall.WindowsKitBackedSdk("x86", host)
        }
        throw UnsupportedOperationException(String.format("Unsupported %s for %s.", platform.getArchitecture().displayName, toString()))
    }

    private inner class WindowsKitBackedSdk(private val platformDirName: String, private val hostDirName: String) : WindowsSdk {
        val implementationVersion: VersionNumber
            get() = this@WindowsKitSdkInstall.getVersion()

        override fun getSdkVersion(): VersionNumber {
            return this.implementationVersion
        }

        val includeDirs: MutableList<File>
            get() = Arrays.asList<File>(
                File(getBaseDir(), "Include/" + this.implementationVersion.toString() + "/um"),
                File(getBaseDir(), "Include/" + this.implementationVersion.toString() + "/shared")
            )

        val libDirs: MutableList<File>
            get() = mutableListOf<File>(File(getBaseDir(), "Lib/" + this.implementationVersion.toString() + "/um/" + platformDirName))

        override fun getResourceCompiler(): File {
            return File(this.hostBinDir, "rc.exe")
        }

        val preprocessorMacros: MutableMap<String, String>
            get() = mutableMapOf<String, String>()

        val path: MutableList<File>
            get() = mutableListOf<File>(getBinDir())

        fun getBinDir(): File {
            return File(binDir, platformDirName)
        }

        val hostBinDir: File
            get() = File(binDir, hostDirName)
    }
}
