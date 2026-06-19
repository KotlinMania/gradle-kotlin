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
package org.gradle.language.base.plugins

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Delete
import org.gradle.internal.execution.BuildOutputCleanupRegistry
import org.gradle.language.base.internal.plugins.CleanRule

/**
 *
 * A [Plugin] which defines a basic project lifecycle.
 *
 * @see [Base plugin reference](https://docs.gradle.org/current/userguide/base_plugin.html)
 */
abstract class LifecycleBasePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val projectInternal = project as ProjectInternal
        addClean(projectInternal)
        addCleanRule(project)
        addAssemble(project)
        addCheck(project)
        addBuild(project)
    }

    private fun addClean(project: ProjectInternal) {
        val buildDir: Provider<Directory> = project.getLayout().getBuildDirectory()

        // Register at least the project buildDir as a directory to be deleted.
        val buildOutputCleanupRegistry = project.getServices().get(BuildOutputCleanupRegistry::class.java) as BuildOutputCleanupRegistry
        buildOutputCleanupRegistry.registerOutputs(buildDir)

        val clean: Provider<Delete> = project.getTasks().register(CLEAN_TASK_NAME, Delete::class.java, Action { cleanTask: Delete ->
            cleanTask.setDescription("Deletes the build directory.")
            cleanTask.setGroup(BUILD_GROUP)
            cleanTask.delete(buildDir)
        })
        buildOutputCleanupRegistry.registerOutputs(clean.map<FileCollection>(Transformer { cl: Delete -> cl.getTargetFiles() }))
    }

    private fun addCleanRule(project: Project) {
        project.getTasks().addRule(CleanRule(project.getTasks()))
    }

    private fun addAssemble(project: Project) {
        project.getTasks().register(ASSEMBLE_TASK_NAME, Action { assembleTask: Task? ->
            assembleTask!!.setDescription("Assembles the outputs of this project.")
            assembleTask.setGroup(BUILD_GROUP)
        })
    }

    private fun addCheck(project: Project) {
        project.getTasks().register(CHECK_TASK_NAME, Action { checkTask: Task? ->
            checkTask!!.setDescription("Runs all checks.")
            checkTask.setGroup(VERIFICATION_GROUP)
        })
    }

    private fun addBuild(project: Project) {
        project.getTasks().register(BUILD_TASK_NAME, Action { buildTask: Task? ->
            buildTask!!.setDescription("Assembles and tests this project.")
            buildTask.setGroup(BUILD_GROUP)
            buildTask.dependsOn(ASSEMBLE_TASK_NAME)
            buildTask.dependsOn(CHECK_TASK_NAME)
        })
    }

    companion object {
        const val CLEAN_TASK_NAME: String = "clean"
        const val ASSEMBLE_TASK_NAME: String = "assemble"
        const val CHECK_TASK_NAME: String = "check"
        const val BUILD_TASK_NAME: String = "build"
        const val BUILD_GROUP: String = "build"
        const val VERIFICATION_GROUP: String = "verification"
    }
}
