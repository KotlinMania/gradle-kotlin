/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.testing.jacoco.plugins

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskCollection
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty
import org.gradle.internal.jacoco.JacocoAgentJar
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.process.JavaForkOptions
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension.Companion.TASK_EXTENSION_NAME
import java.io.File
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Extension including common properties and methods for Jacoco.
 */
abstract class JacocoPluginExtension @Inject constructor(project: Project, private val agent: JacocoAgentJar) {
    private val providers: ProviderFactory
    private val objects: ObjectFactory
    private val layout: ProjectLayout
    private val fs: FileSystemOperations

    /**
     * Version of Jacoco JARs to use.
     */
    @get:ToBeReplacedByLazyProperty
    var toolVersion: String? = null

    /**
     * Creates a Jacoco plugin extension.
     *
     * @param project the project the extension is attached to
     * @param agent the agent JAR to be used by Jacoco
     */
    init {
        this.providers = project.getProviders()
        this.objects = project.getObjects()
        this.layout = project.getLayout()
        this.fs = (project as ProjectInternal).getServices().get<FileSystemOperations?>(FileSystemOperations::class.java)!!
    }

    /**
     * The directory where reports will be generated.
     *
     * @since 6.8
     */
    abstract val reportsDirectory: DirectoryProperty?

    /**
     * Applies Jacoco to the given task.
     * Configuration options will be provided on a task extension named 'jacoco'.
     * Jacoco will be run as an agent during the execution of the task.
     *
     * @param task the task to apply Jacoco to.
     * @see TASK_EXTENSION_NAME
     */
    fun <T> applyTo(task: T?) where T : Task?, T : JavaForkOptions? {
        val taskName = task!!.getName()
        LOGGER.debug("Applying Jacoco to " + taskName)
        val extension = task.getExtensions().create<JacocoTaskExtension>(TASK_EXTENSION_NAME, JacocoTaskExtension::class.java, objects, agent, task)
        extension.setDestinationFile(layout.getBuildDirectory().file("jacoco/" + taskName + ".exec").map<File>(Transformer { obj: RegularFile? -> obj!!.getAsFile() }))

        task.getJvmArgumentProviders().add(JacocoAgent(extension))
        task.doFirst(
            JacocoOutputCleanupTestTaskAction(
                fs,
                providers.provider<Boolean>(Callable { extension.isEnabled() && extension.getOutput() == JacocoTaskExtension.Output.FILE }),
                providers.provider<File>(Callable { extension.getDestinationFile() })
            )
        )

        // Do not cache the task if we are not writing execution data to a file
        val doNotCachePredicate = providers.provider<Boolean>(Callable { // Do not cache Test task if Jacoco doesn't produce its output as files
            extension.isEnabled() && extension.getOutput() != JacocoTaskExtension.Output.FILE
        }
        )
        task.getOutputs().doNotCacheIf(
            "JaCoCo configured to not produce its output as a file",
            SerializableLambdas.spec<Task>(SerializableLambdas.SerializableSpec { targetTask: Task -> doNotCachePredicate.get() })
        )
    }

    private class JacocoOutputCleanupTestTaskAction(private val fs: FileSystemOperations, private val hasFileOutput: Provider<Boolean>, private val destinationFile: Provider<File>) : Action<Task> {
        override fun execute(task: Task) {
            if (hasFileOutput.get()) {
                // Delete the coverage file before the task executes, so we don't append to a leftover file from the last execution.
                // This makes the task cacheable even if multiple JVMs write to same destination file, e.g. when executing tests in parallel.
                // The JaCoCo agent supports writing in parallel to the same file, see https://github.com/jacoco/jacoco/pull/52.
                val coverageFile = destinationFile.getOrNull()
                if (coverageFile == null) {
                    throw GradleException("JaCoCo destination file must not be null if output type is FILE")
                }
                fs.delete(Action { spec: DeleteSpec? -> spec!!.delete(coverageFile) })
            }
        }
    }

    private class JacocoAgent(private val jacoco: JacocoTaskExtension) : CommandLineArgumentProvider, Named {
        @Optional
        @Nested
        @Suppress("unused") // public API
        fun getJacoco(): JacocoTaskExtension? {
            return if (jacoco.isEnabled()) jacoco else null
        }

        override fun asArguments(): Iterable<String> {
            return if (jacoco.isEnabled()) ImmutableList.of<String>(jacoco.getAsJvmArg()) else mutableListOf<String>()
        }

        @Internal
        override fun getName(): String {
            return "jacocoAgent"
        }
    }

    /**
     * Applies Jacoco to all of the given tasks.
     *
     * @param tasks the tasks to apply Jacoco to
     */
    fun <T> applyTo(tasks: TaskCollection<T?>) where T : Task?, T : JavaForkOptions? {
        (tasks as TaskCollection<*>).withType(JavaForkOptions::class.java, Action { task: T? -> this.applyTo(task) })
    }

    companion object {
        const val TASK_EXTENSION_NAME: String = "jacoco"

        private val LOGGER: Logger = getLogger(JacocoPluginExtension::class.java)!!
    }
}
