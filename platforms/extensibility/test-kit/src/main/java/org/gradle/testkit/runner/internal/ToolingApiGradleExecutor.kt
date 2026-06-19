/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.testkit.runner.internal

import com.google.common.io.FileBackedOutputStream
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.internal.SystemProperties
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.InvalidRunnerConfigurationException
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnsupportedFeatureException
import org.gradle.testkit.runner.internal.feature.BuildResultOutputFeatureCheck
import org.gradle.testkit.runner.internal.feature.TestKitFeature
import org.gradle.testkit.runner.internal.io.NoCloseOutputStream
import org.gradle.testkit.runner.internal.io.SynchronizedOutputStream
import org.gradle.tooling.BuildException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationResult
import org.gradle.tooling.events.task.TaskProgressEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.events.task.TaskSuccessResult
import org.gradle.tooling.internal.consumer.DefaultBuildLauncher
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.util.GradleVersion
import org.gradle.util.internal.CollectionUtils.join
import org.gradle.wrapper.GradleUserHomeLookup.gradleUserHome
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.Exception
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.get
import kotlin.collections.toTypedArray
import kotlin.text.StringBuilder
import kotlin.text.isEmpty
import kotlin.text.startsWith

class ToolingApiGradleExecutor : GradleExecutor {
    override fun run(parameters: GradleExecutionParameters?): GradleExecutionResult {
        parameters!!
        val outputBuffer = FileBackedOutputStream(OUTPUT_BUFFER_FILE_THRESHOLD_BYTES)
        val syncOutput: OutputStream = SynchronizedOutputStream(outputBuffer)

        val tasks: MutableList<BuildTask?> = ArrayList<BuildTask?>()

        maybeRegisterCleanup()

        val gradleConnector = buildConnector(
            parameters.gradleUserHome,
            parameters.projectDir,
            parameters.isEmbedded,
            parameters.gradleProvider!!
        )

        var connection: ProjectConnection? = null
        var targetGradleVersion: GradleVersion? = null

        try {
            connection = gradleConnector.connect()
            targetGradleVersion = determineTargetGradleVersion(connection!!)
            if (targetGradleVersion.compareTo(TestKitFeature.RUN_BUILDS.since!!) < 0) {
                throw UnsupportedFeatureException(
                    String.format(
                        "The version of Gradle you are using (%s) is not supported by TestKit. TestKit supports all Gradle versions %s and later.",
                        targetGradleVersion.getVersion(), DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION.getVersion()
                    )
                )
            } else {
                warnIfUnsupportedVersion(targetGradleVersion)
            }


            val launcher = connection!!.newBuild() as DefaultBuildLauncher

            launcher.setStandardOutput(NoCloseOutputStream(teeOutput(syncOutput, parameters.standardOutput)!!))
            launcher.setStandardError(NoCloseOutputStream(teeOutput(syncOutput, parameters.standardError)!!))

            if (parameters.standardInput != null) {
                launcher.setStandardInput(parameters.standardInput)
            }

            launcher.addProgressListener(TaskExecutionProgressListener(tasks), OperationType.TASK)

            launcher.withArguments(*parameters.buildArgs.orEmpty().toTypedArray())
            launcher.setJvmArguments(*parameters.jvmArgs.orEmpty().toTypedArray())
            parameters.environment?.let {
                @Suppress("UNCHECKED_CAST")
                launcher.setEnvironmentVariables(it as MutableMap<String, String>)
            }

            if (!parameters.injectedClassPath!!.isEmpty) {
                if (targetGradleVersion.compareTo(TestKitFeature.PLUGIN_CLASSPATH_INJECTION.since!!) < 0) {
                    throw UnsupportedFeatureException("support plugin classpath injection", targetGradleVersion, TestKitFeature.PLUGIN_CLASSPATH_INJECTION.since!!)
                } else {
                    warnIfUnsupportedVersion(targetGradleVersion)
                }
                launcher.withInjectedClassPath(parameters.injectedClassPath)
            }

            launcher.run()
        } catch (e: UnsupportedVersionException) {
            throw InvalidRunnerConfigurationException("The build could not be executed due to a feature not being supported by the target Gradle version", e)
        } catch (t: BuildException) {
            return GradleExecutionResult(BuildOperationParameters(targetGradleVersion, parameters.isEmbedded), outputBuffer.asByteSource(), tasks, t)
        } catch (t: GradleConnectionException) {
            val message = StringBuilder("An error occurred executing build with ")
            if (parameters.buildArgs.orEmpty().isEmpty()) {
                message.append("no args")
            } else {
                message.append("args '")
                message.append(join(" ", parameters.buildArgs.orEmpty()))
                message.append("'")
            }

            message.append(" in directory '").append(parameters.projectDir!!.absolutePath).append("'")

            var capturedOutput: String?
            try {
                capturedOutput = outputBuffer.asByteSource().asCharSource(Charset.defaultCharset())
                    .read()
            } catch (e: IOException) {
                capturedOutput = "<Error fetching output: " + e.message + ">"
            }
            if (!capturedOutput.isNullOrEmpty()) {
                message.append(". Output before error:")
                    .append(SystemProperties.getInstance().getLineSeparator())
                    .append(capturedOutput)
            }

            throw IllegalStateException(message.toString(), t)
        } finally {
            if (connection != null) {
                connection.close()
            }
        }

        return GradleExecutionResult(BuildOperationParameters(targetGradleVersion, parameters.isEmbedded), outputBuffer.asByteSource(), tasks)
    }

    private fun determineTargetGradleVersion(connection: ProjectConnection): GradleVersion {
        @Suppress("UNCHECKED_CAST")
        val buildEnvironment = connection.getModel(BuildEnvironment::class.java as Class<BuildEnvironment?>?)
        return GradleVersion.version(buildEnvironment!!.gradle!!.gradleVersion)
    }

    private fun buildConnector(gradleUserHome: File?, projectDir: File?, embedded: Boolean, gradleProvider: GradleProvider): GradleConnector {
        val gradleConnector = GradleConnector.newConnector() as DefaultGradleConnector
        gradleConnector.useDistributionBaseDir(gradleUserHome())
        gradleProvider.applyTo(gradleConnector)
        gradleConnector.useGradleUserHomeDir(gradleUserHome)
        gradleConnector.daemonBaseDir(File(gradleUserHome!!, TEST_KIT_DAEMON_DIR_NAME))
        gradleConnector.forProjectDirectory(projectDir)
        gradleConnector.daemonMaxIdleTime(120, TimeUnit.SECONDS)
        gradleConnector.embedded(embedded)
        return gradleConnector
    }

    private class TaskExecutionProgressListener(private val tasks: MutableList<BuildTask?>) : ProgressListener {
        private val order: MutableMap<String?, Int> = HashMap<String?, Int>()

        override fun statusChanged(event: ProgressEvent?) {
            if (event is TaskStartEvent) {
                val taskStartEvent = event
                if (taskIsFromBuildSrc(taskStartEvent)) {
                    return
                }
                order.put(taskStartEvent.descriptor!!.taskPath, tasks.size)
                tasks.add(null)
            }
            if (event is TaskFinishEvent) {
                val taskFinishEvent = event
                if (taskIsFromBuildSrc(taskFinishEvent)) {
                    return
                }
                val taskPath = taskFinishEvent.descriptor!!.taskPath
                val result = taskFinishEvent.result
                val index: Int = order[taskPath]
                    ?: error("Received task finish event for task $taskPath without first receiving task start event")
                tasks.set(index, determineBuildTask(result, taskPath))
            }
        }

        fun determineBuildTask(result: TaskOperationResult?, taskPath: String?): BuildTask {
            if (isFailed(result)) {
                return createBuildTask(taskPath, TaskOutcome.FAILED)
            } else if (isNoSource(result)) {
                return createBuildTask(taskPath, TaskOutcome.NO_SOURCE)
            } else if (isSkipped(result)) {
                return createBuildTask(taskPath, TaskOutcome.SKIPPED)
            } else if (isFromCache(result)) {
                return createBuildTask(taskPath, TaskOutcome.FROM_CACHE)
            } else if (isUpToDate(result)) {
                return createBuildTask(taskPath, TaskOutcome.UP_TO_DATE)
            }

            return createBuildTask(taskPath, TaskOutcome.SUCCESS)
        }

        fun isNoSource(result: TaskOperationResult?): Boolean {
            return isSkipped(result) && (result as TaskSkippedResult).skipMessage == "NO-SOURCE"
        }

        fun createBuildTask(taskPath: String?, outcome: TaskOutcome?): BuildTask {
            return DefaultBuildTask(taskPath, outcome)
        }

        fun isFailed(result: TaskOperationResult?): Boolean {
            return result is TaskFailureResult
        }

        fun isSkipped(result: TaskOperationResult?): Boolean {
            return result is TaskSkippedResult
        }

        fun isUpToDate(result: TaskOperationResult?): Boolean {
            return result is TaskSuccessResult && result.isUpToDate
        }

        fun isFromCache(result: TaskOperationResult?): Boolean {
            return result is TaskSuccessResult && result.isFromCache
        }

        companion object {
            private fun taskIsFromBuildSrc(event: TaskProgressEvent): Boolean {
                return event.descriptor!!.taskPath?.startsWith(":buildSrc") == true
            }
        }
    }

    companion object {
        const val TEST_KIT_DAEMON_DIR_NAME: String = "test-kit-daemon"

        private const val CLEANUP_THREAD_NAME = "gradle-runner-cleanup"

        /**
         * The number of bytes before the output of an execution output should switch to buffering to a file
         *
         * @see FileBackedOutputStream
         */
        private val OUTPUT_BUFFER_FILE_THRESHOLD_BYTES = 1024 * 1024

        private val SHUTDOWN_REGISTERED = AtomicBoolean()

        // We don't have logging infrastructure here
        private fun maybeRegisterCleanup() {
            if (SHUTDOWN_REGISTERED.compareAndSet(false, true)) {
                Runtime.getRuntime().addShutdownHook(Thread(Runnable {
                    try {
                        DefaultGradleConnector.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, CLEANUP_THREAD_NAME))
            }
        }

        private fun warnIfUnsupportedVersion(targetGradleVersion: GradleVersion?) {
            BuildResultOutputFeatureCheck.warnIfUnsupportedVersion(
                targetGradleVersion!!,
                { version -> String.format("The version of Gradle you are using (%s) is deprecated with TestKit.", version) })
        }

        private fun teeOutput(capture: OutputStream?, user: OutputStream?): OutputStream? {
            if (user == null) {
                return capture
            } else {
                return TeeOutputStream(capture, user)
            }
        }
    }
}
