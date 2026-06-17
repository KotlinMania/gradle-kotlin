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

import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.internal.VersionNumber
import java.io.File

@ServiceScope(Scope.BuildSession::class)
class DefaultUcrtLocator(windowsRegistry: WindowsRegistry?) : AbstractWindowsKitComponentLocator<UcrtInstall?>(windowsRegistry), UcrtLocator {
    public override fun getComponentName(): String {
        return COMPONENT_NAME
    }

    override fun getDisplayName(): String {
        return DISPLAY_NAME
    }

    override fun isValidComponentBinDir(binDir: File?): Boolean {
        // Nothing special to check for UCRT
        return true
    }

    override fun isValidComponentIncludeDir(includeDir: File?): Boolean {
        return File(includeDir, "io.h").exists()
    }

    override fun isValidComponentLibDir(libDir: File?): Boolean {
        for (platform in AbstractWindowsKitComponentLocator.Companion.PLATFORMS) {
            if (!File(libDir, platform + "/libucrt.lib").exists()) {
                return false
            }
        }
        return true
    }

    override fun newComponent(baseDir: File?, binDir: File?, version: VersionNumber?, discoveryType: DiscoveryType): UcrtInstall {
        return UcrtInstall(baseDir, version, getVersionedDisplayName(version, discoveryType))
    }

    companion object {
        private const val DISPLAY_NAME = "Universal C Runtime"
        private const val COMPONENT_NAME = "ucrt"
    }
}
