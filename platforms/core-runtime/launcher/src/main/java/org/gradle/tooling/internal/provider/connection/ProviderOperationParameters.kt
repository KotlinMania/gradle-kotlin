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
package org.gradle.tooling.internal.provider.connection

/**
 * Defines what information is needed on the provider side regarding the build operation.
 *
 * This is used as an adapter over the [org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters] instance provided by the consumer.
 *
 * Adding a new parameter and exposing it to TAPI clients requires declaring a getter here,
 * adding it to `ConsumerOperationParameters`,
 * and updating the public [org.gradle.tooling.LongRunningOperation].
 *
 * For backwards compatibility, the provider need to provide a default value.
 * Let the getter take a parameter of the same type as its return type.
 * When the getter is called and `ConsumerOperationParameters` does not have a corresponding getter (without a parameter), the default value from the parameter is returned immediately.
 */
interface ProviderOperationParameters {
    val verboseLogging: Boolean

    @JvmField
    val buildLogLevel: LogLevel?

    /**
     * @return When null, assume empty stdin (rather than consume from the current process' stdin).
     */
    @JvmField
    val standardInput: InputStream?

    /**
     * @return When null, use the provider's default Java home.
     */
    @JvmField
    val javaHome: File?

    /**
     * Returns the concatenated list of [.getBaseJvmArguments] and [.getAdditionalJvmArguments]. The method is only kept for backwards compatibility (see #31462) to support Gradle versions where [.getBaseJvmArguments] and  [.getAdditionalJvmArguments] is not yet available.
     *
     * @return null if no JVM arguments are provided, otherwise a concatenated list of JVM arguments.
     */
    @JvmField
    val jvmArguments: MutableList<String?>?

    /**
     * Arguments, which will override the default JVM arguments.
     *
     * @return null if no JVM arguments are provided, otherwise a list of JVM arguments.
     */
    @JvmField
    val baseJvmArguments: MutableList<String?>?

    /**
     * Additional arguments, which will be appended to the default/overridden JVM arguments.
     *
     * @return null if no additional JVM arguments are provided, otherwise a list of additional JVM arguments.
     */
    @JvmField
    val additionalJvmArguments: MutableList<String?>?

    /**
     * @return When null, use the provider's default environment variables. When empty, use no environment variables.
     * @since 3.5-rc-1
     */
    fun getEnvironmentVariables(defaultValue: MutableMap<String?, String?>?): MutableMap<String?, String?>?

    /**
     * @since 1.0-milestone-3
     */
    @JvmField
    val startTime: Long

    /**
     * @return When null, use the provider's default Gradle user home dir.
     * @since 1.0-milestone-3
     */
    @JvmField
    val gradleUserHomeDir: File?

    /**
     * @since 1.0-milestone-3
     */
    @JvmField
    val projectDir: File?


    /**
     * @return When null, use the provider's default value for embedded.
     * @since 1.0-milestone-3
     */
    @JvmField
    val isEmbedded: Boolean?

    /**
     * @return When null, use the provider's default value for color output.
     * @since 2.3-rc-1
     */
    @JvmField
    val isColorOutput: Boolean?

    /**
     * @return When null, discard the stdout (rather than forward to the current process' stdout)
     * @since 1.0-milestone-3
     */
    @JvmField
    val standardOutput: OutputStream?

    /**
     * @return When null, discard the stderr (rather than forward to the current process' stdout)
     * @since 1.0-milestone-3
     */
    @JvmField
    val standardError: OutputStream?

    /**
     * @return When null, use the provider's default daemon idle timeout
     * @since 1.0-milestone-3
     */
    @JvmField
    val daemonMaxIdleTimeValue: Int?

    /**
     * @return Must not return null when [.getDaemonMaxIdleTimeValue] returns a non-null value. Otherwise, unspecified.
     * @since 1.0-milestone-3
     */
    @JvmField
    val daemonMaxIdleTimeUnits: TimeUnit?

    /**
     * @return When null, use the provider's default daemon base dir.
     * @since 2.2-rc-1
     */
    @JvmField
    val daemonBaseDir: File?

    /**
     * @since 1.0-milestone-3
     */
    @JvmField
    val progressListener: ProgressListenerVersion1?

    /**
     * @return When null, do not forward any build progress events.
     * @since 2.4-rc-1
     */
    @JvmField
    val buildProgressListener: InternalBuildProgressListener?

    /**
     * @return When null, assume no arguments.
     */
    @JvmField
    val arguments: MutableList<String?>?

    /**
     * @return When null, no tasks should be run. When empty, use the default tasks
     */
    @JvmField
    val tasks: MutableList<String?>?

    /**
     * @since 1.12-rc-1
     */
    @JvmField
    val launchables: MutableList<InternalLaunchable?>?

    /**
     * @return When empty, do not inject a plugin classpath.
     * @since 2.8-rc-1
     */
    @JvmField
    val injectedPluginClasspath: MutableList<File?>?

    /**
     * @return Additional system properties defined by the client to be available in the build.
     * @since 7.6
     */
    fun getSystemProperties(defaultValue: MutableMap<String?, String?>?): MutableMap<String?, String?>?

    /**
     * Handles a value streamed from the build action. Blocks until the value has been handled.
     *
     *
     * This method is called from the provider's message handling loop in the client process, so is required to block until handling is complete so as to preserve
     * the message ordering.
     *
     * @since 8.6
     */
    fun onStreamedValue(value: Any?)
}
