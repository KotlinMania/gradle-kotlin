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
package org.gradle.nativeplatform.internal.configure

import org.gradle.language.base.internal.ProjectLayout
import org.gradle.model.Defaults
import org.gradle.model.RuleSource
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.StaticLibraryBinarySpec
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainRegistryInternal
import java.io.File

object NativeBinaryRules : RuleSource() {
    @Defaults
    fun assignTools(nativeBinarySpec: NativeBinarySpec?, toolChains: NativeToolChainRegistryInternal, projectLayout: ProjectLayout) {
        assignTools(nativeBinarySpec, toolChains, projectLayout.buildDir)
    }

    fun assignTools(nativeBinarySpec: NativeBinarySpec?, toolChains: NativeToolChainRegistryInternal, buildDir: File?) {
        val nativeBinary = nativeBinarySpec as NativeBinarySpecInternal
        assignToolsToNativeBinary(nativeBinary, nativeBinarySpec, toolChains)
        assignToolsToNativeBinaryExtension(nativeBinary, buildDir)
    }

    private fun assignToolsToNativeBinary(nativeBinary: NativeBinarySpecInternal, nativeBinarySpec: NativeBinarySpec, toolChains: NativeToolChainRegistryInternal) {
        val toolChain = toolChainFor(nativeBinarySpec, toolChains)
        val toolProvider = toolChain.select(nativeBinarySpec.getTargetPlatform() as NativePlatformInternal?)
        nativeBinary.setToolChain(toolChain)
        nativeBinary.setPlatformToolProvider(toolProvider)
    }

    private fun assignToolsToNativeBinaryExtension(nativeBinary: NativeBinarySpecInternal?, buildDir: File?) {
        if (nativeBinary is NativeExecutableBinarySpec) {
            assignToolsToNativeExecutableBinary(nativeBinary, buildDir)
        } else if (nativeBinary is SharedLibraryBinarySpec) {
            assignToolsToSharedLibraryBinary(nativeBinary, buildDir)
        } else if (nativeBinary is StaticLibraryBinarySpec) {
            assignToolsToStaticLibraryBinary(buildDir, nativeBinary)
        }
    }

    private fun assignToolsToNativeExecutableBinary(nativeBinary: NativeBinarySpecInternal?, buildDir: File?) {
        val nativeExecutable = nativeBinary as NativeExecutableBinarySpec
        val executable = nativeExecutable.getExecutable()
        executable.setFile(executableFileFor(nativeBinary, buildDir))
        executable.setToolChain(nativeBinary.getToolChain())
        nativeExecutable.getInstallation().setDirectory(installationDirFor(nativeBinary, buildDir))
    }

    private fun assignToolsToSharedLibraryBinary(nativeBinary: NativeBinarySpecInternal?, buildDir: File?) {
        val sharedLibrary = nativeBinary as SharedLibraryBinarySpec
        sharedLibrary.setSharedLibraryFile(sharedLibraryFileFor(nativeBinary, buildDir))
        sharedLibrary.setSharedLibraryLinkFile(sharedLibraryLinkFileFor(nativeBinary, buildDir))
    }

    private fun assignToolsToStaticLibraryBinary(buildDir: File?, nativeBinary: NativeBinarySpecInternal?) {
        val staticLibrary = nativeBinary as StaticLibraryBinarySpec
        staticLibrary.setStaticLibraryFile(staticLibraryFileFor(nativeBinary, buildDir))
    }

    fun executableFileFor(nativeBinary: NativeBinarySpecInternal, buildDir: File?): File {
        return File(nativeBinary.getNamingScheme().getOutputDirectory(buildDir, "exe"), executableNameFor(nativeBinary))
    }

    private fun sharedLibraryLinkFileFor(nativeBinary: NativeBinarySpecInternal, buildDir: File?): File {
        return File(nativeBinary.getNamingScheme().getOutputDirectory(buildDir, "libs"), sharedLibraryLinkFileNameFor(nativeBinary))
    }

    private fun sharedLibraryFileFor(nativeBinary: NativeBinarySpecInternal, buildDir: File?): File {
        return File(nativeBinary.getNamingScheme().getOutputDirectory(buildDir, "libs"), sharedLibraryNameFor(nativeBinary))
    }

    private fun staticLibraryFileFor(nativeBinary: NativeBinarySpecInternal, buildDir: File?): File {
        return File(nativeBinary.getNamingScheme().getOutputDirectory(buildDir, "libs"), staticLibraryNameFor(nativeBinary))
    }

    fun installationDirFor(nativeBinary: NativeBinarySpecInternal, buildDir: File?): File? {
        return nativeBinary.getNamingScheme().getOutputDirectory(buildDir, "install")
    }

    private fun executableNameFor(nativeBinary: NativeBinarySpecInternal): String? {
        return nativeBinary.getPlatformToolProvider().getExecutableName(baseNameOf(nativeBinary))
    }

    private fun sharedLibraryLinkFileNameFor(nativeBinary: NativeBinarySpecInternal): String? {
        return nativeBinary.getPlatformToolProvider().getSharedLibraryLinkFileName(baseNameOf(nativeBinary))
    }

    private fun sharedLibraryNameFor(nativeBinary: NativeBinarySpecInternal): String? {
        return nativeBinary.getPlatformToolProvider().getSharedLibraryName(baseNameOf(nativeBinary))
    }

    private fun staticLibraryNameFor(nativeBinary: NativeBinarySpecInternal): String? {
        return nativeBinary.getPlatformToolProvider().getStaticLibraryName(baseNameOf(nativeBinary))
    }

    private fun baseNameOf(nativeBinary: NativeBinarySpec): String? {
        return nativeBinary.getComponent().getBaseName()
    }

    private fun toolChainFor(nativeBinary: NativeBinarySpec, toolChains: NativeToolChainRegistryInternal): NativeToolChainInternal {
        return toolChains.getForPlatform(nativeBinary.getTargetPlatform()) as NativeToolChainInternal
    }
}
