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
package org.gradle.tooling.internal.consumer

import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.internal.FileUtils
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.time.Clock
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.internal.consumer.ConnectionConfigurationUtil.determineRealUserHomeDir
import org.gradle.tooling.internal.consumer.ConnectionConfigurationUtil.determineRootDir
import org.gradle.tooling.internal.consumer.ConnectionConfigurationUtil.determineSystemProperties
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.util.GradleVersion
import org.gradle.util.internal.DistributionLocator
import org.gradle.wrapper.WrapperConfiguration
import org.gradle.wrapper.WrapperExecutor.Companion.forProjectDirectory
import java.io.File
import java.io.FileFilter
import java.io.FileNotFoundException
import java.net.URI
import java.util.Arrays
import java.util.concurrent.CancellationException

class DistributionFactory(private val clock: Clock?) {
    /**
     * Returns the default distribution to use for the specified project.
     */
    fun getDefaultDistribution(projectDir: File, searchUpwards: Boolean): Distribution {
        val layout = BuildLayoutFactory().getLayoutFor(projectDir, searchUpwards)
        val wrapper = forProjectDirectory(layout.getRootDirectory())
        if (wrapper.distribution != null) {
            return DistributionFactory.ZippedDistribution(wrapper.configuration, clock)
        }
        return getDownloadedDistribution(GradleVersion.current().getVersion())
    }

    /**
     * Returns the distribution installed in the specified directory.
     */
    fun getDistribution(gradleHomeDir: File): Distribution {
        return InstalledDistribution(
            gradleHomeDir, "Gradle installation '" + gradleHomeDir + "'",
            "Gradle installation directory '" + gradleHomeDir + "'"
        )
    }

    /**
     * Returns the distribution for the specified gradle version.
     */
    fun getDistribution(gradleVersion: String?): Distribution {
        return getDownloadedDistribution(gradleVersion)
    }

    /**
     * Returns the distribution at the given URI.
     */
    fun getDistribution(gradleDistribution: URI?): Distribution {
        val configuration = WrapperConfiguration()
        configuration.distribution = gradleDistribution
        return DistributionFactory.ZippedDistribution(configuration, clock)
    }

    private fun getDownloadedDistribution(gradleVersion: String?): Distribution {
        val distUri = DistributionLocator().getDistributionFor(GradleVersion.version(gradleVersion))
        return getDistribution(distUri)
    }

    class ZippedDistribution private constructor(private val wrapperConfiguration: WrapperConfiguration, private val clock: Clock?) : Distribution {
        private var installedDistribution: InstalledDistribution? = null

        override fun getDisplayName(): String {
            return "Gradle distribution '" + wrapperConfiguration.distribution + "'"
        }

        override fun getToolingImplementationClasspath(
            progressLoggerFactory: ProgressLoggerFactory?,
            progressListener: InternalBuildProgressListener?,
            connectionParameters: ConnectionParameters,
            cancellationToken: BuildCancellationToken
        ): ClassPath {
            if (installedDistribution == null) {
                val installer = DistributionInstaller(progressLoggerFactory, progressListener, clock, wrapperConfiguration.networkTimeout)
                val installDir: File
                try {
                    cancellationToken.addCallback(object : Runnable {
                        override fun run() {
                            installer.cancel()
                        }
                    })
                    installDir =
                        installer.install(determineRealUserHomeDir(connectionParameters), determineRootDir(connectionParameters), wrapperConfiguration, determineSystemProperties(connectionParameters))
                } catch (e: CancellationException) {
                    throw BuildCancelledException(String.format("Distribution download cancelled. Using distribution from '%s'.", wrapperConfiguration.distribution), e)
                } catch (e: FileNotFoundException) {
                    throw IllegalArgumentException(String.format("The specified %s does not exist.", getDisplayName()), e)
                } catch (e: Exception) {
                    throw GradleConnectionException(String.format("Could not install Gradle distribution from '%s'.", wrapperConfiguration.distribution), e)
                }
                installedDistribution = InstalledDistribution(installDir, getDisplayName(), getDisplayName())
            }
            return installedDistribution!!.getToolingImplementationClasspath(progressLoggerFactory, progressListener, connectionParameters, cancellationToken)
        }
    }

    private class InstalledDistribution(private val gradleHomeDir: File, private val displayName: String?, private val locationDisplayName: String?) : Distribution {
        override fun getDisplayName(): String? {
            return displayName
        }

        override fun getToolingImplementationClasspath(
            progressLoggerFactory: ProgressLoggerFactory?,
            progressListener: InternalBuildProgressListener?,
            connectionParameters: ConnectionParameters?,
            cancellationToken: BuildCancellationToken?
        ): ClassPath {
            require(gradleHomeDir.exists()) { String.format("The specified %s does not exist.", locationDisplayName) }
            require(gradleHomeDir.isDirectory()) { String.format("The specified %s is not a directory.", locationDisplayName) }
            // The lib directory implements a cross-gradle-version contract, where the
            // TAPI consumer will load the TAPI provider classpath from the
            // `lib` directory of the target gradle distribution.
            val libDir = File(gradleHomeDir, "lib")
            require(libDir.isDirectory()) { String.format("The specified %s does not appear to contain a Gradle distribution.", locationDisplayName) }
            val files = libDir.listFiles(object : FileFilter {
                override fun accept(file: File): Boolean {
                    return FileUtils.hasExtension(file, ".jar")
                }
            })
            // Make sure file order is always consistent
            Arrays.sort(files)
            return DefaultClassPath.of(*files)
        }
    }
}
