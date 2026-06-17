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
package org.gradle.tooling

import org.gradle.api.Incubating
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressListener
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Offers ways to communicate both ways with a Gradle operation, be it building a model or running tasks.
 *
 *
 * Enables tracking progress via listeners that will receive events from the Gradle operation.
 *
 *
 * Allows providing standard output streams that will receive output if the Gradle operation writes to standard streams.
 *
 *
 * Allows providing standard input that can be consumed by the gradle operation (useful for interactive builds).
 *
 *
 * Enables configuring the build run / model request with options like the Java home or JVM arguments.
 * Those settings might not be supported by the target Gradle version. Refer to Javadoc for those methods
 * to understand what kind of exception throw and when is it thrown.
 *
 * @since 1.0-milestone-7
 */
interface LongRunningOperation {
    /**
     * Sets the [OutputStream] which should receive standard output logging generated while running the operation.
     * The default is to discard the output.
     *
     * @param outputStream The output stream. The system default character encoding will be used to encode characters written to this stream.
     * @return this
     * @since 1.0-milestone-7
     */
    fun setStandardOutput(outputStream: OutputStream?): LongRunningOperation?

    /**
     * Sets the [OutputStream] which should receive standard error logging generated while running the operation.
     * The default is to discard the output.
     *
     * @param outputStream The output stream. The system default character encoding will be used to encode characters written to this stream.
     * @return this
     * @since 1.0-milestone-7
     */
    fun setStandardError(outputStream: OutputStream?): LongRunningOperation?

    /**
     * Specifies whether to generate colored (ANSI encoded) output for logging. The default is to not generate color output.
     *
     *
     * Supported by Gradle 2.3 or later. Ignored for older versions.
     *
     * @param colorOutput `true` to request color output (using ANSI encoding).
     * @return this
     * @since 2.3
     */
    fun setColorOutput(colorOutput: Boolean): LongRunningOperation?

    /**
     * Sets the [InputStream] that will be used as standard input for this operation.
     * Defaults to an empty input stream.
     *
     * @param inputStream The input stream
     * @return this
     * @since 1.0-milestone-8
     */
    fun setStandardInput(inputStream: InputStream?): LongRunningOperation?

    /**
     * Specifies the Java home directory to use for this operation.
     *
     *
     * [org.gradle.tooling.model.build.BuildEnvironment] model contains information such as Java or Gradle environment.
     * If you want to get hold of this information you can ask tooling API to build this model.
     *
     *
     * If not configured or null is passed, then the sensible default will be used.
     *
     * @param javaHome to use for the Gradle process
     * @return this
     * @throws IllegalArgumentException when supplied javaHome is not a valid folder.
     * @since 1.0-milestone-8
     */
    @Throws(IllegalArgumentException::class)
    fun setJavaHome(javaHome: File?): LongRunningOperation?

    /**
     * Specifies the Java VM arguments to use for this operation.
     *
     *
     * [org.gradle.tooling.model.build.BuildEnvironment] model contains information such as Java or Gradle environment.
     * If you want to get hold of this information you can ask tooling API to build this model.
     *
     *
     * If not configured, null, or an empty array is passed, then the reasonable default will be used.
     *
     *
     * The jvm argument set by this method is independent of arguments set by {`addJvmArguments`} methods.
     * The daemon JVM arguments list will always have the arguments from the {`setJvmArguments`} at the beginning of the list, and then have the {`addJvmArguments`} configuration appended.
     *
     * @param jvmArguments to use for the Gradle process
     * @return this
     * @since 1.0-milestone-8
     */
    fun setJvmArguments(vararg jvmArguments: String?): LongRunningOperation?

    /**
     * Appends Java VM arguments to the existing list.
     *
     *
     * The jvm argument set by this method is independent of arguments set by {`setJvmArguments`} methods.
     * The daemon JVM arguments list will always have the arguments from the {`setJvmArguments`} at the beginning of the list, and then have the {`addJvmArguments`} configuration appended.
     *
     * @param jvmArguments the argument to use for the Gradle process
     * @return this
     * @since 5.0
     */
    fun addJvmArguments(vararg jvmArguments: String?): LongRunningOperation?

    /**
     * Sets system properties to pass to the build.
     *
     *
     * By default, the Tooling API passes all system properties defined in the client to the build. If called, this method limits the system properties that are passed to the build, except for
     * immutable system properties that need to match on both sides.
     *
     *
     * System properties can be also defined in the build scripts (and in the gradle.properties file), or with a JVM argument. In case of an overlapping system property definition the precedence is as follows:
     *
     *  * `withSystemProperties(...)` (highest)
     *  * `addJvmArguments(...)` and `setJvmArguments(...)`
     *  * build scripts
     *
     *
     *
     * Note: this method has "setter" behavior, so the last invocation will overwrite previously set values.
     *
     * @param systemProperties the system properties add to the Gradle process. Passing `null` resets to the default behavior.
     * @return this
     * @since 7.6
     */
    @Incubating
    fun withSystemProperties(systemProperties: MutableMap<String?, String?>?): LongRunningOperation?

    /**
     * Appends Java VM arguments to the existing list.
     *
     *
     * The jvm argument set by this method is independent of arguments set by {`setJvmArguments`} methods.
     * The daemon JVM arguments list will always have the arguments from the {`setJvmArguments`} at the beginning of the list, and then have the {`addJvmArguments`} configuration appended.
     *
     * @param jvmArguments the argument to use for the Gradle process
     * @return this
     * @since 5.0
     */
    fun addJvmArguments(jvmArguments: Iterable<String?>?): LongRunningOperation?

    /**
     * Specifies the Java VM arguments to use for this operation.
     *
     *
     * [org.gradle.tooling.model.build.BuildEnvironment] model contains information such as Java or Gradle environment.
     * If you want to get hold of this information you can ask tooling API to build this model.
     *
     *
     * If not configured, null, or an empty list is passed, then the reasonable default will be used.
     *
     *
     * The jvm argument set by this method is independent of arguments set by {`addJvmArguments`} methods.
     * The daemon JVM arguments list will always have the arguments from the {`setJvmArguments`} at the beginning of the list, and then have the {`addJvmArguments`} configuration appended.
     *
     * @param jvmArguments to use for the Gradle process
     * @return this
     * @since 2.6
     */
    fun setJvmArguments(jvmArguments: Iterable<String?>?): LongRunningOperation?

    /**
     * Specify the command line build arguments. Useful mostly for running tasks via [BuildLauncher].
     *
     *
     * Be aware that not all of the Gradle command line options are supported!
     * Only the build arguments that configure the build execution are supported.
     * They are modelled in the Gradle API via [org.gradle.StartParameter].
     * Examples of supported build arguments: '--info', '-p'.
     * The command line instructions that are actually separate commands (like '-?' and '-v') are not supported.
     * Some other instructions like '--daemon' are also not supported - the tooling API always runs with the daemon.
     *
     *
     * If an unknown or unsupported command line option is specified, [org.gradle.tooling.exceptions.UnsupportedBuildArgumentException]
     * will be thrown at the time the operation is executed via [BuildLauncher.run] or [ModelBuilder.get].
     *
     *
     * For the list of all Gradle command line options please refer to the User Manual
     * or take a look at the output of the 'gradle -?' command. Majority of arguments modeled by
     * [org.gradle.StartParameter] are supported.
     *
     *
     * The arguments can potentially override some other settings you have configured.
     * For example, the project directory or Gradle user home directory that are configured
     * in the [GradleConnector].
     * Also, the task names configured by [BuildLauncher.forTasks] can be overridden
     * if you happen to specify other tasks via the build arguments.
     *
     *
     * See the example in the docs for [BuildLauncher]
     *
     * If not configured, null, or an empty array is passed, then the reasonable default will be used.
     *
     *
     * Requires Gradle 1.0 or later.
     *
     * @param arguments Gradle command line arguments
     * @return this
     * @since 1.0
     */
    fun withArguments(vararg arguments: String?): LongRunningOperation?

    /**
     * Specify the command line build arguments. Useful mostly for running tasks via [BuildLauncher].
     *
     *
     * If not configured, null, or an empty list is passed, then the reasonable default will be used.
     *
     *
     * Requires Gradle 1.0 or later.
     *
     * @param arguments Gradle command line arguments
     * @return this
     * @since 2.6
     */
    fun withArguments(arguments: Iterable<String?>?): LongRunningOperation?

    /**
     * Appends new command line arguments to the existing list. Useful mostly for running tasks via [BuildLauncher].
     *
     * @param arguments Gradle command line arguments
     * @return this
     * @since 5.0
     */
    fun addArguments(vararg arguments: String?): LongRunningOperation?

    /**
     * Appends new command line arguments to the existing list. Useful mostly for running tasks via [BuildLauncher].
     *
     * @param arguments Gradle command line arguments
     * @return this
     * @since 5.0
     */
    fun addArguments(arguments: Iterable<String?>?): LongRunningOperation?

    /**
     * Specifies the environment variables to use for this operation.
     *
     *
     * [org.gradle.tooling.model.build.BuildEnvironment] model contains information such as Java or Gradle environment.
     * If you want to get hold of this information you can ask tooling API to build this model.
     *
     *
     * If not configured or null is passed, then the reasonable default will be used.
     * *
     *
     *
     * Note: When this method is called, the provided map completely replaces the environment variables for the operation.
     * To add custom environment variables while preserving system environment variables, first copy the current environment
     * and then add custom values:
     * <pre>
     * Map&lt;String, String&gt; env = new HashMap&lt;&gt;(System.getenv());
     * env.put("MY_VAR", "my_value");
     * operation.setEnvironmentVariables(env);
    </pre> *
     * This is particularly important on Windows, where omitting essential system environment variables may cause the operation to fail.
     *
     * @param envVariables environment variables
     * @return this
     * @since 3.5
     */
    fun setEnvironmentVariables(envVariables: MutableMap<String?, String?>?): LongRunningOperation?

    /**
     * Adds a progress listener which will receive progress events as the operation runs.
     *
     *
     * This method is intended to be replaced by [.addProgressListener]. The new progress listener type
     * provides much richer information and much better handling of parallel operations that run during the build, such as tasks that run in parallel.
     * You should prefer using the new listener interface where possible. Note, however, that the new interface is supported only for Gradle 2.5.
     *
     *
     * @param listener The listener
     * @return this
     * @since 1.0-milestone-7
     */
    fun addProgressListener(listener: ProgressListener?): LongRunningOperation?

    /**
     * Adds a progress listener which will receive progress events of all types as the operation runs.
     *
     *
     * This method is intended to replace [.addProgressListener]. You should prefer using the new progress listener method where possible,
     * as the new interface provides much richer information and much better handling of parallel operations that run during the build.
     *
     *
     *
     * Supported by Gradle 2.5 or later. Gradle 2.4 supports [OperationType.TEST] operations only. Ignored for older versions.
     *
     * @param listener The listener
     * @return this
     * @since 2.5
     */
    fun addProgressListener(listener: ProgressListener?): LongRunningOperation?

    /**
     * Adds a progress listener which will receive progress events as the operations of the requested type run.
     *
     *
     * This method is intended to replace [.addProgressListener]. You should prefer using the new progress listener method where possible,
     * as the new interface provides much richer information and much better handling of parallel operations that run during the build.
     *
     *
     *
     * Supported by Gradle 2.5 or later. Gradle 2.4 supports [OperationType.TEST] operations only. Ignored for older versions.
     *
     * @param listener The listener
     * @param operationTypes The types of operations to receive progress events for.
     * @return this
     * @since 2.5
     */
    fun addProgressListener(listener: ProgressListener?, operationTypes: MutableSet<OperationType?>?): LongRunningOperation?

    /**
     * Adds a progress listener which will receive progress events as the operations of the requested type run.
     *
     *
     * This method is intended to replace [.addProgressListener]. You should prefer using the new progress listener method where possible,
     * as the new interface provides much richer information and much better handling of parallel operations that run during the build.
     *
     *
     *
     * Supported by Gradle 2.5 or later. Gradle 2.4 supports [OperationType.TEST] operations only. Ignored for older versions.
     *
     * @param listener The listener
     * @param operationTypes The types of operations to receive progress events for.
     * @return this
     * @since 2.6
     */
    fun addProgressListener(listener: ProgressListener?, vararg operationTypes: OperationType?): LongRunningOperation?

    /**
     * Sets the cancellation token to use to cancel the operation if required.
     *
     *
     * Supported by Gradle 2.1 or later. Ignored for older versions.
     *
     * @since 2.1
     */
    fun withCancellationToken(cancellationToken: CancellationToken?): LongRunningOperation?

    /**
     * Adds more detailed information about the build failure to the [GradleConnectionException] that provides insights into the reasons for the failure, making it easier to diagnose and fix issues.
     *
     * @return this
     * @since 8.12
     */
    @Incubating
    fun withDetailedFailure(): LongRunningOperation?
}
