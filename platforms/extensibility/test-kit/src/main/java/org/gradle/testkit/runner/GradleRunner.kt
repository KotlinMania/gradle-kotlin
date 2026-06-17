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
package org.gradle.testkit.runner

import org.gradle.api.Incubating
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import java.io.File
import java.io.Writer
import java.net.URI

/**
 * Executes a Gradle build, allowing inspection of the outcome.
 *
 *
 * A Gradle runner can be used to functionally test build logic, by executing a contrived build.
 * Assertions can then be made on the outcome of the build, such as the state of files created by the build,
 * or what tasks were actually executed during the build.
 *
 *
 * A runner can be created via the [.create] method.
 *
 *
 * Typically, the test code using the runner will programmatically create a build (e.g. by writing Gradle build files to a temporary space) to execute.
 * The build to execute is effectively specified by the [.withProjectDir]} method.
 * It is a requirement that a project directory be set.
 *
 *
 * The [.withArguments] method allows the build arguments to be specified,
 * just as they would be on the command line.
 *
 *
 * The [.build] method can be used to invoke the build when it is expected to succeed,
 * while the [.buildAndFail] method can be used when the build is expected to fail.
 *
 *
 * GradleRunner instances are not thread safe and cannot be used concurrently.
 * However, multiple instances are able to be used concurrently.
 *
 *
 * On Windows, Gradle runner disables file system watching for the executed build, since the Windows watchers add a file lock
 * on the root project directory, causing problems when trying to delete it. You can still enable file system watching manually
 * for your test by adding the `--watch-fs` command line argument via [.withArguments].
 *
 *
 * Please see the Gradle [TestKit](https://docs.gradle.org/current/userguide/test_kit.html) User Manual chapter for more information.
 *
 * @since 2.6
 */
abstract class GradleRunner {
    /**
     * Configures the runner to execute the build with the version of Gradle specified.
     *
     *
     * Unless previously downloaded, this method will cause the Gradle runtime for the version specified
     * to be downloaded over the Internet from Gradle's distribution servers.
     * The download will be cached beneath the Gradle User Home directory, the location of which is determined by the following in order of precedence:
     *
     *  1. The system property `"gradle.user.home"`
     *  1. The environment variable `"GRADLE_USER_HOME"`
     *
     *
     *
     * If neither are present, `"~/.gradle"` will be used, where `"~"` is the value advertised by the JVM's `"user.dir"` system property.
     * The system property and environment variable are read in the process using the runner, not the build process.
     *
     *
     * Alternatively, you may use [.withGradleInstallation] to use an installation already on the filesystem.
     *
     *
     * To use a non standard Gradle runtime, or to obtain the runtime from an alternative location, use [.withGradleDistribution].
     *
     * @param versionNumber the version number (e.g. "2.9")
     * @return this
     * @since 2.9
     * @see .withGradleInstallation
     * @see .withGradleDistribution
     */
    abstract fun withGradleVersion(versionNumber: String?): GradleRunner?

    /**
     * Configures the runner to execute the build using the installation of Gradle specified.
     *
     *
     * The given file must be a directory containing a valid Gradle installation.
     *
     *
     * Alternatively, you may use [.withGradleVersion] to use an automatically installed Gradle version.
     *
     * @param installation a valid Gradle installation
     * @return this
     * @since 2.9
     * @see .withGradleVersion
     * @see .withGradleDistribution
     */
    abstract fun withGradleInstallation(installation: File?): GradleRunner?

    /**
     * Configures the runner to execute the build using the distribution of Gradle specified.
     *
     *
     * The given URI must point to a valid Gradle distribution ZIP file.
     * This method is typically used as an alternative to [.withGradleVersion],
     * where it is preferable to obtain the Gradle runtime from "local" servers.
     *
     *
     * Unless previously downloaded, this method will cause the Gradle runtime at the given URI to be downloaded.
     * The download will be cached beneath the Gradle User Home directory, the location of which is determined by the following in order of precedence:
     *
     *  1. The system property `"gradle.user.home"`
     *  1. The environment variable `"GRADLE_USER_HOME"`
     *
     *
     *
     * If neither are present, `"~/.gradle"` will be used, where `"~"` is the value advertised by the JVM's `"user.dir"` system property.
     * The system property and environment variable are read in the process using the runner, not the build process.
     *
     * @param distribution a URI pointing at a valid Gradle distribution zip file
     * @return this
     * @since 2.9
     * @see .withGradleVersion
     * @see .withGradleInstallation
     */
    abstract fun withGradleDistribution(distribution: URI?): GradleRunner?

    /**
     * Sets the directory to use for TestKit's working storage needs.
     *
     *
     * This directory is used internally to store various files required by the runner.
     * If no explicit Gradle user home is specified via the build arguments (i.e. the `-g «dir»` option}),
     * this directory will also be used for the Gradle user home for the test build.
     *
     *
     * If no value has been specified when the build is initiated, a directory will be created within a temporary directory.
     *
     *  * When executed from a Gradle Test task, the Test task's temporary directory is used (see [org.gradle.api.Task.getTemporaryDir]).
     *  * When executed from somewhere else, the system's temporary directory is used (based on `java.io.tmpdir`).
     *
     *
     *
     * This directory is not deleted by the runner after the test build.
     *
     *
     * You may wish to specify a location that is within your project and regularly cleaned, such as the project's build directory.
     *
     *
     * It can be set using the system property `org.gradle.testkit.dir` for the test process,
     *
     *
     * The actual contents of this directory are an internal implementation detail and may change at any time.
     *
     * @param testKitDir the TestKit directory
     * @return `this`
     * @since 2.7
     */
    abstract fun withTestKitDir(testKitDir: File?): GradleRunner?

    /**
     * The directory that the build will be executed in.
     *
     *
     * This is analogous to the current directory when executing Gradle from the command line.
     *
     * @return the directory to execute the build in
     */
    abstract val projectDir: File?

    /**
     * Sets the directory that the Gradle will be executed in.
     *
     *
     * This is typically set to the root project of the build under test.
     *
     *
     * A project directory must be set.
     * This method must be called before [.build] or [.buildAndFail].
     *
     *
     * All builds executed with the runner effectively do not search parent directories for a `settings.gradle` file.
     * This suppresses Gradle's default behaviour of searching upwards through the file system in order to find the root of the current project tree.
     * This default behaviour is often utilised when focusing on a particular build within a multi-project build.
     * This behaviour is suppressed due to test builds being executed potentially being created within a "real build"
     * (e.g. under the `/build` directory of the plugin's project directory).
     *
     * @param projectDir the project directory
     * @return `this`
     * @see .getProjectDir
     */
    abstract fun withProjectDir(projectDir: File?): GradleRunner?

    /**
     * The build arguments.
     *
     *
     * Effectively, the command line arguments to Gradle.
     * This includes all tasks, flags, properties etc.
     *
     *
     * The returned list is immutable.
     *
     * @return the build arguments
     */
    abstract val arguments: MutableList<String?>?

    /**
     * Sets the build arguments.
     *
     * @param arguments the build arguments
     * @return this
     * @see .getArguments
     */
    abstract fun withArguments(arguments: MutableList<String?>?): GradleRunner?

    /**
     * Sets the build arguments.
     *
     * @param arguments the build arguments
     * @return this
     * @see .getArguments
     */
    abstract fun withArguments(vararg arguments: String?): GradleRunner?

    /**
     * The injected plugin classpath for the build.
     *
     *
     * The returned list is immutable.
     * Returns an empty list if no classpath was provided with [.withPluginClasspath].
     *
     * @return the classpath of plugins to make available to the build under test
     * @since 2.8
     */
    abstract val pluginClasspath: MutableList<out File?>?

    /**
     * Sets the plugin classpath based on the Gradle plugin development plugin conventions.
     *
     *
     * The 'java-gradle-plugin' generates a file describing the plugin under test and makes it available to the test runtime.
     * This method configures the runner to use this file.
     * Please consult the Gradle documentation of this plugin for more information.
     *
     *
     * This method looks for a file named `plugin-under-test-metadata.properties` on the runtime classpath,
     * and uses the `implementation-classpath` as the classpath, which is expected to a [File.pathSeparatorChar] joined string.
     * If the plugin metadata file cannot be resolved an [InvalidPluginMetadataException] is thrown.
     *
     *
     * Plugins from classpath are able to be resolved using the `plugins { }` syntax in the build under test.
     * Please consult the TestKit Gradle User Manual chapter for more information and usage examples.
     *
     *
     * Calling this method will replace any previous classpath specified via [.withPluginClasspath] and vice versa.
     *
     *
     * **Note:** this method will cause an [InvalidRunnerConfigurationException] to be emitted when the build is executed,
     * if the version of Gradle executing the build (i.e. not the version of the runner) is earlier than Gradle 2.8 as those versions do not support this feature.
     * Please consult the TestKit Gradle User Manual chapter alternative strategies that can be used for older Gradle versions.
     *
     * @return this
     * @see .withPluginClasspath
     * @see .getPluginClasspath
     * @since 2.13
     */
    @Throws(InvalidPluginMetadataException::class)
    abstract fun withPluginClasspath(): GradleRunner?

    /**
     * Sets the injected plugin classpath for the build.
     *
     *
     * Plugins from the given classpath are able to be resolved using the `plugins { }` syntax in the build under test.
     * Please consult the TestKit Gradle User Manual chapter for more information and usage examples.
     *
     *
     * **Note:** this method will cause an [InvalidRunnerConfigurationException] to be emitted when the build is executed,
     * if the version of Gradle executing the build (i.e. not the version of the runner) is earlier than Gradle 2.8 as those versions do not support this feature.
     * Please consult the TestKit Gradle User Manual chapter alternative strategies that can be used for older Gradle versions.
     *
     * @param classpath the classpath of plugins to make available to the build under test
     * @return this
     * @see .getPluginClasspath
     * @since 2.8
     */
    abstract fun withPluginClasspath(classpath: Iterable<out File?>?): GradleRunner?

    /**
     * Indicates whether the build should be executed "in process" so that it is debuggable.
     *
     *
     * If debug support is not enabled, the build will be executed in an entirely separate process.
     * This means that any debugger that is attached to the test execution process will not be attached to the build process.
     * When debug support is enabled, the build is executed in the same process that is using the Gradle Runner, allowing the build to be debugged.
     *
     *
     * Debug support is off (i.e. `false`) by default.
     * It can be enabled by setting the system property `org.gradle.testkit.debug` to `true` for the test process,
     * or by using the [.withDebug] method.
     *
     *
     * When [.withEnvironment] is specified, running with debug is not allowed.
     * Debug mode runs "in process" and we need to fork a separate process to pass environment variables.
     *
     * @return whether the build should be executed in the same process
     * @since 2.9
     */
    abstract val isDebug: Boolean

    /**
     * Sets whether debugging support is enabled.
     *
     * @see .isDebug
     * @param flag the debug flag
     * @return this
     * @since 2.9
     */
    abstract fun withDebug(flag: Boolean): GradleRunner?

    /**
     * Environment variables for the build.
     * `null` is valid and indicates the build will use the system environment.
     *
     * @return environment variables
     * @since 5.2
     */
    abstract val environment: MutableMap<String?, String?>?

    /**
     * Sets the environment variables for the build.
     * `null` is permitted and will make the build use the system environment.
     *
     *
     * When environment is specified, running with [.isDebug] is not allowed.
     * Debug mode runs in-process and TestKit must fork a separate process to pass environment variables.
     *
     * @param environmentVariables the variables to use, `null` is allowed.
     * @return this
     * @since 5.2
     */
    abstract fun withEnvironment(environmentVariables: MutableMap<String?, String?>?): GradleRunner?

    /**
     * Configures the runner to forward standard output from builds to the given writer.
     *
     *
     * The output of the build is always available via [BuildResult.getOutput].
     * This method can be used to additionally capture the output.
     *
     *
     * Calling this method will negate the effect of previously calling [.forwardOutput].
     *
     *
     * The given writer will not be closed by the runner.
     *
     *
     * When executing builds with Gradle versions earlier than 2.9 **in debug mode**, any output produced by the build that
     * was written directly to `System.out` or `System.err` will not be represented in [BuildResult.getOutput].
     * This is due to a defect that was fixed in Gradle 2.9.
     *
     * @param writer the writer that build standard output should be forwarded to
     * @return this
     * @since 2.9
     * @see .forwardOutput
     * @see .forwardStdError
     */
    abstract fun forwardStdOutput(writer: Writer?): GradleRunner?

    /**
     * Configures the runner to forward standard error output from builds to the given writer.
     *
     *
     * The output of the build is always available via [BuildResult.getOutput].
     * This method can be used to additionally capture the error output.
     *
     *
     * Calling this method will negate the effect of previously calling [.forwardOutput].
     *
     *
     * The given writer will not be closed by the runner.
     *
     * @param writer the writer that build standard error output should be forwarded to
     * @return this
     * @since 2.9
     * @see .forwardOutput
     * @see .forwardStdOutput
     */
    abstract fun forwardStdError(writer: Writer?): GradleRunner?

    /**
     * Forwards the output of executed builds to the [System.out] stream.
     *
     *
     * The output of the build is always available via [BuildResult.getOutput].
     * This method can be used to additionally forward the output to `System.out` of the process using the runner.
     *
     *
     * This method does not separate the standard output and error output.
     * The two streams will be merged as they typically are when using Gradle from a command line interface.
     * If you require separation of the streams, you can use [.forwardStdOutput] and [.forwardStdError] directly.
     *
     *
     * Calling this method will negate the effect of previously calling [.forwardStdOutput] and/or [.forwardStdError].
     *
     * @return this
     * @since 2.9
     * @see .forwardStdOutput
     * @see .forwardStdError
     */
    abstract fun forwardOutput(): GradleRunner?

    /**
     * Executes a build, expecting it to complete without failure.
     *
     * @throws InvalidRunnerConfigurationException if the configuration of this runner is invalid (e.g. project directory not set)
     * @throws UnexpectedBuildFailure if the build does not succeed
     * @return the build result
     */
    @Throws(InvalidRunnerConfigurationException::class, UnexpectedBuildFailure::class)
    abstract fun build(): BuildResult?

    /**
     * Executes a build, expecting it to complete with failure.
     *
     * @throws InvalidRunnerConfigurationException if the configuration of this runner is invalid (e.g. project directory not set)
     * @throws UnexpectedBuildSuccess if the build succeeds
     * @return the build result
     */
    @Throws(InvalidRunnerConfigurationException::class, UnexpectedBuildSuccess::class)
    abstract fun buildAndFail(): BuildResult?

    /**
     * Executes a build, without expecting a particular outcome.
     * The outcome should then be tested by inspecting the returned [BuildResult].
     *
     * @throws InvalidRunnerConfigurationException if the configuration of this runner is invalid (e.g. project directory not set)
     * @return the build result
     * @since 8.0
     */
    @Incubating
    @Throws(InvalidRunnerConfigurationException::class)
    abstract fun run(): BuildResult?

    companion object {
        /**
         * Creates a new Gradle runner.
         *
         *
         * The runner requires a Gradle distribution (and therefore a specific version of Gradle) in order to execute builds.
         * This method will find a Gradle distribution, based on the filesystem location of this class.
         * That is, it is expected that this class is loaded from a Gradle distribution.
         *
         *
         * When using the runner as part of tests *being executed by Gradle* (i.e. a build using the `gradleTestKit()` dependency),
         * this means that the same distribution of Gradle that is executing the tests will be used by runner returned by this method.
         *
         *
         * When using the runner as part of tests *being executed by an IDE*,
         * this means that the same distribution of Gradle that was used when importing the project will be used.
         *
         * @return a new Gradle runner
         */
        fun create(): GradleRunner {
            return DefaultGradleRunner()
        }
    }
}
