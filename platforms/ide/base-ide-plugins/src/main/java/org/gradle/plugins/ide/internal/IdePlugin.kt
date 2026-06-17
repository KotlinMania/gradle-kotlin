/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.internal

import com.google.common.base.Optional
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.deprecation.DeprecationLogger.deprecateTask
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.internal.os.OperatingSystem.Companion.current
import org.gradle.plugins.ide.IdeWorkspace
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import java.awt.Desktop
import java.io.IOException
import javax.inject.Inject

abstract class IdePlugin : Plugin<Project?> {
    private var lifecycleTask: TaskProvider<Task?>? = null
    private var cleanTask: TaskProvider<Delete?>? = null
    @JvmField
    protected var project: Project? = null

    @get:Inject
    protected abstract val execOperations: ExecOperations

    override fun apply(target: Project) {
        project = target
        lifecycleTask = target.getTasks().register(this.lifecycleTaskName!!)
        cleanTask = target.getTasks().register<Delete?>(cleanName(this.lifecycleTaskName), Delete::class.java, object : Action<Delete?> {
            override fun execute(task: Delete) {
                task.setGroup("IDE")
                if (shouldDeprecateLifecycleTask()) {
                    task.doFirst(deprecateTaskAction())
                }
            }
        })
        lifecycleTask!!.configure(object : Action<Task?> {
            override fun execute(task: Task) {
                task.setGroup("IDE")
                task.shouldRunAfter(cleanTask!!)
                if (shouldDeprecateLifecycleTask()) {
                    task.doFirst(deprecateTaskAction())
                }
            }
        })
        onApply(target)
    }

    fun getLifecycleTask(): TaskProvider<out Task?> {
        return lifecycleTask!!
    }

    fun getCleanTask(): TaskProvider<out Task?> {
        return cleanTask!!
    }

    protected fun cleanName(taskName: String?): String {
        return String.format("clean%s", StringUtils.capitalize(taskName))
    }

    fun addWorker(worker: Task) {
        addWorker(project!!.getTasks().named(worker.getName()), worker.getName())
    }

    fun addWorker(worker: Task, includeInClean: Boolean) {
        addWorker(project!!.getTasks().named(worker.getName()), worker.getName(), includeInClean)
    }

    @JvmOverloads
    fun addWorker(worker: TaskProvider<out Task?>, workerName: String?, includeInClean: Boolean = true) {
        lifecycleTask!!.configure(dependsOn(worker))
        val cleanWorker = project!!.getTasks().register<Delete?>(cleanName(workerName), Delete::class.java, object : Action<Delete?> {
            override fun execute(cleanWorker: Delete) {
                cleanWorker.delete(worker)
                cleanWorker.doFirst(deprecateTaskAction())
            }
        })

        if (includeInClean) {
            cleanTask!!.configure(dependsOn(cleanWorker))
        }

        // Always schedule the generation task after the clean task
        worker.configure(object : Action<Task?> {
            override fun execute(task: Task) {
                task.shouldRunAfter(cleanWorker)
            }
        })
    }

    protected open fun onApply(target: Project?) {
    }

    protected fun addWorkspace(workspace: IdeWorkspace) {
        lifecycleTask!!.configure(object : Action<Task?> {
            override fun execute(lifecycleTask: Task) {
                val displayName = workspace.getDisplayName()
                val location = workspace.getLocation()
                lifecycleTask.doLast(SerializableLambdas.action<Task?>(SerializableLambdas.SerializableAction { t: Task? ->
                    LOGGER!!.lifecycle(String.format("Generated %s at %s", displayName, ConsoleRenderer().asClickableFileUrl(location.get().getAsFile())))
                }))
            }
        })

        project!!.getTasks().register("open" + StringUtils.capitalize(this.lifecycleTaskName), object : Action<Task?> {
            override fun execute(openTask: Task) {
                openTask.dependsOn(lifecycleTask!!)
                openTask.setGroup("IDE")
                openTask.setDescription("Opens the " + workspace.getDisplayName())

                val execOperations: ExecOperations = this.execOperations
                if (shouldDeprecateLifecycleTask()) {
                    openTask.doFirst(deprecateTaskAction())
                }
                openTask.doLast(object : Action<Task?> {
                    override fun execute(task: Task?) {
                        if (current()!!.isMacOsX) {
                            execOperations.exec(Action { execSpec: ExecSpec? -> execSpec!!.commandLine("open", workspace.getLocation().get()) })
                        } else {
                            try {
                                Desktop.getDesktop().open(workspace.getLocation().get().getAsFile())
                            } catch (e: IOException) {
                                throw throwAsUncheckedException(e)
                            }
                        }
                    }
                })
            }
        })
    }

    protected abstract val lifecycleTaskName: String?

    protected open fun shouldDeprecateLifecycleTask(): Boolean {
        return false
    }

    val isRoot: Boolean
        get() = project!!.getParent() == null

    companion object {
        private val LOGGER = getLogger(IdePlugin::class.java)

        /**
         * Returns the path to the correct Gradle distribution to use. The wrapper of the generating project will be used only if the execution context of the currently running Gradle is in the Gradle home (typical of a wrapper execution context). If this isn't the case, we try to use the current Gradle home, if available, as the distribution. Finally, if nothing matches, we default to the system-wide Gradle distribution.
         *
         * @param project the Gradle project generating the IDE files
         * @return path to Gradle distribution to use within the generated IDE files
         */
        @JvmStatic
        fun toGradleCommand(project: Project): String {
            val gradle = project.getGradle()
            var gradleWrapperPath: Optional<String> = Optional.absent<String?>()

            val rootProject = project.getRootProject()
            val gradlewExtension = if (current()!!.isWindows) ".bat" else ""
            val gradlewFile = rootProject.file("gradlew" + gradlewExtension)
            if (gradlewFile.exists()) {
                gradleWrapperPath = Optional.of<String?>(gradlewFile.getAbsolutePath())
            }

            if (gradle.getGradleHomeDir() != null) {
                if (gradleWrapperPath.isPresent() && gradle.getGradleHomeDir()!!.getAbsolutePath().startsWith(gradle.getGradleUserHomeDir().getAbsolutePath())) {
                    return gradleWrapperPath.get()
                }
                return gradle.getGradleHomeDir()!!.getAbsolutePath() + "/bin/gradle"
            }

            return gradleWrapperPath.or("gradle")
        }

        @JvmStatic
        protected fun dependsOn(taskDependency: Task): Action<in Task?> {
            return object : Action<Task?> {
                override fun execute(task: Task) {
                    task.dependsOn(taskDependency)
                }
            }
        }

        protected fun dependsOn(taskProvider: TaskProvider<out Task?>): Action<in Task?> {
            return object : Action<Task?> {
                override fun execute(task: Task) {
                    task.dependsOn(taskProvider)
                }
            }
        }

        private fun deprecateTaskAction(): Action<Task?> {
            return SerializableLambdas.action<Task?>(SerializableLambdas.SerializableAction { task: Task? ->
                deprecateTask(task!!.getName())
                    .willBeRemovedInGradle10()
                    .withUpgradeGuideSection(9, "ide_task_deprecation")!!
                    .nagUser()
            })
        }

        @JvmStatic
        protected fun withDescription(description: String?): Action<in Task?> {
            return object : Action<Task?> {
                override fun execute(task: Task) {
                    task.setDescription(description)
                }
            }
        }
    }
}
