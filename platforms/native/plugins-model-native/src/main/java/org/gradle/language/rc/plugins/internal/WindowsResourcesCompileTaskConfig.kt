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
package org.gradle.language.rc.plugins.internal

import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.LanguageSourceSetInternal
import org.gradle.language.base.internal.SourceTransformTaskConfig
import org.gradle.language.rc.WindowsResourceSet
import org.gradle.language.rc.tasks.WindowsResourceCompile
import org.gradle.nativeplatform.PreprocessingTool
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.nativeplatform.internal.StaticLibraryBinarySpecInternal
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.platform.base.BinarySpec
import java.io.File

class WindowsResourcesCompileTaskConfig : SourceTransformTaskConfig {
    val taskPrefix: String?
        get() = "compile"

    val taskType: Class<out DefaultTask?>?
        get() = WindowsResourceCompile::class.java

    override fun configureTask(task: Task?, binary: BinarySpec?, sourceSet: LanguageSourceSet?, serviceRegistry: ServiceRegistry?) {
        configureResourceCompileTask(
            (task as org.gradle.language.rc.tasks.WindowsResourceCompile?)!!,
            (binary as org.gradle.nativeplatform.internal.NativeBinarySpecInternal?)!!,
            (sourceSet as org.gradle.language.rc.WindowsResourceSet?)!!
        )
    }

    private fun configureResourceCompileTask(task: WindowsResourceCompile, binary: NativeBinarySpecInternal, sourceSet: WindowsResourceSet) {
        task.setDescription("Compiles resources of the " + sourceSet + " of " + binary)

        task.toolChain.set(binary.getToolChain())
        task.targetPlatform.set(binary.getTargetPlatform())

        task.includes(sourceSet.exportedHeaders.getSourceDirectories())

        val fileCollectionFactory = (task.getProject() as ProjectInternal).getServices().get<FileCollectionFactory?>(FileCollectionFactory::class.java)
        task.includes(fileCollectionFactory!!.create(object : MinimalFileSet {
            override fun getFiles(): MutableSet<File?> {
                val platformToolProvider = (binary.getToolChain() as NativeToolChainInternal).select(binary.getTargetPlatform() as NativePlatformInternal?)
                return LinkedHashSet<File?>(platformToolProvider!!.getSystemLibraries(ToolType.WINDOW_RESOURCES_COMPILER)!!.includeDirs)
            }

            override fun getDisplayName(): String {
                return "System includes for " + binary.getToolChain().displayName
            }
        }))

        task.source(sourceSet.source)

        val project = task.getProject()

        task.outputDir = project.getLayout().getBuildDirectory().getAsFile()
            .map<File?>(Transformer { it: File? -> File(binary.namingScheme.getOutputDirectory(it, "objs"), (sourceSet as LanguageSourceSetInternal).projectScopedName) }).get()

        val rcCompiler = binary.getToolByName("rcCompiler") as PreprocessingTool
        task.macros = rcCompiler.getMacros()
        task.compilerArgs.set(rcCompiler.getArgs())

        val resourceOutputs = task.getOutputs().getFiles().getAsFileTree().matching(PatternSet().include("**/*.res"))
        binary.binaryInputs(resourceOutputs)
        if (binary is StaticLibraryBinarySpecInternal) {
            binary.additionalLinkFiles(resourceOutputs)
        }
    }
}
