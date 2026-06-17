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
package org.gradle.ide.visualstudio.plugins

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.gradle.ide.visualstudio.VisualStudioExtension
import org.gradle.ide.visualstudio.VisualStudioProject
import org.gradle.ide.visualstudio.VisualStudioRootExtension
import org.gradle.ide.visualstudio.VisualStudioSolution
import org.gradle.ide.visualstudio.internal.CppApplicationVisualStudioTargetBinary
import org.gradle.ide.visualstudio.internal.CppSharedLibraryVisualStudioTargetBinary
import org.gradle.ide.visualstudio.internal.CppStaticLibraryVisualStudioTargetBinary
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioExtension
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioProject
import org.gradle.ide.visualstudio.internal.DefaultVisualStudioRootExtension
import org.gradle.ide.visualstudio.internal.VisualStudioExtensionInternal
import org.gradle.ide.visualstudio.internal.VisualStudioProjectInternal
import org.gradle.ide.visualstudio.internal.VisualStudioSolutionInternal
import org.gradle.ide.visualstudio.internal.VisualStudioTargetBinary
import org.gradle.ide.visualstudio.tasks.GenerateFiltersFileTask
import org.gradle.ide.visualstudio.tasks.GenerateProjectFileTask
import org.gradle.ide.visualstudio.tasks.GenerateSolutionFileTask
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.reflect.Instantiator
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.cpp.CppApplication
import org.gradle.language.cpp.CppExecutable
import org.gradle.language.cpp.CppLibrary
import org.gradle.language.cpp.CppSharedLibrary
import org.gradle.language.cpp.CppStaticLibrary
import org.gradle.nativeplatform.Linkage
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.plugins.ide.internal.IdePlugin
import javax.inject.Inject

/**
 * A plugin for creating a Visual Studio solution for a gradle project.
 */
abstract class VisualStudioPlugin @Inject constructor(
    private val instantiator: Instantiator,
    private val fileResolver: FileResolver?,
    private val artifactRegistry: IdeArtifactRegistry?,
    private val collectionCallbackActionDecorator: CollectionCallbackActionDecorator?
) : IdePlugin() {
    override fun onApply(target: Project) {
        project!!.getPluginManager().apply(LifecycleBasePlugin::class.java)

        // Create Visual Studio project extensions
        val extension: VisualStudioExtensionInternal
        if (isRoot) {
            extension = project!!.getExtensions().create<VisualStudioRootExtension?>(
                VisualStudioRootExtension::class.java,
                "visualStudio",
                DefaultVisualStudioRootExtension::class.java,
                project!!.getName(),
                instantiator,
                target.getObjects(),
                fileResolver,
                artifactRegistry,
                collectionCallbackActionDecorator,
                project!!.getProviders()
            ) as VisualStudioExtensionInternal
            val solution = (extension as VisualStudioRootExtension).getSolution()
            getLifecycleTask().configure({ it: Task? -> it!!.dependsOn(solution) })
            addWorkspace(solution)
        } else {
            extension = project!!.getExtensions().create<VisualStudioExtension?>(
                VisualStudioExtension::class.java,
                "visualStudio",
                DefaultVisualStudioExtension::class.java,
                instantiator,
                fileResolver,
                artifactRegistry,
                collectionCallbackActionDecorator,
                project!!.getProviders()
            ) as VisualStudioExtensionInternal
            getLifecycleTask().configure({ it: Task? -> it!!.dependsOn(extension.getProjects()) })
        }
        includeBuildFileInProject(extension)

        // Create tasks for solutions, projects and filters
        createTasksForVisualStudio(extension)

        // Current Model
        applyVisualStudioCurrentModelRules(extension)
    }

    private fun applyVisualStudioCurrentModelRules(extension: VisualStudioExtensionInternal) {
        project!!.getComponents().withType<CppApplication?>(CppApplication::class.java).all(Action { cppApplication: CppApplication? ->
            val vsProject = extension.getProjectRegistry().createProject(project!!.getName(), cppApplication!!.getName())
            vsProject.getSourceFiles().from(cppApplication.cppSource)
            vsProject.getHeaderFiles().from(cppApplication.headerFiles)
            cppApplication.getBinaries().whenElementFinalized<CppExecutable?>(CppExecutable::class.java, Action { executable: CppExecutable? ->
                extension.getProjectRegistry().addProjectConfiguration(
                    CppApplicationVisualStudioTargetBinary(project!!.getName(), project!!.getPath(), cppApplication, executable, project!!.getLayout())
                )
            })
        })
        project!!.afterEvaluate(Action { proj: Project? ->
            project!!.getComponents().withType<CppLibrary?>(CppLibrary::class.java).all(Action { cppLibrary: CppLibrary? ->
                for (linkage in cppLibrary!!.linkage.get()) {
                    var projectType = VisualStudioTargetBinary.ProjectType.DLL
                    if (Linkage.STATIC == linkage) {
                        projectType = VisualStudioTargetBinary.ProjectType.LIB
                    }
                    val vsProject = extension.getProjectRegistry().createProject(project!!.getName() + projectType.getSuffix(), cppLibrary.getName())
                    vsProject.getSourceFiles().from(cppLibrary.cppSource)
                    vsProject.getHeaderFiles().from(cppLibrary.headerFiles)
                }
                cppLibrary.getBinaries().whenElementFinalized<CppSharedLibrary?>(CppSharedLibrary::class.java, Action { library: CppSharedLibrary? ->
                    extension.getProjectRegistry().addProjectConfiguration(
                        CppSharedLibraryVisualStudioTargetBinary(project!!.getName(), project!!.getPath(), cppLibrary, library, project!!.getLayout())
                    )
                })
                cppLibrary.getBinaries().whenElementFinalized<CppStaticLibrary?>(CppStaticLibrary::class.java, Action { library: CppStaticLibrary? ->
                    extension.getProjectRegistry().addProjectConfiguration(
                        CppStaticLibraryVisualStudioTargetBinary(project!!.getName(), project!!.getPath(), cppLibrary, library, project!!.getLayout())
                    )
                })
            })
        })
    }

    private fun includeBuildFileInProject(extension: VisualStudioExtensionInternal) {
        extension.getProjectRegistry().all(Action { vsProject: DefaultVisualStudioProject? ->
            if (project!!.getBuildFile() != null) {
                vsProject!!.addSourceFile(project!!.getBuildFile())
            }
        })
    }

    private fun createTasksForVisualStudio(extension: VisualStudioExtensionInternal) {
        extension.getProjectRegistry().all(Action { vsProject: DefaultVisualStudioProject? -> addTasksForVisualStudioProject(vsProject!!) })

        if (isRoot) {
            val rootExtension = extension as VisualStudioRootExtension
            val vsSolution = rootExtension.getSolution() as VisualStudioSolutionInternal

            vsSolution.builtBy(createSolutionTask(vsSolution))
        }

        configureCleanTask()
    }

    private fun addTasksForVisualStudioProject(vsProject: VisualStudioProjectInternal) {
        vsProject.builtBy(createProjectsFileTask(vsProject), createFiltersFileTask(vsProject))

        val lifecycleTask = project!!.getTasks().maybeCreate(vsProject.getComponentName() + "VisualStudio")
        lifecycleTask.dependsOn(vsProject)
    }

    private fun configureCleanTask() {
        val cleanTask: TaskProvider<Delete?> = uncheckedCast<TaskProvider<Delete?>?>(getCleanTask())!!

        cleanTask.configure(Action { it: Delete? ->
            it!!.delete(project!!.getTasks().withType<GenerateSolutionFileTask?>(GenerateSolutionFileTask::class.java))
            it.delete(project!!.getTasks().withType<GenerateFiltersFileTask?>(GenerateFiltersFileTask::class.java))
            it.delete(project!!.getTasks().withType<GenerateProjectFileTask?>(GenerateProjectFileTask::class.java))
        })
    }

    @Suppress("deprecation")
    private fun createSolutionTask(solution: VisualStudioSolution): Task {
        return project!!.getTasks().create<GenerateSolutionFileTask>(solution.getName() + "VisualStudioSolution", GenerateSolutionFileTask::class.java, solution)
    }

    @Suppress("deprecation")
    private fun createProjectsFileTask(vsProject: VisualStudioProject): Task {
        val task = project!!.getTasks().create<GenerateProjectFileTask>(vsProject.getName() + "VisualStudioProject", GenerateProjectFileTask::class.java, vsProject)
        task.initGradleCommand()
        return task
    }

    @Suppress("deprecation")
    private fun createFiltersFileTask(vsProject: VisualStudioProject): Task {
        return project!!.getTasks().create<GenerateFiltersFileTask>(vsProject.getName() + "VisualStudioFilters", GenerateFiltersFileTask::class.java, vsProject)
    }

    companion object {
        val lifecycleTaskName: String = "visualStudio"
            get() = Companion.field
    }
}
