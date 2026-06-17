/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.ide.visualstudio.internal

import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.language.cpp.CppBinary
import org.gradle.language.cpp.CppComponent
import org.gradle.language.cpp.internal.DefaultCppBinary
import org.gradle.language.nativeplatform.internal.Dimensions
import org.gradle.nativeplatform.MachineArchitecture
import org.gradle.nativeplatform.OperatingSystemFamily
import org.gradle.nativeplatform.TargetMachine
import org.gradle.nativeplatform.toolchain.internal.MacroArgsConverter
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.msvcpp.WindowsSdkLibraries
import org.gradle.nativeplatform.toolchain.internal.msvcpp.metadata.VisualCppMetadata
import org.gradle.util.internal.VersionNumber
import java.io.File
import java.util.stream.Collectors

abstract class AbstractCppBinaryVisualStudioTargetBinary protected constructor(
    protected val projectName: String?,
    private val projectPath: String?,
    private val component: CppComponent,
    private val projectLayout: ProjectLayout
) : VisualStudioTargetBinary {
    override fun getLanguageStandard(): VisualStudioTargetBinary.LanguageStandard? {
        return VisualStudioTargetBinary.LanguageStandard.Companion.from(this.binary.compileTask.get().compilerArgs.get())
    }

    abstract val binary: CppBinary?

    override fun getProjectPath(): String? {
        return projectPath
    }

    override fun getComponentName(): String? {
        return projectName
    }

    override fun getVisualStudioProjectName(): String? {
        return projectName + getProjectType().getSuffix()
    }

    override fun getVisualStudioConfigurationName(): String {
        // TODO: this is terrible
        var buildType = "debug"
        if (this.binary.isOptimized) {
            buildType = "release"
        }

        val operatingSystemFamilySuffix = Dimensions.createDimensionSuffix(
            this.binary.targetMachine.getOperatingSystemFamily(),
            component.getBinaries().get().stream().map<TargetMachine?> { obj: CppBinary? -> obj!!.targetMachine }
                .map<OperatingSystemFamily?> { obj: TargetMachine? -> obj!!.operatingSystemFamily }.collect(
                    Collectors.toSet()
                )
        )
        val architectureSuffix = Dimensions.createDimensionSuffix(
            this.binary.targetMachine.getArchitecture(),
            component.getBinaries().get().stream().map<TargetMachine?> { obj: CppBinary? -> obj!!.targetMachine }.map<MachineArchitecture?> { obj: TargetMachine? -> obj!!.architecture }
                .collect(
                    Collectors.toSet()
                ))

        return buildType + operatingSystemFamilySuffix + architectureSuffix
    }

    protected fun taskPath(taskName: String): String {
        if (":" == projectPath) {
            return ":" + taskName
        }

        return projectPath + ":" + taskName
    }

    override fun getVisualStudioVersion(): VersionNumber? {
        val provider = (this.binary as DefaultCppBinary).platformToolProvider
        if (provider.isAvailable) {
            val compilerMetadata = provider.getCompilerMetadata(ToolType.CPP_COMPILER)
            if (compilerMetadata is VisualCppMetadata) {
                return compilerMetadata.visualStudioVersion
            }
        }

        // Assume VS 2015
        return DEFAULT_VISUAL_STUDIO_VERSION
    }

    override fun getSdkVersion(): VersionNumber? {
        val provider = (this.binary as DefaultCppBinary).platformToolProvider
        if (provider.isAvailable) {
            val systemLibraries = provider.getSystemLibraries(ToolType.CPP_COMPILER)
            if (systemLibraries is WindowsSdkLibraries) {
                val sdkLibraries = systemLibraries
                return sdkLibraries.sdkVersion
            }
        }

        // Assume 8.1
        return DEFAULT_SDK_VERSION
    }

    override fun getVariantDimensions(): MutableList<String?> {
        return mutableListOf<String?>(this.binary.getName())
    }

    override fun getSourceFiles(): FileCollection? {
        return this.binary.cppSource
    }

    override fun getResourceFiles(): FileCollection {
        return projectLayout.files()
    }

    override fun getHeaderFiles(): FileCollection? {
        return component.headerFiles
    }

    override fun getCleanTaskPath(): String {
        return taskPath("clean")
    }

    override fun isDebuggable(): Boolean {
        return this.binary.isDebuggable
    }

    override fun getCompilerDefines(): MutableList<String?>? {
        return MacroArgsConverter().transform(this.binary.compileTask.get().getMacros())
    }

    override fun getIncludePaths(): MutableSet<File?> {
        return this.binary.compileIncludePath.getFiles()
    }

    companion object {
        val DEFAULT_SDK_VERSION: VersionNumber? = VersionNumber.parse("8.1")
        val DEFAULT_VISUAL_STUDIO_VERSION: VersionNumber = VersionNumber.version(14)
    }
}
