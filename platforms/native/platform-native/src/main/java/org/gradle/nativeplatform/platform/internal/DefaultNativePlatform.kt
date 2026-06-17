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
package org.gradle.nativeplatform.platform.internal

import net.rubygrapefruit.platform.SystemInfo
import org.gradle.internal.nativeintegration.NativeIntegrationUnavailableException
import org.gradle.internal.nativeintegration.services.NativeServices.Companion.getInstance
import org.gradle.internal.os.OperatingSystem.Companion.current

open class DefaultNativePlatform @JvmOverloads constructor(
    private val name: String?,
    @JvmField var operatingSystem: OperatingSystemInternal? = currentOperatingSystem,
    @JvmField var architecture: ArchitectureInternal? = currentArchitecture
) : NativePlatformInternal {
    override fun getName(): String? {
        return name
    }

    override fun toString(): String {
        return getDisplayName()
    }

    override fun getDisplayName(): String {
        return String.format("platform '%s'", name)
    }

    override fun architecture(name: String?) {
        architecture = Architectures.forInput(name)
    }

    override fun operatingSystem(name: String) {
        operatingSystem = DefaultOperatingSystem(name)
    }

    open fun withArchitecture(architecture: ArchitectureInternal?): DefaultNativePlatform {
        return DefaultNativePlatform(name, operatingSystem, architecture)
    }

    private class HostPlatform : DefaultNativePlatform {
        internal constructor() : super(
            "host:" + currentArchitecture.getName(),
            currentOperatingSystem,
            currentArchitecture
        )

        internal constructor(architecture: ArchitectureInternal) : super(
            "host:" + architecture.getName(),
            currentOperatingSystem, architecture
        )

        override fun getDisplayName(): String {
            return String.format("host %s %s", operatingSystem, architecture)
        }

        override fun withArchitecture(architecture: ArchitectureInternal): DefaultNativePlatform {
            return HostPlatform(architecture)
        }
    }

    companion object {
        val currentOperatingSystem: DefaultOperatingSystem
            get() = DefaultOperatingSystem(System.getProperty("os.name"), current()!!)

        @JvmStatic
        val currentArchitecture: ArchitectureInternal
            get() {
                var architectureName: String?
                try {
                    architectureName =
                        getInstance().get<SystemInfo?>(SystemInfo::class.java)!!
                            .getArchitectureName()
                } catch (e: NativeIntegrationUnavailableException) {
                    architectureName = System.getProperty("os.arch")
                }
                return Architectures.forInput(architectureName)
            }

        @JvmStatic
        fun host(): DefaultNativePlatform {
            return HostPlatform()
        }
    }
}
