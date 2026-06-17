/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.platform.internal

import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import net.rubygrapefruit.platform.SystemInfo
import org.gradle.api.GradleException
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.platform.Architecture
import org.gradle.platform.BuildPlatform
import org.gradle.platform.BuildPlatformFactory
import javax.inject.Inject

/**
 * Information about the machine host Gradle is running on.
 */
@ServiceScope(Scope.Global::class)
class CurrentBuildPlatform @Inject constructor(systemInfo: SystemInfo, operatingSystem: OperatingSystem) {
    private val architecture: Supplier<Architecture>

    @JvmField
    val operatingSystem: org.gradle.platform.OperatingSystem

    init {
        this.architecture = Suppliers.memoize<Architecture>(object : Supplier<Architecture> {
            override fun get(): Architecture {
                return getArchitecture(systemInfo)
            }
        })
        this.operatingSystem = getOperatingSystem(operatingSystem)
    }

    fun getArchitecture(): Architecture {
        return architecture.get()
    }

    fun toBuildPlatform(): BuildPlatform {
        return BuildPlatformFactory.of(getArchitecture(), this.operatingSystem)
    }

    companion object {
        private fun getArchitecture(systemInfo: SystemInfo): Architecture {
            val architecture = systemInfo.getArchitecture()
            when (architecture) {
                SystemInfo.Architecture.i386 -> return Architecture.X86
                SystemInfo.Architecture.amd64 -> return Architecture.X86_64
                SystemInfo.Architecture.aarch64 -> return Architecture.AARCH64
            }
            throw GradleException("Unhandled system architecture: " + architecture)
        }

        fun getOperatingSystem(operatingSystem: OperatingSystem): org.gradle.platform.OperatingSystem {
            if (OperatingSystem.LINUX === operatingSystem) {
                return org.gradle.platform.OperatingSystem.LINUX
            } else if (OperatingSystem.UNIX === operatingSystem) {
                return org.gradle.platform.OperatingSystem.UNIX
            } else if (OperatingSystem.WINDOWS === operatingSystem) {
                return org.gradle.platform.OperatingSystem.WINDOWS
            } else if (OperatingSystem.MAC_OS === operatingSystem) {
                return org.gradle.platform.OperatingSystem.MAC_OS
            } else if (OperatingSystem.SOLARIS === operatingSystem) {
                return org.gradle.platform.OperatingSystem.SOLARIS
            } else if (OperatingSystem.FREE_BSD === operatingSystem) {
                return org.gradle.platform.OperatingSystem.FREE_BSD
            } else {
                throw GradleException("Unhandled operating system: " + operatingSystem.getName())
            }
        }
    }
}
