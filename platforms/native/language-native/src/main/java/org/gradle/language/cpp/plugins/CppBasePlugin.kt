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
package org.gradle.language.cpp.plugins

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Transformer
import org.gradle.api.file.Directory
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.language.cpp.CppSharedLibrary
import org.gradle.language.cpp.ProductionCppComponent
import org.gradle.language.cpp.internal.DefaultCppBinary
import org.gradle.language.cpp.internal.DefaultCppComponent
import org.gradle.language.cpp.tasks.CppCompile
import org.gradle.language.plugins.NativeBasePlugin
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.nativeplatform.toolchain.internal.plugins.StandardToolChainsPlugin
import org.gradle.swiftpm.internal.NativeProjectPublication
import org.gradle.swiftpm.internal.SwiftPmTarget
import org.jspecify.annotations.NullMarked
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * A common base plugin for the C++ executable and library plugins
 *
 * @since 4.1
 */
@NullMarked
abstract class CppBasePlugin @Inject constructor(private val publicationRegistry: ProjectPublicationRegistry) : Plugin<Project> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(NativeBasePlugin::class.java)
        project.getPluginManager().apply(StandardToolChainsPlugin::class.java)

        val tasks = project.getTasks()
        val buildDirectory = project.getLayout().getBuildDirectory()

        // Create the tasks for each C++ binary that is registered
        project.getComponents().withType<DefaultCppBinary>(DefaultCppBinary::class.java, Action { binary: DefaultCppBinary ->
            val names = binary.getNames()
            val language = "cpp"

            val compile = tasks.register<CppCompile>(names.getCompileTaskName(language), CppCompile::class.java, Action { task: CppCompile ->
                val systemIncludes = Callable { binary.getPlatformToolProvider().getSystemLibraries(ToolType.CPP_COMPILER).getIncludeDirs() }
                task.includes(binary.getCompileIncludePath())
                task.getSystemIncludes().from(systemIncludes)
                task.source(binary.getCppSource())
                if (binary.isDebuggable()) {
                    task.setDebuggable(true)
                }
                if (binary.isOptimized()) {
                    task.setOptimized(true)
                }
                task.getTargetPlatform().set(binary.getNativePlatform())
                task.getToolChain().set(binary.getToolChain())
                task.getObjectFileDir().set(buildDirectory.dir("obj/" + names.getDirName()))
                if (binary is CppSharedLibrary) {
                    task.setPositionIndependentCode(true)
                }
            })

            binary.getObjectsDir().set(compile.flatMap<Directory>(Transformer { task: CppCompile? -> task!!.getObjectFileDir() }))
            binary.getCompileTask().set(compile)
        })

        project.getComponents().withType<ProductionCppComponent>(ProductionCppComponent::class.java, Action { component: ProductionCppComponent ->
            project.afterEvaluate(Action { p: Project? ->
                val componentInternal = component as DefaultCppComponent
                val projectIdentity = (project as ProjectInternal).getProjectIdentity()
                publicationRegistry.registerPublication(projectIdentity, NativeProjectPublication(componentInternal.getDisplayName(), SwiftPmTarget(component.getBaseName().get())))
            })
        })
    }
}
