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

import com.google.common.annotations.VisibleForTesting
import net.rubygrapefruit.platform.SystemInfo
import net.rubygrapefruit.platform.WindowsRegistry
import org.gradle.internal.logging.text.DiagnosticsVisitor
import org.gradle.internal.os.OperatingSystem
import org.gradle.platform.base.internal.toolchain.SearchResult
import java.io.File

class DefaultWindowsSdkLocator @VisibleForTesting internal constructor(
    private val legacyWindowsSdkLocator: WindowsSdkLocator,
    private val windowsKitWindowsSdkLocator: WindowsComponentLocator<WindowsKitSdkInstall?>
) : WindowsSdkLocator {
    constructor(operatingSystem: OperatingSystem?, windowsRegistry: WindowsRegistry?, systemInfo: SystemInfo?) : this(
        LegacyWindowsSdkLocator(operatingSystem, windowsRegistry),
        WindowsKitWindowsSdkLocator(windowsRegistry, systemInfo)
    )

    override fun locateComponent(candidate: File?): SearchResult<WindowsSdkInstall?> {
        return SdkSearchResult(legacyWindowsSdkLocator.locateComponent(candidate), windowsKitWindowsSdkLocator.locateComponent(candidate))
    }

    override fun locateAllComponents(): MutableList<WindowsSdkInstall?> {
        val allSdks: MutableList<WindowsSdkInstall?> = ArrayList<WindowsSdkInstall?>()
        allSdks.addAll(legacyWindowsSdkLocator.locateAllComponents())
        allSdks.addAll(windowsKitWindowsSdkLocator.locateAllComponents())
        return allSdks
    }

    private class SdkSearchResult(val legacySearchResult: SearchResult<WindowsSdkInstall?>, val windowsKitSearchResult: SearchResult<WindowsKitSdkInstall?>) : SearchResult<WindowsSdkInstall?> {
        val component: WindowsSdkInstall?
            get() {
                if (windowsKitSearchResult.isAvailable) {
                    return windowsKitSearchResult.component
                } else if (legacySearchResult.isAvailable) {
                    return legacySearchResult.component
                } else {
                    return null
                }
            }

        val isAvailable: Boolean
            get() = windowsKitSearchResult.isAvailable || legacySearchResult.isAvailable

        override fun explain(visitor: DiagnosticsVisitor) {
            windowsKitSearchResult.explain(visitor)
        }
    }
}
