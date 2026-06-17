/*
 * Copyright 2014 the original author or authors.
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

class NativePlatforms {
    fun defaultPlatformDefinitions(): MutableSet<DefaultNativePlatform?> {
        val platforms: MutableSet<DefaultNativePlatform?> = LinkedHashSet<DefaultNativePlatform?>()

        val windows: OperatingSystemInternal = DefaultOperatingSystem(OS_WINDOWS)
        val linux: OperatingSystemInternal = DefaultOperatingSystem(OS_LINUX)
        val osx: OperatingSystemInternal = DefaultOperatingSystem(OS_OSX)
        val unix: OperatingSystemInternal = DefaultOperatingSystem(OS_UNIX)
        val freebsd: OperatingSystemInternal = DefaultOperatingSystem("freebsd")
        val solaris: OperatingSystemInternal = DefaultOperatingSystem("solaris")

        val x86 = Architectures.forInput(ARCH_X86)
        val x64 = Architectures.forInput("x86_64")
        val ia64 = Architectures.forInput("ia64")
        val armv7 = Architectures.forInput("armv7")
        val aarch64 = Architectures.forInput("aarch64")
        val sparc = Architectures.forInput("sparc")
        val ultrasparc = Architectures.forInput("ultrasparc")
        val ppc = Architectures.forInput("ppc")
        val ppc64 = Architectures.forInput("ppc64")
        val e2k = Architectures.forInput("e2k")

        platforms.add(createPlatform(windows, x86))
        platforms.add(createPlatform(windows, x64))
        platforms.add(createPlatform(windows, armv7))
        platforms.add(createPlatform(windows, ia64))

        platforms.add(createPlatform(freebsd, x86))
        platforms.add(createPlatform(freebsd, x64))
        platforms.add(createPlatform(freebsd, armv7))
        platforms.add(createPlatform(freebsd, aarch64))
        platforms.add(createPlatform(freebsd, ppc))
        platforms.add(createPlatform(freebsd, ppc64))

        platforms.add(createPlatform(unix, x86))
        platforms.add(createPlatform(unix, x64))
        platforms.add(createPlatform(unix, armv7))
        platforms.add(createPlatform(unix, aarch64))
        platforms.add(createPlatform(unix, ppc))
        platforms.add(createPlatform(unix, ppc64))

        platforms.add(createPlatform(linux, x64))
        platforms.add(createPlatform(linux, x86))
        platforms.add(createPlatform(linux, armv7))
        platforms.add(createPlatform(linux, aarch64))
        platforms.add(createPlatform(linux, e2k))

        platforms.add(createPlatform(osx, x86))
        platforms.add(createPlatform(osx, x64))
        platforms.add(createPlatform(osx, aarch64))

        platforms.add(createPlatform(solaris, x64))
        platforms.add(createPlatform(solaris, x86))
        platforms.add(createPlatform(solaris, sparc))
        platforms.add(createPlatform(solaris, ultrasparc))

        return platforms
    }

    val defaultPlatformName: String
        get() {
            val defaultPlatform: NativePlatformInternal = DefaultNativePlatform("default")
            val os: OperatingSystemInternal = defaultPlatform.getOperatingSystem()
            val architecture: ArchitectureInternal = defaultPlatform.getArchitecture()

            if (os.isWindows) {
                // Always use x86 as default on windows
                return platformName(
                    OS_WINDOWS,
                    ARCH_X86
                )
            }
            if (os.isLinux) {
                return platformName(
                    OS_LINUX,
                    architecture.getName()
                )
            }
            if (os.isMacOsX) {
                return platformName(
                    OS_OSX,
                    architecture.getName()
                )
            }
            return platformName(
                OS_UNIX,
                ARCH_X86
            )
        }

    companion object {
        private const val OS_WINDOWS = "windows"
        private const val OS_LINUX = "linux"
        private const val OS_OSX = "osx"
        private const val OS_UNIX = "unix"
        private const val ARCH_X86 = "x86"

        private fun createPlatform(os: OperatingSystemInternal, arch: ArchitectureInternal): DefaultNativePlatform {
            return DefaultNativePlatform(platformName(os.getName(), arch.getName()), os, arch)
        }

        private fun platformName(os: String?, arch: String?): String {
            return os + "_" + arch
        }
    }
}
