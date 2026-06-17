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
package org.gradle.language.nativeplatform.internal

import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.base.internal.LanguageSourceSetInternal
import org.gradle.language.base.internal.SourceTransformTaskConfig
import org.gradle.language.nativeplatform.DependentSourceSet
import org.gradle.language.nativeplatform.HeaderExportingSourceSet
import org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.nativeplatform.PreprocessingTool
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.internal.NativeBinarySpecInternal
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.platform.base.BinarySpec
import org.gradle.util.internal.CollectionUtils.collect
import java.io.File
import java.util.concurrent.Callable
import java.util.function.Function

abstract class CompileTaskConfig(private val languageTransform: NativeLanguageTransform<*>, val taskType: Class<out DefaultTask?>?) : SourceTransformTaskConfig {
    val taskPrefix: String?
        get() = "compile"

    override fun configureTask(task: Task?, binary: BinarySpec?, sourceSet: LanguageSourceSet?, serviceRegistry: ServiceRegistry?) {
        configureCompileTaskCommon(
            (task as org.gradle.language.nativeplatform.tasks.AbstractNativeCompileTask?)!!,
            (binary as org.gradle.nativeplatform.internal.NativeBinarySpecInternal?)!!,
            (sourceSet as org.gradle.language.base.internal.LanguageSourceSetInternal?)!!
        )
        configureCompileTask(task as AbstractNativeCompileTask, binary as NativeBinarySpecInternal, sourceSet)
    }

    private fun configureCompileTaskCommon(task: AbstractNativeCompileTask, binary: NativeBinarySpecInternal, sourceSet: LanguageSourceSetInternal) {
        task.toolChain.set(binary.getToolChain())
        task.targetPlatform.set(binary.getTargetPlatform())
        task.isPositionIndependentCode = binary is SharedLibraryBinarySpec

        task.includes((sourceSet as HeaderExportingSourceSet).getExportedHeaders().getSourceDirectories())
        task.includes(object : Callable<MutableList<FileCollection?>?> {
            override fun call(): MutableList<FileCollection?> {
                val libs = binary.getLibs(sourceSet as DependentSourceSet)
                return collect<FileCollection?, NativeDependencySet?>(libs, Function { obj: NativeDependencySet? -> obj!!.getIncludeRoots() })
            }
        })
        val fileCollectionFactory = (task.getProject() as ProjectInternal).getServices().get<FileCollectionFactory?>(FileCollectionFactory::class.java)
        task.systemIncludes!!.from(fileCollectionFactory!!.create(object : MinimalFileSet {
            override fun getFiles(): MutableSet<File?> {
                val platformToolProvider = (binary.getToolChain() as NativeToolChainInternal).select(binary.getTargetPlatform() as NativePlatformInternal?)
                val toolType = languageTransform.getToolType()
                return LinkedHashSet<File?>(platformToolProvider!!.getSystemLibraries(toolType)!!.includeDirs)
            }

            override fun getDisplayName(): String {
                return "System includes for " + binary.getToolChain().displayName
            }
        }))

        for (toolName in languageTransform.binaryTools!!.keys) {
            val tool = binary.getToolByName(toolName)
            if (tool is PreprocessingTool) {
                task.setMacros(tool.getMacros())
            }

            task.compilerArgs.set(tool.getArgs())
        }
    }

    abstract fun configureCompileTask(task: AbstractNativeCompileTask?, binary: NativeBinarySpecInternal?, sourceSet: LanguageSourceSetInternal?)
}
