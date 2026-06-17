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
import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.util.internal.VersionNumber
import java.io.File

class WindowsKitWindowsSdkLocator(windowsRegistry: WindowsRegistry?, private val systemInfo: SystemInfo) : AbstractWindowsKitComponentLocator<WindowsKitSdkInstall?>(windowsRegistry) {
    override fun getComponentName(): String {
        return COMPONENT_NAME
    }

    override fun getDisplayName(): String {
        return DISPLAY_NAME
    }

    override fun isValidComponentBinDir(binDir: File?): Boolean {
        for (platform in PLATFORMS) {
            if (!File(binDir, platform + "/" + RC_EXE).exists()) {
                return false
            }
        }
        return true
    }

    override fun isValidComponentIncludeDir(includeDir: File?): Boolean {
        return File(includeDir, "windows.h").exists()
    }

    override fun isValidComponentLibDir(libDir: File?): Boolean {
        for (platform in PLATFORMS) {
            if (!File(libDir, platform + "/kernel32.lib").exists()) {
                return false
            }
        }
        return true
    }

    override fun newComponent(baseDir: File, binDir: File, version: VersionNumber, discoveryType: DiscoveryType): WindowsKitSdkInstall {
        return WindowsKitSdkInstall(baseDir, version, binDir, getVersionedDisplayName(version, discoveryType), systemInfo)
    }

    companion object {
        private const val COMPONENT_NAME = "um"
        private const val DISPLAY_NAME = "Windows SDK"
        private const val RC_EXE = "rc.exe"
    }
}
