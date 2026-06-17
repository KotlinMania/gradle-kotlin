/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.nativeplatform.internal

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.resolve.ProjectModelResolver
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.model.ModelMap
import org.gradle.model.internal.type.ModelTypes
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeComponentSpec
import org.gradle.nativeplatform.NativeDependencySet
import org.gradle.nativeplatform.NativeExecutableFileSpec
import org.gradle.nativeplatform.NativeInstallationSpec
import org.gradle.nativeplatform.tasks.InstallExecutable
import org.gradle.nativeplatform.tasks.LinkExecutable
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.ComponentSpecContainer
import org.gradle.platform.base.VariantComponentSpec
import org.gradle.platform.base.internal.BinaryNamingScheme
import org.gradle.platform.base.internal.BinarySpecInternal
import org.gradle.platform.base.internal.dependents.DependentBinariesResolver
import java.io.File
import java.util.concurrent.Callable

object NativeComponents {
    private const val ASSEMBLE_DEPENDENTS_TASK_NAME = "assembleDependents"
    private const val BUILD_DEPENDENTS_TASK_NAME = "buildDependents"

    fun createExecutableTask(binary: NativeBinarySpecInternal, executableFile: File?) {
        val taskName = binary.getNamingScheme().getTaskName("link")
        binary.getTasks().create<LinkExecutable?>(taskName, LinkExecutable::class.java, object : Action<LinkExecutable?> {
            override fun execute(linkTask: LinkExecutable) {
                linkTask.setDescription("Links " + binary.getDisplayName())
                linkTask.toolChain!!.set(binary.getToolChain())
                linkTask.targetPlatform!!.set(binary.getTargetPlatform())
                linkTask.linkedFile.set(executableFile)
                linkTask.linkerArgs.set(binary.getLinker().getArgs())

                linkTask.lib(object : BinaryLibs(binary) {
                    override fun getFiles(nativeDependencySet: NativeDependencySet): FileCollection? {
                        return nativeDependencySet.getLinkFiles()
                    }
                })
                binary.builtBy(linkTask)
            }
        })
    }

    fun createInstallTask(binary: NativeBinarySpecInternal, installation: NativeInstallationSpec, executable: NativeExecutableFileSpec, namingScheme: BinaryNamingScheme) {
        binary.getTasks().create<InstallExecutable?>(namingScheme.getTaskName("install"), InstallExecutable::class.java, object : Action<InstallExecutable?> {
            override fun execute(installTask: InstallExecutable) {
                installTask.setDescription("Installs a development image of " + binary.getDisplayName())
                installTask.setGroup(LifecycleBasePlugin.BUILD_GROUP)
                installTask.toolChain.set(executable.getToolChain())
                installTask.targetPlatform.set(binary.getTargetPlatform())
                installTask.executableFile.set(executable.getFile())
                installTask.installDirectory.set(installation.getDirectory())
                //TODO:HH wire binary libs via executable
                installTask.lib(object : BinaryLibs(binary) {
                    override fun getFiles(nativeDependencySet: NativeDependencySet): FileCollection? {
                        return nativeDependencySet.getRuntimeFiles()
                    }
                })

                //TODO:HH installTask.dependsOn(executable)
                installTask.dependsOn(binary)
            }
        })
    }

    fun createBuildDependentComponentsTasks(tasks: ModelMap<Task?>, components: ComponentSpecContainer) {
        for (component in components.withType<NativeComponentSpec?>(NativeComponentSpec::class.java).withType<VariantComponentSpec>(VariantComponentSpec::class.java)) {
            tasks.create<DefaultTask?>(getAssembleDependentComponentsTaskName(component), DefaultTask::class.java, object : Action<DefaultTask?> {
                override fun execute(assembleDependents: DefaultTask) {
                    assembleDependents.setGroup("Build Dependents")
                    assembleDependents.setDescription("Assemble dependents of " + component.getDisplayName() + ".")
                }
            })
            tasks.create<DefaultTask?>(getBuildDependentComponentsTaskName(component), DefaultTask::class.java, object : Action<DefaultTask?> {
                override fun execute(buildDependents: DefaultTask) {
                    buildDependents.setGroup("Build Dependents")
                    buildDependents.setDescription("Build dependents of " + component.getDisplayName() + ".")
                }
            })
        }
    }

    fun createBuildDependentBinariesTasks(binary: NativeBinarySpecInternal, namingScheme: BinaryNamingScheme) {
        binary.getTasks().create<DefaultTask?>(namingScheme.getTaskName(ASSEMBLE_DEPENDENTS_TASK_NAME), DefaultTask::class.java, object : Action<DefaultTask?> {
            override fun execute(buildDependentsTask: DefaultTask) {
                buildDependentsTask.setGroup("Build Dependents")
                buildDependentsTask.setDescription("Assemble dependents of " + binary.getDisplayName() + ".")
                buildDependentsTask.dependsOn(binary)
            }
        })
        binary.getTasks().create<DefaultTask?>(namingScheme.getTaskName(BUILD_DEPENDENTS_TASK_NAME), DefaultTask::class.java, object : Action<DefaultTask?> {
            override fun execute(buildDependentsTask: DefaultTask) {
                buildDependentsTask.setGroup("Build Dependents")
                buildDependentsTask.setDescription("Build dependents of " + binary.getDisplayName() + ".")
                buildDependentsTask.dependsOn(binary)
            }
        })
    }

    fun wireBuildDependentTasks(tasks: ModelMap<Task?>, binaries: BinaryContainer, dependentsResolver: DependentBinariesResolver, projectModelResolver: ProjectModelResolver) {
        val nativeBinaries = binaries.withType<NativeBinarySpecInternal?>(NativeBinarySpecInternal::class.java)
        for (binary in nativeBinaries) {
            val assembleDependents = tasks.get(binary.getNamingScheme().getTaskName(ASSEMBLE_DEPENDENTS_TASK_NAME))
            val buildDependents = tasks.get(binary.getNamingScheme().getTaskName(BUILD_DEPENDENTS_TASK_NAME))
            // Wire build dependent components tasks dependencies
            val assembleDependentComponents = tasks.get(NativeComponents.getAssembleDependentComponentsTaskName(binary.getComponent()!!))
            if (assembleDependentComponents != null) {
                assembleDependentComponents.dependsOn(assembleDependents!!)
            }
            val buildDependentComponents = tasks.get(NativeComponents.getBuildDependentComponentsTaskName(binary.getComponent()!!))
            if (buildDependentComponents != null) {
                buildDependentComponents.dependsOn(buildDependents!!)
            }
            // Wire build dependent binaries tasks dependencies
            // Defer dependencies gathering as we need to resolve across project's boundaries
            assembleDependents!!.dependsOn(object : Callable<Iterable<Task?>?> {
                override fun call(): Iterable<Task?> {
                    return getDependentTaskDependencies(ASSEMBLE_DEPENDENTS_TASK_NAME, binary, dependentsResolver, projectModelResolver)
                }
            })
            buildDependents!!.dependsOn(object : Callable<Iterable<Task?>?> {
                override fun call(): Iterable<Task?> {
                    return getDependentTaskDependencies(BUILD_DEPENDENTS_TASK_NAME, binary, dependentsResolver, projectModelResolver)
                }
            })
        }
    }

    private fun getDependentTaskDependencies(
        dependedOnBinaryTaskName: String?,
        binary: BinarySpecInternal?,
        dependentsResolver: DependentBinariesResolver,
        projectModelResolver: ProjectModelResolver
    ): MutableList<Task?> {
        val dependencies: MutableList<Task?> = ArrayList<Task?>()
        val result = dependentsResolver.resolve(binary).getRoot()
        for (dependent in result.getChildren()) {
            if (dependent.isBuildable()) {
                val modelRegistry = projectModelResolver.resolveProjectModel(dependent.getId().getProjectPath())
                val projectBinaries = modelRegistry!!.realize<ModelMap<NativeBinarySpecInternal?>>("binaries", ModelTypes.modelMap<NativeBinarySpecInternal?>(NativeBinarySpecInternal::class.java))
                val projectTasks = modelRegistry.realize<ModelMap<Task?>>("tasks", ModelTypes.modelMap<Task?>(Task::class.java))
                val dependentBinary = projectBinaries.get(dependent.getProjectScopedName())
                dependencies.add(projectTasks.get(dependentBinary!!.getNamingScheme().getTaskName(dependedOnBinaryTaskName)))
            }
        }
        return dependencies
    }

    private fun getAssembleDependentComponentsTaskName(component: ComponentSpec): String {
        return ASSEMBLE_DEPENDENTS_TASK_NAME + StringUtils.capitalize(component.getName())
    }

    private fun getBuildDependentComponentsTaskName(component: ComponentSpec): String {
        return BUILD_DEPENDENTS_TASK_NAME + StringUtils.capitalize(component.getName())
    }

    abstract class BinaryLibs(private val binary: NativeBinarySpec) : Callable<MutableList<FileCollection?>?> {
        @Throws(Exception::class)
        override fun call(): MutableList<FileCollection?> {
            val runtimeFiles: MutableList<FileCollection?> = ArrayList<FileCollection?>()
            for (nativeDependencySet in binary.getLibs()) {
                runtimeFiles.add(getFiles(nativeDependencySet))
            }
            return runtimeFiles
        }

        protected abstract fun getFiles(nativeDependencySet: NativeDependencySet?): FileCollection?
    }
}
