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

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Transformer
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.CompositeFileCollection
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.specs.Spec
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.nativeplatform.HeaderExportingSourceSet
import org.gradle.language.rc.WindowsResourceSet
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeExecutableBinarySpec
import org.gradle.nativeplatform.PreprocessingTool
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.StaticLibraryBinarySpec
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.test.NativeTestSuiteBinarySpec
import org.gradle.nativeplatform.toolchain.internal.MacroArgsConverter
import org.gradle.util.internal.CollectionUtils
import org.gradle.util.internal.VersionNumber
import java.io.File
import java.util.function.Consumer

class NativeSpecVisualStudioTargetBinary(binary: NativeBinarySpec?) : VisualStudioTargetBinary {
    private val binary: NativeBinarySpecInternal

    init {
        this.binary = binary as NativeBinarySpecInternal
    }

    val projectPath: String?
        get() = binary.getProjectPath()

    val componentName: String?
        get() = binary.getComponent()!!.getName()

    val visualStudioProjectName: String?
        get() = Companion.projectPrefix(projectPath!!) + componentName + projectType!!.suffix

    val visualStudioConfigurationName: String
        get() = Companion.makeName(variantDimensions!!)

    val visualStudioVersion: VersionNumber
        get() =// Assume VS 2015
            VersionNumber.parse("14.0")

    val sdkVersion: VersionNumber
        get() =// Assume 8.1
            VersionNumber.parse("8.1")

    val sourceFiles: FileCollection
        get() {
            val filter: Spec<LanguageSourceSet?> = object : Spec<LanguageSourceSet?> {
                override fun isSatisfiedBy(sourceSet: LanguageSourceSet?): Boolean {
                    return sourceSet !is WindowsResourceSet
                }
            }
            val transform: Transformer<FileCollection?, LanguageSourceSet?> =
                object : Transformer<FileCollection?, LanguageSourceSet?> {
                    override fun transform(sourceSet: LanguageSourceSet): FileCollection? {
                        return sourceSet.getSource()
                    }
                }

            return NativeSpecVisualStudioTargetBinary.LanguageSourceSetCollectionAdapter(componentName + " source files", binary.getInputs(), filter, transform)
        }

    val resourceFiles: FileCollection
        get() {
            val filter: Spec<LanguageSourceSet?> = object : Spec<LanguageSourceSet?> {
                override fun isSatisfiedBy(sourceSet: LanguageSourceSet?): Boolean {
                    return sourceSet is WindowsResourceSet
                }
            }
            val transform: Transformer<FileCollection?, LanguageSourceSet?> =
                object : Transformer<FileCollection?, LanguageSourceSet?> {
                    override fun transform(sourceSet: LanguageSourceSet): FileCollection? {
                        return sourceSet.getSource()
                    }
                }

            return NativeSpecVisualStudioTargetBinary.LanguageSourceSetCollectionAdapter(componentName + " resource files", binary.getInputs(), filter, transform)
        }

    val headerFiles: FileCollection
        get() {
            val filter: Spec<LanguageSourceSet?> = object : Spec<LanguageSourceSet?> {
                override fun isSatisfiedBy(sourceSet: LanguageSourceSet?): Boolean {
                    return sourceSet is HeaderExportingSourceSet
                }
            }
            val transform: Transformer<FileCollection?, LanguageSourceSet?> =
                object : Transformer<FileCollection?, LanguageSourceSet?> {
                    override fun transform(sourceSet: LanguageSourceSet?): FileCollection {
                        val exportingSourceSet = sourceSet as HeaderExportingSourceSet
                        return exportingSourceSet.exportedHeaders.plus(exportingSourceSet.implicitHeaders)
                    }
                }

            return NativeSpecVisualStudioTargetBinary.LanguageSourceSetCollectionAdapter(componentName + " header files", binary.getInputs(), filter, transform)
        }

    val isExecutable: Boolean
        get() = binary is NativeExecutableBinarySpec || binary is NativeTestSuiteBinarySpec

    val projectType: VisualStudioTargetBinary.ProjectType?
        get() = if (binary is SharedLibraryBinarySpec)
            VisualStudioTargetBinary.ProjectType.DLL
        else
            if (binary is StaticLibraryBinarySpec)
                VisualStudioTargetBinary.ProjectType.LIB
            else
                if (binary is NativeExecutableBinarySpec)
                    VisualStudioTargetBinary.ProjectType.EXE
                else
                    if (binary is NativeTestSuiteBinarySpec)
                        VisualStudioTargetBinary.ProjectType.EXE
                    else
                        VisualStudioTargetBinary.ProjectType.NONE

    val variantDimensions: MutableList<String?>?
        get() {
            val dimensions = binary.getNamingScheme().getVariantDimensions()
            if (dimensions.isEmpty()) {
                return mutableListOf<String?>(binary.getBuildType().getName())
            } else {
                return dimensions
            }
        }

    private val installTask: InstallExecutable?
        get() {
            val installTasks =
                binary.getTasks().withType<InstallExecutable?>(InstallExecutable::class.java)
            return if (installTasks.isEmpty()) null else installTasks.iterator().next()
        }

    val buildTaskPath: String?
        get() {
            if (isExecutable) {
                return this.installTask.getPath()
            } else {
                return binary.getTasks().getBuild().getPath()
            }
        }

    val cleanTaskPath: String
        get() = taskPath("clean")

    private fun taskPath(taskName: String): String {
        val projectPath = binary.getComponent()!!.getProjectPath()
        if (":" == projectPath) {
            return ":" + taskName
        }

        return projectPath + ":" + taskName
    }

    val isDebuggable: Boolean
        get() = "release" != binary.getBuildType().getName()

    val outputFile: File
        get() {
            if (isExecutable) {
                val installTask = this.installTask
                return File(installTask!!.installDirectory.get().getAsFile(), "lib/" + installTask.executableFile.get().getAsFile().getName())
            } else {
                return binary.getPrimaryOutput()
            }
        }

    val compilerDefines: MutableList<String?>
        get() {
            val defines: MutableList<String?> = ArrayList<String?>()
            defines.addAll(getDefines("cCompiler"))
            defines.addAll(getDefines("cppCompiler"))
            defines.addAll(getDefines("rcCompiler"))
            return defines
        }

    val languageStandard: VisualStudioTargetBinary.LanguageStandard
        get() = VisualStudioTargetBinary.LanguageStandard.from(binary.getCppCompiler().getArgs())

    private fun getDefines(tool: String?): MutableList<String?> {
        val rcCompiler = findCompiler(tool)
        return if (rcCompiler == null) ArrayList<String?>() else MacroArgsConverter().transform(rcCompiler.getMacros())
    }

    private fun findCompiler(tool: String?): PreprocessingTool? {
        return binary.getToolByName(tool) as PreprocessingTool?
    }

    val includePaths: MutableSet<File?>
        get() {
            val includes: MutableSet<File?> = LinkedHashSet<File?>()

            for (sourceSet in binary.getInputs()) {
                if (sourceSet is HeaderExportingSourceSet) {
                    includes.addAll(sourceSet.exportedHeaders.getSrcDirs())
                }
            }

            for (lib in binary.getLibs()) {
                includes.addAll(lib.getIncludeRoots().getFiles())
            }

            return includes
        }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as NativeSpecVisualStudioTargetBinary

        return binary == that.binary
    }

    override fun hashCode(): Int {
        return binary.hashCode()
    }

    private class LanguageSourceSetCollectionAdapter(
        private val displayName: String,
        private val inputs: MutableSet<LanguageSourceSet?>,
        private val filterSpec: Spec<LanguageSourceSet?>,
        private val transformer: Transformer<FileCollection?, LanguageSourceSet?>
    ) : CompositeFileCollection() {
        override fun visitDependencies(context: TaskDependencyResolveContext) {
            for (input in inputs) {
                context.add(input)
            }
        }

        override fun visitChildren(visitor: Consumer<FileCollectionInternal?>) {
            val filtered: MutableSet<LanguageSourceSet?> = CollectionUtils.filter<LanguageSourceSet?>(inputs, filterSpec)
            for (languageSourceSet in filtered) {
                visitor.accept(transformer.transform(languageSourceSet) as FileCollectionInternal?)
            }
        }

        override fun getDisplayName(): String {
            return displayName
        }
    }

    companion object {
        fun projectPrefix(projectPath: String): String {
            if (":" == projectPath) {
                return ""
            }
            return projectPath.substring(1).replace(":", "_") + "_"
        }

        private fun makeName(components: Iterable<String?>): String {
            val builder = StringBuilder()
            for (component in components) {
                if (component != null && component.length > 0) {
                    if (builder.length == 0) {
                        builder.append(component)
                    } else {
                        builder.append(StringUtils.capitalize(component))
                    }
                }
            }
            return builder.toString()
        }
    }
}
