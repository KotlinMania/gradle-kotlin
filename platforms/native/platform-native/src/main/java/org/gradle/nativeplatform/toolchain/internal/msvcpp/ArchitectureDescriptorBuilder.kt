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

import org.gradle.nativeplatform.platform.Architecture
import org.gradle.nativeplatform.platform.internal.Architectures
import org.gradle.util.internal.VersionNumber
import java.io.File

enum class ArchitectureDescriptorBuilder(architecture: String, val binPath: String, val libPath: String, asmFilename: String) {
    // See https://blogs.msdn.microsoft.com/vcblog/2016/10/07/compiler-tools-layout-in-visual-studio-15/
    // Host: x64
    // Target: x64
    LEGACY_AMD64_ON_AMD64("amd64", "bin/amd64", "lib/amd64", "ml64.exe"),
    AMD64_ON_AMD64("amd64", "bin/HostX64/x64", "lib/x64", "ml64.exe"),

    // Host: x64
    // Target: x86
    LEGACY_AMD64_ON_X86("amd64", "bin/x86_amd64", "lib/amd64", "ml64.exe") {
        override fun getCrossCompilePath(basePath: File?): File {
            return ArchitectureDescriptorBuilder.LEGACY_X86_ON_X86.getBinPath(basePath)
        }
    },
    AMD64_ON_X86("amd64", "bin/HostX86/x64", "lib/x64", "ml64.exe") {
        override fun getCrossCompilePath(basePath: File?): File {
            return ArchitectureDescriptorBuilder.X86_ON_X86.getBinPath(basePath)
        }
    },

    // Host: x86
    // Target: x64
    LEGACY_X86_ON_AMD64("x86", "bin/amd64_x86", "lib", "ml.exe") {
        override fun getCrossCompilePath(basePath: File?): File {
            return ArchitectureDescriptorBuilder.LEGACY_AMD64_ON_AMD64.getBinPath(basePath)
        }
    },
    X86_ON_AMD64("x86", "bin/HostX64/x86", "lib/x86", "ml.exe") {
        override fun getCrossCompilePath(basePath: File?): File {
            return ArchitectureDescriptorBuilder.AMD64_ON_AMD64.getBinPath(basePath)
        }
    },

    // Host: x86
    // Target: x86
    LEGACY_X86_ON_X86("x86", "bin", "lib", "ml.exe"),
    X86_ON_X86("x86", "bin/HostX86/x86", "lib/x86", "ml.exe"),

    // Host: x64
    // Target: arm
    LEGACY_ARM_ON_AMD64("arm", "bin/amd64_arm", "lib/arm", "armasm.exe") {
        override fun getCrossCompilePath(basePath: File?): File {
            return ArchitectureDescriptorBuilder.LEGACY_AMD64_ON_AMD64.getBinPath(basePath)
        }

        override fun getDefinitions(): MutableMap<String?, String?> {
            val definitions = super.definitions
            definitions.put(ArchitectureDescriptorBuilder.Companion.DEFINE_ARMPARTITIONAVAILABLE, "1")
            return definitions
        }
    },
    ARM_ON_AMD64("arm", "bin/Hostx64/arm", "lib/arm", "armasm.exe") {
        override fun getCrossCompilePath(basePath: File?): File {
            return ArchitectureDescriptorBuilder.AMD64_ON_AMD64.getBinPath(basePath)
        }

        override fun getDefinitions(): MutableMap<String?, String?> {
            val definitions = super.definitions
            definitions.put(ArchitectureDescriptorBuilder.Companion.DEFINE_ARMPARTITIONAVAILABLE, "1")
            return definitions
        }
    },

    ARM64_ON_X86("arm64", "bin/HostX86/arm64", "lib/arm64", "armasm64.exe"),
    ARM64_ON_AMD64("arm64", "bin/Hostx64/arm64", "lib/arm64", "armasm64.exe"),

    // Host: x86
    // Target: arm
    LEGACY_ARM_ON_X86("arm", "bin/x86_arm", "lib/arm", "armasm.exe") {
        override fun getCrossCompilePath(basePath: File?): File {
            return ArchitectureDescriptorBuilder.LEGACY_X86_ON_X86.getBinPath(basePath)
        }

        override fun getDefinitions(): MutableMap<String?, String?> {
            val definitions = super.definitions
            definitions.put(ArchitectureDescriptorBuilder.Companion.DEFINE_ARMPARTITIONAVAILABLE, "1")
            return definitions
        }
    },
    ARM_ON_X86("arm", "bin/HostX86/arm", "lib/arm", "armasm.exe") {
        override fun getCrossCompilePath(basePath: File?): File {
            return ArchitectureDescriptorBuilder.X86_ON_X86.getBinPath(basePath)
        }

        override fun getDefinitions(): MutableMap<String?, String?> {
            val definitions = super.definitions
            definitions.put(ArchitectureDescriptorBuilder.Companion.DEFINE_ARMPARTITIONAVAILABLE, "1")
            return definitions
        }
    },

    // Host: x86
    // Target: ia64
    // (ia64 is no longer supported on later versions of Visual Studio)
    LEGACY_IA64_ON_X86("ia64", "bin/x86_ia64", "lib/ia64", "ias.exe") {
        override fun getCrossCompilePath(basePath: File?): File {
            return ArchitectureDescriptorBuilder.LEGACY_X86_ON_X86.getBinPath(basePath)
        }
    };

    val architecture: Architecture
    val asmFilename: String?

    init {
        this.asmFilename = asmFilename
        this.architecture = Architectures.forInput(architecture)
    }

    fun getBinPath(basePath: File?): File {
        return File(basePath, binPath)
    }

    fun getLibPath(basePath: File?): File {
        return File(basePath, libPath)
    }

    fun getCompilerPath(basePath: File?): File {
        return File(getBinPath(basePath), COMPILER_FILENAME)
    }

    open fun getCrossCompilePath(basePath: File?): File? {
        return null
    }

    open val definitions: MutableMap<String?, String?>
        get() = HashMap<String?, String?>()

    fun buildDescriptor(compilerVersion: VersionNumber?, basePath: File?, vsPath: File?): ArchitectureSpecificVisualCpp {
        val commonTools = File(vsPath, PATH_COMMONTOOLS)
        val commonIde = File(vsPath, PATH_COMMONIDE)
        val paths: MutableList<File?> = ArrayList<File?>(3)
        paths.add(commonTools)
        paths.add(commonIde)
        val crossCompilePath = getCrossCompilePath(basePath)
        if (crossCompilePath != null) {
            paths.add(crossCompilePath)
        }
        val includePath = File(basePath, PATH_INCLUDE)
        return ArchitectureSpecificVisualCpp(
            compilerVersion, paths, getBinPath(basePath), getLibPath(basePath), getCompilerPath(basePath), includePath, asmFilename,
            this.definitions
        )
    }

    companion object {
        private const val PATH_COMMON = "Common7/"
        private val PATH_COMMONTOOLS: String = PATH_COMMON + "Tools/"
        private val PATH_COMMONIDE: String = PATH_COMMON + "IDE/"
        private const val PATH_INCLUDE = "include/"
        private const val DEFINE_ARMPARTITIONAVAILABLE = "_ARM_WINAPI_PARTITION_DESKTOP_SDK_AVAILABLE"
        private const val COMPILER_FILENAME = "cl.exe"
    }
}
