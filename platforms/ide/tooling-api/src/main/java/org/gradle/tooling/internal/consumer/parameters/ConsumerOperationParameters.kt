/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer.parameters

import com.google.common.collect.Lists
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumMap
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Collectors
import org.gradle.api.GradleException
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.classpath.ClassPath
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.ProgressListener
import org.gradle.tooling.StreamedValueListener
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.CancellationTokenInternal
import org.gradle.tooling.internal.consumer.ConnectionConfigurationUtil
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.gradle.TaskListingLaunchable
import org.gradle.tooling.internal.protocol.BuildParameters
import org.gradle.tooling.internal.protocol.InternalLaunchable
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1
import org.gradle.tooling.model.Launchable
import org.gradle.tooling.model.Task
import org.gradle.tooling.model.TaskSelector

/**
 * This is used via reflection from `ProviderOperationParameters`.
 */
class ConsumerOperationParameters private constructor(
    val entryPointName: String,
    private val parameters: ConnectionParameters,
    /**
     * @since 1.0-milestone-3
     */
    val standardOutput: OutputStream,
    /**
     * @since 1.0-milestone-3
     */
    val standardError: OutputStream,
    /**
     * @since 2.3-rc-1
     */
    val isColorOutput: Boolean,
    val standardInput: InputStream,
    val javaHome: File,
    val baseJvmArguments: MutableList<String>?,
    val additionalJvmArguments: MutableList<String>?,
    val environmentVariables: MutableMap<String, String>,
    val arguments: MutableList<String>,
    val tasks: MutableList<String>,
    /**
     * @since 1.12-rc-1
     */
    val launchables: MutableList<InternalLaunchable>,
    private val injectedPluginClasspath: ClassPath,
    private val legacyProgressListeners: MutableList<ProgressListener>,
    private val progressListeners: MutableMap<OperationType, MutableList<org.gradle.tooling.events.ProgressListener>>,
    private val cancellationToken: CancellationToken,
    private val systemProperties: MutableMap<String, String>,
    val streamedValueListener: FailsafeStreamedValueListener
) : BuildParameters {
    class Builder {
        private val legacyProgressListeners: MutableList<ProgressListener> = ArrayList<ProgressListener>()
        private val progressListeners: MutableMap<OperationType, MutableList<org.gradle.tooling.events.ProgressListener>> =
            EnumMap<OperationType, MutableList<org.gradle.tooling.events.ProgressListener>>(
                OperationType::class.java
            )
        private var entryPoint: String? = null
        private var cancellationToken: CancellationToken? = null
        private var parameters: ConnectionParameters? = null
        private var stdout: OutputStream? = null
        private var stderr: OutputStream? = null
        private var colorOutput: Boolean? = null
        private var stdin: InputStream? = null
        private var javaHome: File? = null
        private var baseJvmArguments: MutableList<String>? = null
        private var additionalJvmArguments: MutableList<String>? = null
        private var envVariables: MutableMap<String, String>? = null
        private var arguments: MutableList<String>? = null
        private var tasks: MutableList<String>? = null
        private var launchables: MutableList<InternalLaunchable>? = null
        private var injectedPluginClasspath: ClassPath = ClassPath.EMPTY
        private var systemProperties: MutableMap<String, String>? = null
        private var streamedValueListener: StreamedValueListener? = null

        fun setEntryPoint(entryPoint: String): Builder {
            this.entryPoint = entryPoint
            return this
        }

        fun setParameters(parameters: ConnectionParameters): Builder {
            this.parameters = parameters
            return this
        }

        fun setStdout(stdout: OutputStream): Builder {
            this.stdout = stdout
            return this
        }

        fun setStderr(stderr: OutputStream): Builder {
            this.stderr = stderr
            return this
        }

        fun setColorOutput(colorOutput: Boolean): Builder {
            this.colorOutput = colorOutput
            return this
        }

        fun setStdin(stdin: InputStream): Builder {
            this.stdin = stdin
            return this
        }

        fun setJavaHome(javaHome: File): Builder {
            validateJavaHome(javaHome)
            this.javaHome = javaHome
            return this
        }

        fun setBaseJvmArguments(baseJvmArguments: MutableList<String>?): Builder {
            if (baseJvmArguments != null) {
                this.baseJvmArguments = ArrayList<String>(baseJvmArguments)
            }
            return this
        }

        fun addJvmArguments(jvmArguments: MutableList<String>?): Builder {
            this.additionalJvmArguments = concat(this.additionalJvmArguments, jvmArguments)
            return this
        }

        fun setArguments(arguments: MutableList<String>?): Builder {
            this.arguments = arguments
            return this
        }

        fun addArguments(arguments: MutableList<String>?): Builder {
            this.arguments = concat(this.arguments, arguments)
            return this
        }

        fun setEnvironmentVariables(envVariables: MutableMap<String, String>): Builder {
            this.envVariables = envVariables
            return this
        }

        fun setTasks(tasks: MutableList<String>?): Builder {
            this.tasks = tasks
            return this
        }

        fun setLaunchables(launchables: Iterable<out Launchable>): Builder {
            val taskPaths: MutableSet<String> = LinkedHashSet<String>()
            val launchablesParams: MutableList<InternalLaunchable> = ArrayList<InternalLaunchable>()
            for (launchable in launchables) {
                val original = ProtocolToModelAdapter().unpack(launchable)
                if (original is InternalLaunchable) {
                    // A launchable created by the provider - just hand it back
                    launchablesParams.add(original)
                } else if (original is TaskListingLaunchable) {
                    // A launchable synthesized by the consumer - unpack it into a set of task names
                    taskPaths.addAll(original.taskNames?.filterNotNull().orEmpty())
                } else if (launchable is Task) {
                    // A task created by a provider that does not understand launchables
                    taskPaths.add(launchable.path!!)
                } else {
                    throw GradleException(
                        "Only Task or TaskSelector instances are supported: "
                                + (if (launchable != null) launchable.javaClass else "null")
                    )
                }
            }
            // Tasks are ignored by providers if launchables is not null
            this.launchables = if (launchablesParams.isEmpty()) null else launchablesParams
            tasks = Lists.newArrayList<String>(taskPaths.filterNotNull())
            return this
        }

        fun setInjectedPluginClasspath(classPath: ClassPath): Builder {
            this.injectedPluginClasspath = classPath
            return this
        }

        fun setSystemProperties(systemProperties: MutableMap<String, String>): Builder {
            this.systemProperties = systemProperties
            return this
        }

        fun addProgressListener(listener: ProgressListener) {
            legacyProgressListeners.add(listener)
        }

        fun addProgressListener(listener: org.gradle.tooling.events.ProgressListener, eventTypes: MutableSet<OperationType>) {
            for (type in eventTypes) {
                val listeners = this.progressListeners.computeIfAbsent(type, object : Function<OperationType, MutableList<org.gradle.tooling.events.ProgressListener>> {
                    override fun apply(operationType: OperationType): MutableList<org.gradle.tooling.events.ProgressListener> {
                        return ArrayList<org.gradle.tooling.events.ProgressListener>()
                    }
                })
                listeners.add(listener)
            }
        }

        fun setCancellationToken(cancellationToken: CancellationToken) {
            this.cancellationToken = cancellationToken
        }

        fun setStreamedValueListener(streamedValueListener: StreamedValueListener) {
            this.streamedValueListener = streamedValueListener
        }

        fun build(): ConsumerOperationParameters {
            checkNotNull(entryPoint) { "No entry point specified." }

            return ConsumerOperationParameters(
                entryPoint!!,
                parameters!!,
                stdout!!,
                stderr!!,
                colorOutput!!,
                stdin!!,
                javaHome!!,
                baseJvmArguments,
                additionalJvmArguments,
                envVariables!!,
                arguments!!,
                tasks!!,
                launchables!!,
                injectedPluginClasspath,
                legacyProgressListeners,
                progressListeners,
                cancellationToken!!,
                systemProperties!!,
                FailsafeStreamedValueListener(streamedValueListener)
            )
        }

        fun copyFrom(operationParameters: ConsumerOperationParameters) {
            tasks = operationParameters.tasks
            launchables = operationParameters.launchables
            cancellationToken = operationParameters.cancellationToken
            legacyProgressListeners.addAll(operationParameters.legacyProgressListeners)
            progressListeners.putAll(operationParameters.progressListeners)
            arguments = operationParameters.arguments
            baseJvmArguments = operationParameters.baseJvmArguments
            additionalJvmArguments = operationParameters.additionalJvmArguments
            envVariables = operationParameters.environmentVariables
            stdout = operationParameters.standardOutput
            stderr = operationParameters.standardError
            stdin = operationParameters.standardInput
            colorOutput = operationParameters.isColorOutput
            javaHome = operationParameters.javaHome
            injectedPluginClasspath = operationParameters.injectedPluginClasspath
            systemProperties = operationParameters.systemProperties
            entryPoint = operationParameters.entryPointName
            parameters = operationParameters.parameters
            arguments = operationParameters.arguments
        }

        companion object {
            private fun concat(first: MutableList<String>?, second: MutableList<String>?): MutableList<String> {
                val result: MutableList<String> = ArrayList<String>()
                if (first != null) {
                    result.addAll(first)
                }
                if (second != null) {
                    result.addAll(second)
                }
                return result
            }
        }
    }

    private val progressListener: ProgressListenerAdapter

    /**
     * @since 2.4-rc-1
     */
    val buildProgressListener: FailsafeBuildProgressListenerAdapter

    /**
     * @since 1.0-milestone-3
     */
    val startTime: Long = System.currentTimeMillis()

    init {
        // create the listener adapters right when the ConsumerOperationParameters are instantiated but no earlier,
        // this ensures that when multiple requests are issued that are built from the same builder, such requests do not share any state kept in the listener adapters
        // e.g. if the listener adapters do per-request caching, such caching must not leak between different requests built from the same builder
        this.progressListener = ProgressListenerAdapter(this.legacyProgressListeners)
        this.buildProgressListener = FailsafeBuildProgressListenerAdapter(BuildProgressListenerAdapter(this.progressListeners))
    }

    fun containsHelpArg(): Boolean {
        return containsArg(IS_HELP_ARG)
    }

    fun containsVersionArg(): Boolean {
        return containsArg(IS_VERSION_ARG)
    }

    fun containsShowVersionArg(): Boolean {
        return containsArg(IS_SHOW_VERSION_ARG)
    }

    fun containsHelpOrVersionArgs(): Boolean {
        return containsArg(IS_HELP_OR_VERSION)
    }

    private fun containsArg(predicate: Predicate<String>): Boolean {
        return if (arguments == null) false else arguments.stream().filter(predicate).findFirst().isPresent()
    }

    fun withoutHelpOrVersionArgs(): ConsumerOperationParameters {
        val builder: Builder = builder()
        builder.copyFrom(this)
        builder.setArguments(arguments.stream().filter(IS_HELP_OR_VERSION.negate()).collect(Collectors.toList()))
        return builder.build()
    }

    fun withArgs(updateArgs: Function<MutableList<String>, MutableList<String>>): ConsumerOperationParameters {
        val builder: Builder = builder()
        builder.copyFrom(this)
        builder.setArguments(updateArgs.apply(this.arguments))
        return builder.build()
    }

    fun withNoTasks(): ConsumerOperationParameters {
        val builder: Builder = builder()
        builder.copyFrom(this)
        builder.setTasks(null)
        return builder.build()
    }

    val verboseLogging: Boolean
        get() = parameters.verboseLogging

    val gradleUserHomeDir: File
        /**
         * @since 1.0-milestone-3
         */
        get() = parameters.gradleUserHomeDir!!

    val projectDir: File
        /**
         * @since 1.0-milestone-3
         */
        get() = parameters.projectDir!!

    val isSearchUpwards: Boolean
        /**
         * @since 1.0-milestone-3
         */
        get() = parameters.isSearchUpwards!!

    val isEmbedded: Boolean
        /**
         * @since 1.0-milestone-3
         */
        get() = parameters.isEmbedded!!

    val daemonMaxIdleTimeUnits: TimeUnit
        /**
         * @since 1.0-milestone-3
         */
        get() = parameters.daemonMaxIdleTimeUnits!!

    val daemonMaxIdleTimeValue: Int
        /**
         * @since 1.0-milestone-3
         */
        get() = parameters.daemonMaxIdleTimeValue!!

    val daemonBaseDir: File
        /**
         * @since 2.2-rc-1
         */
        get() = parameters.daemonBaseDir!!

    val jvmArguments: MutableList<String>?
        get() {
            // Backport fix for https://github.com/gradle/gradle/issues/31462
            // Cross-version scenarios
            // - Old TAPI (8.12 and before) invokes new Gradle (8.13 and after):
            //     ProviderConnection will catch UnsupportedMethodException when calling getBaseJvmArgs() and will fall back to this method, but with the previous behavior (ie `return jvmArguments`)
            //     see https://github.com/gradle/gradle/blob/v8.12.0/platforms/ide/tooling-api/src/main/java/org/gradle/tooling/internal/consumer/parameters/ConsumerOperationParameters.java#L406
            // - New TAPI (8.13 and after) invokes old Gradle (8.12 and before):
            //     We try to approximate the behavior of the new Gradle by returning the combined list of JVM arguments defined in gradle.properties and in the TAPI client config; see the method body below.
            //     see https://github.com/gradle/gradle/blob/v8.12.0/platforms/core-runtime/launcher/src/main/java/org/gradle/tooling/internal/provider/ProviderConnection.java#L350

            if (baseJvmArguments == null && additionalJvmArguments == null) {
                // To keep the old behavior, when no JVM arguments are set, return null.
                return null
            } else {
                // Otherwise, return the combined list of JVM arguments defined in the gradle.properties file and in the TAPI client config.
                val arguments: MutableList<String> = ArrayList<String>()
                if (baseJvmArguments != null) {
                    arguments.addAll(baseJvmArguments)
                } else {
                    arguments.addAll(ConnectionConfigurationUtil.determineJvmArguments(parameters))
                }
                if (additionalJvmArguments != null) {
                    arguments.addAll(additionalJvmArguments)
                }
                return arguments
            }
        }

    /**
     * @since 2.8-rc-1
     */
    fun getInjectedPluginClasspath(): MutableList<File> {
        return injectedPluginClasspath.getAsFiles()
    }

    /**
     * @since 1.0-milestone-3
     */
    fun getProgressListener(): ProgressListenerVersion1 {
        return progressListener
    }

    fun getCancellationToken(): BuildCancellationToken {
        return (cancellationToken as CancellationTokenInternal).token!!
    }

    /**
     * @since 7.6
     */
    fun getSystemProperties(): MutableMap<String, *> {
        return systemProperties
    }

    /**
     * @since 8.6
     */
    fun onStreamedValue(model: Any) {
        streamedValueListener.onValue(model)
    }

    companion object {
        @JvmStatic
        fun builder(): Builder {
            return ConsumerOperationParameters.Builder()
        }

        private val IS_HELP_ARG = Predicate { s: String -> "--help" == s || "-h" == s || "-?" == s }
        private val IS_VERSION_ARG = Predicate { s: String -> "--version" == s || "-v" == s }
        private val IS_SHOW_VERSION_ARG = Predicate { s: String -> "--show-version" == s || "-V" == s }
        private val IS_HELP_OR_VERSION: Predicate<String> = IS_HELP_ARG.or(IS_VERSION_ARG).or(IS_SHOW_VERSION_ARG)

        private fun validateJavaHome(javaHome: File) {
            if (javaHome == null) {
                return
            }
            require(javaHome.isDirectory()) { "Supplied javaHome is not a valid folder. You supplied: " + javaHome }
        }
    }
}
