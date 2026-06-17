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

import org.gradle.tooling.internal.consumer.ConnectorServices.createCancellationTokenSource
import org.gradle.tooling.internal.consumer.ConnectorServices.createConnector
import java.io.File
import java.net.URI

/**
 *
 * A `GradleConnector` is the main entry point to the Gradle tooling API. You use this API as follows:
 *
 *
 *
 *  1. Call [.newConnector] to create a new connector instance.
 *
 *  1. Configure the connector. You must call [.forProjectDirectory] to specify which project you wish to connect to. Other methods are optional.
 *
 *  1. Call [.connect] to create the connection to a project.
 *
 *  1. When finished with the connection, call [ProjectConnection.close] to clean up.
 *
 *
 *
 *
 * Example:
 * <pre class='autoTested'>
 * ProjectConnection connection = GradleConnector.newConnector()
 * .forProjectDirectory(new File("someProjectFolder"))
 * .connect();
 *
 * try {
 * connection.newBuild().forTasks("tasks").run();
 * } finally {
 * connection.close();
 * }
</pre> *
 *
 *
 * The connection will use the version of Gradle that the target build is configured to use, for example in the Gradle wrapper properties file. When no Gradle version is defined for the build, the connection will use the tooling API's version as the Gradle version to run the build.
 * Generally, you should avoid configuring a Gradle distribution or version and instead use the default provided by the tooling API.
 *
 *
 *
 * Similarly, the connection will use the JVM and JVM arguments that the target build is configured to use, for example in the `gradle.properties` file. When no JVM or JVM arguments are defined for the build, the connection will use the current JVM and some default JVM arguments.
 *
 *
 * `GradleConnector` instances are not thread-safe. If you want to use a `GradleConnector` concurrently you *must* always create a
 * new instance for each thread using [.newConnector]. Note, however, the [ProjectConnection] instances that a connector creates are completely thread-safe.
 *
 * <h2>Gradle version compatibility</h2>
 *
 *
 * The Tooling API is both forwards and backwards compatible with other versions of Gradle. It supports execution of Gradle builds that use older or newer versions of Gradle.  Each release of Gradle contains a new release of the Tooling API as well.
 *
 *
 * The Tooling API supports running builds using Gradle version 4.0 and up.
 *
 *
 * You should note that not all features of the Tooling API are available for all versions of Gradle. Refer to the documentation for each class and method for more details.
 *
 *
 * Builds using Gradle 5.0 and up require the use of Tooling API version 3.0 or later.
 *
 * <h2>Java version compatibility</h2>
 *
 *
 * The Tooling API requires Java 8 or later. The Gradle version used by builds may have additional Java version requirements.
 *
 * @since 1.0-milestone-3
 */
abstract class GradleConnector {
    /**
     * Specifies which Gradle installation to use. This replaces any value specified using [.useDistribution], [.useGradleVersion], or [.useBuildDistribution].
     * Defaults to a project-specific Gradle version.
     *
     * @param gradleHome The Gradle installation directory.
     * @return this
     * @since 1.0-milestone-3
     */
    abstract fun useInstallation(gradleHome: File?): GradleConnector?

    /**
     * Specifies which Gradle version to use. The appropriate distribution is downloaded and installed into the user's Gradle home directory. This replaces any value specified using [ ][.useInstallation], [.useDistribution], or [.useBuildDistribution]. Defaults to a project-specific Gradle version.
     *
     * @param gradleVersion The version to use.
     * @return this
     * @since 1.0-milestone-3
     */
    abstract fun useGradleVersion(gradleVersion: String?): GradleConnector?

    /**
     * Specifies which Gradle distribution to use. The appropriate distribution is downloaded and installed into the user's Gradle home directory. This replaces any value specified using [ ][.useInstallation], [.useGradleVersion], or [.useBuildDistribution]. Defaults to a project-specific Gradle version.
     *
     * @param gradleDistribution The distribution to use.
     * @return this
     * @since 1.0-milestone-3
     */
    abstract fun useDistribution(gradleDistribution: URI?): GradleConnector?

    /**
     * Specifies to use the Gradle distribution defined by the target Gradle build. The appropriate distribution defined by the target Gradle build is downloaded and installed into the user's
     * Gradle home directory. If the target Gradle build does not define the distribution that it should be built with, the Gradle version of this connector is used. This replaces any value
     * specified using [.useInstallation], [.useDistribution], or [.useGradleVersion]. Acts as the default behavior.
     *
     * @return this
     * @since 2.3
     */
    abstract fun useBuildDistribution(): GradleConnector?

    /**
     * Specifies the working directory to use.
     *
     * @param projectDir The working directory.
     * @return this
     * @since 1.0-milestone-3
     */
    abstract fun forProjectDirectory(projectDir: File?): GradleConnector?

    /**
     * Specifies the user's Gradle home directory to use. Defaults to `~/.gradle`.
     *
     * @param gradleUserHomeDir The user's Gradle home directory to use.
     * @return this
     * @since 1.0-milestone-3
     */
    abstract fun useGradleUserHomeDir(gradleUserHomeDir: File?): GradleConnector?

    /**
     * Creates a connection to the project in the specified project directory. You should call [ProjectConnection.close] when you are finished with the connection.
     *
     *
     *
     * Note, that the returned instance does not automatically pick up changes if the connection configuration (e.g. the gradle.properties file) changes. It's the client's responsibility to close the connection and create a new one in that scenario.
     *
     * @return The connection. Never return null.
     * @throws UnsupportedVersionException When the target Gradle version does not support this version of the tooling API.
     * @throws GradleConnectionException On failure to establish a connection with the target Gradle version.
     * @since 1.0-milestone-3
     */
    @Throws(GradleConnectionException::class)
    abstract fun connect(): ProjectConnection?

    /**
     * Disconnects all ProjectConnection instances created by this connector.
     *
     *
     *
     * Calling this method tries to do a best effort to clean up resources used by the tooling API.
     * It tries to cancel any builds and shut down running daemons.
     *
     *
     *
     * After calling `disconnect`, creating new project connections will be rejected and the existing ones
     * created by this instance will also deny future build operations.
     *
     * @since 6.5
     */
    abstract fun disconnect()

    companion object {
        /**
         * Creates a new connector instance.
         *
         * @return The instance. Never returns null.
         * @since 1.0-milestone-3
         */
        @JvmStatic
        fun newConnector(): GradleConnector {
            return createConnector()
        }

        /**
         * Creates a new [CancellationTokenSource] that can be used to cancel one or more [LongRunningOperation] executions.
         *
         * @return The instance. Never returns `null`.
         * @since 2.1
         */
        fun newCancellationTokenSource(): CancellationTokenSource {
            return createCancellationTokenSource()
        }
    }
}
