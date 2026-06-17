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
package org.gradle.plugin.devel.tasks

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Incubating
import org.gradle.api.internal.DocumentationRegistry.getDocumentationRecommendationFor
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.Problems
import org.gradle.api.problems.internal.GradleCoreProblemGroup
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.deprecation.Documentation.Companion.upgradeMajorGuide
import org.gradle.internal.deprecation.Documentation.Companion.upgradeMinorGuide
import org.gradle.internal.execution.WorkValidationException
import org.gradle.internal.execution.WorkValidationUtils
import org.gradle.internal.jvm.SupportedJavaVersions
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.plugin.devel.tasks.internal.ValidateAction
import org.gradle.plugin.devel.tasks.internal.ValidationProblemSerialization
import org.gradle.problems.internal.rendering.ProblemWriter
import org.gradle.workers.ProcessWorkerSpec
import java.io.IOException
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.List
import java.util.function.BiFunction
import java.util.stream.Stream
import javax.inject.Inject

/**
 * Validates plugins by checking property annotations on work items like tasks and artifact transforms.
 *
 * This task should be used in Gradle plugin projects for doing static analysis on the plugin classes.
 *
 * The [java-gradle-plugin](https://docs.gradle.org/current/userguide/java_gradle_plugin.html) adds
 * a `validatePlugins` task, though if you cannot use this plugin then you need to register the task yourself.
 *
 * See the user guide for more information on
 * [incremental build](https://docs.gradle.org/current/userguide/incremental_build.html) and
 * [caching task outputs](https://docs.gradle.org/current/userguide/build_cache.html#sec:task_output_caching).
 *
 * @since 6.0
 */
@CacheableTask
abstract class ValidatePlugins : DefaultTask() {
    init {
        this.enableStricterValidation.convention(false)
        this.ignoreFailures.convention(false)
        this.failOnWarning.convention(true)

        val toolchainService = getProject().getExtensions().findByType<JavaToolchainService>(JavaToolchainService::class.java)
        if (toolchainService != null) {
            val javaPlugin = getProject().getExtensions().findByType<JavaPluginExtension>(JavaPluginExtension::class.java)
            if (javaPlugin != null) {
                createToolchainConvention(toolchainService, javaPlugin)
            } else {
                this.launcher.convention(toolchainService.launcherFor(Action { spec: JavaToolchainSpec? -> }))
            }
        }
    }

    private fun createToolchainConvention(toolchainService: JavaToolchainService, javaPlugin: JavaPluginExtension) {
        this.launcher.convention(
            toolchainService.launcherFor(javaPlugin.toolchain)
                .zip<JavaLauncher, JavaLauncher>(toolchainService.launcherFor(Action { spec: JavaToolchainSpec? -> }), BiFunction { project: JavaLauncher?, current: JavaLauncher ->
                    if (project!!.metadata.languageVersion.canCompileOrRun(
                            SupportedJavaVersions.MINIMUM_DAEMON_JAVA_VERSION
                        )
                    ) {
                        // We use the project toolchain only if it is compatible with the minimum required version for the daemon
                        return@zip project
                    } else {
                        // Otherwise we fall back to the daemon JVM
                        return@zip current
                    }
                })
        )
    }

    @TaskAction
    @Throws(IOException::class)
    fun validateTaskClasses() {
        this.workerExecutor
            .processIsolation(Action { spec: ProcessWorkerSpec? ->
                if (this.launcher.isPresent()) {
                    val launcher = this.launcher.get()
                    if (!launcher.getMetadata().getLanguageVersion().canCompileOrRun(SupportedJavaVersions.MINIMUM_DAEMON_JAVA_VERSION)) {
                        val problemId = ProblemId.create(
                            "invalid-java-toolchain",
                            "Running task ValidatePlugins with Java Toolchain lower than " + SupportedJavaVersions.MINIMUM_DAEMON_JAVA_VERSION,
                            GradleCoreProblemGroup.validation().thisGroup()
                        )
                        val problemReporter = getServices().get<Problems?>(Problems::class.java)!!.reporter
                        val exception = GradleException(problemId.displayName + " is not supported.")
                        throw problemReporter.throwing(
                            exception,
                            problemReporter.create(problemId, Action { problemSpec: ProblemSpec? ->
                                problemSpec!!.documentedAt(upgradeMinorGuide(9, "validate_plugins_java_version").url)
                                problemSpec.contextualLabel(exception.message!!)
                            })
                        )
                    }
                    spec!!.getForkOptions().setExecutable(launcher.getExecutablePath())
                } else {
                    val problemId = ProblemId.create(
                        "missing-java-toolchain-plugin",
                        "Using task ValidatePlugins without applying the Java Toolchain plugin",
                        GradleCoreProblemGroup.validation().thisGroup()
                    )
                    val problemReporter = getServices().get<Problems?>(Problems::class.java)!!.reporter
                    val exception = GradleException(problemId.displayName + " is not supported.")
                    throw problemReporter.throwing(
                        exception,
                        problemReporter.create(problemId, Action { problemSpec: ProblemSpec? ->
                            problemSpec!!.documentedAt(upgradeMajorGuide(9, "validate_plugins_without_java_toolchain_90").url)
                            problemSpec.contextualLabel(exception.message!!)
                        })
                    )
                }
                // The classpath includes both the plugin classes and the dependencies:
                spec.getClasspath().setFrom(this.classpath)
            })
            .submit<ValidateAction.Params>(ValidateAction::class.java, Action { params: ValidateAction.Params ->
                params.getClasses().setFrom(this.classes)
                params.getOutputFile().set(this.outputFile)
                params.getEnableStricterValidation().set(this.enableStricterValidation)
            })
        this.workerExecutor.await()

        val parsedProblems = ValidationProblemSerialization.deserialize(
            String(
                Files.readAllBytes(
                    this.outputFile.get().getAsFile().toPath()
                ), StandardCharsets.UTF_8
            )
        )
        val warnings: MutableList<out ProblemInternal> = parsedProblems.getWarnings()
        val errors: MutableList<out ProblemInternal> = parsedProblems.getErrors()

        if (errors.isEmpty() && warnings.isEmpty()) {
            getLogger().info("Plugin validation finished without warnings.")
        } else if (this.failOnWarning.get() || !errors.isEmpty()) {
            if (this.ignoreFailures.get()) {
                getLogger().warn(
                    "Plugin validation finished with errors. {}{}{}",
                    annotateTaskPropertiesDoc(),
                    System.lineSeparator(),
                    renderProblems(warnings, errors)
                )
            } else {
                val reporter = getServices().get<ProblemsInternal?>(ProblemsInternal::class.java)!!.reporter
                val reportedProblems =
                    WorkValidationUtils.deduplicateAndTruncate(Stream.concat<ProblemInternal>(warnings.stream(), errors.stream()).collect(ImmutableList.toImmutableList<ProblemInternal>()))
                val exception = WorkValidationException.withSummaryForPlugin(reportedProblems.size, List.of<String>(annotateTaskPropertiesDoc()))
                throw reporter.throwing(exception, reportedProblems)
            }
        } else {
            getLogger().warn(
                "Plugin validation finished with warnings:{}{}",
                System.lineSeparator(),
                renderProblems(warnings, errors)
            )
        }
    }

    private fun annotateTaskPropertiesDoc(): String {
        return this.documentationRegistry.getDocumentationRecommendationFor("on how to annotate task properties", "incremental_build", "sec:task_input_output_annotations")
    }

    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    @get:SkipWhenEmpty
    @get:InputFiles
    abstract val classes: ConfigurableFileCollection?

    @get:Classpath
    abstract val classpath: ConfigurableFileCollection?

    @get:Incubating
    @get:Optional
    @get:Nested
    abstract val launcher: Property<JavaLauncher>?

    @get:Input
    abstract val ignoreFailures: Property<Boolean>?

    @get:Input
    abstract val failOnWarning: Property<Boolean>?

    @get:Input
    abstract val enableStricterValidation: Property<Boolean>?

    @get:OutputFile
    abstract val outputFile: RegularFileProperty?

    @get:Inject
    protected abstract val documentationRegistry: DocumentationRegistry?

    @get:Inject
    protected abstract val workerExecutor: WorkerExecutor?

    companion object {
        private fun renderProblems(warnings: MutableList<out ProblemInternal>, errors: MutableList<out ProblemInternal>): String {
            val all: MutableList<ProblemInternal> = Stream.concat<ProblemInternal>(warnings.stream(), errors.stream()).collect(ImmutableList.toImmutableList<ProblemInternal>())
            val writer = StringWriter()
            ProblemWriter.simple().write(all, writer)
            return writer.toString()
        }
    }
}
