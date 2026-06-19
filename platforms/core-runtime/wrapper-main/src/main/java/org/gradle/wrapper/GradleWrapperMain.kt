/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.wrapper

import org.gradle.cli.CommandLineParser
import org.gradle.cli.ParsedCommandLine
import org.gradle.cli.SystemPropertiesCommandLineConverter
import org.jspecify.annotations.NullMarked
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Paths

object GradleWrapperMain {
    const val GRADLE_USER_HOME_OPTION: String = "g"
    const val GRADLE_USER_HOME_DETAILED_OPTION: String = "gradle-user-home"
    const val GRADLE_QUIET_OPTION: String = "q"
    const val GRADLE_QUIET_DETAILED_OPTION: String = "quiet"

    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val wrapperJar = wrapperJar()
        GradleWrapperMain.prepareWrapper(args, wrapperJar).execute()
    }

    @Throws(Exception::class)
    private fun prepareWrapper(args: Array<String>, wrapperJar: File): Action {
        val propertiesFile = wrapperProperties(wrapperJar)
        val rootDir = rootDir(wrapperJar)

        val parser = CommandLineParser()
        parser.allowUnknownOptions()
        parser.option(GRADLE_USER_HOME_OPTION, GRADLE_USER_HOME_DETAILED_OPTION).hasArgument()
        parser.option(GRADLE_QUIET_OPTION, GRADLE_QUIET_DETAILED_OPTION)

        val converter = SystemPropertiesCommandLineConverter()
        converter.configure(parser)
        val options = parser.parse(*args)

        val commandLineSystemProperties = converter.convert(options, HashMap<String?, String?>())
        val projectSystemProperties = PropertiesFileHandler.getSystemProperties(File(rootDir, "gradle.properties"))
        /** If the Gradle system properties may define a custom Gradle home, which needs to be set before loading user gradle.properties */
        maybeAddGradleUserHomeSystemProperty(projectSystemProperties, commandLineSystemProperties)
        val gradleUserHome = gradleUserHome(options)
        val userGradleProperties = File(gradleUserHome, "gradle.properties")
        val userSystemProperties: MutableMap<String?, String?> = HashMap<String?, String?>(PropertiesFileHandler.getSystemProperties(userGradleProperties))
        // Inception: Gradle user home cannot be changed with configuration from the Gradle user home
        val invalidGradleUserHome = userSystemProperties.remove(GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY) != null
        // Set all system properties from all Gradle sources with correct precedence: project (lowest) < user < cli (highest)
        addSystemProperties(projectSystemProperties, userSystemProperties, commandLineSystemProperties)

        val logger = logger(options)

        if (invalidGradleUserHome) {
            logger.log("WARNING Ignored custom Gradle user home location configured in Gradle user home: " + userGradleProperties.getAbsolutePath())
        }

        val wrapperExecutor = WrapperExecutor.forWrapperPropertiesFile(propertiesFile)
        val configuration = wrapperExecutor.configuration
        val download: IDownload = Download(logger, "gradlew", Download.UNKNOWN_VERSION, configuration.networkTimeout)

        return Action {
            wrapperExecutor.execute(
                args,
                Install(logger, download, PathAssembler(gradleUserHome, rootDir)),
                BootstrapMainStarter()
            )
        }
    }

    private fun addSystemProperties(
        projectSystemProperties: MutableMap<String?, String?>,
        userSystemProperties: MutableMap<String?, String?>,
        commandLineSystemProperties: MutableMap<String?, String?>
    ) {
        val gradleSystemProperties = merge(merge(projectSystemProperties, userSystemProperties), commandLineSystemProperties)
        System.getProperties().putAll(gradleSystemProperties)
    }

    private fun maybeAddGradleUserHomeSystemProperty(projectSystemProperties: MutableMap<String?, String?>, commandLineSystemProperties: MutableMap<String?, String?>) {
        val gradleSystemProperties = merge(projectSystemProperties, commandLineSystemProperties)
        val property = gradleSystemProperties.get(GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY)
        if (property != null) {
            System.setProperty(GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY, property)
        }
    }

    private fun merge(p1: MutableMap<String?, String?>, p2: MutableMap<String?, String?>): MutableMap<String?, String?> {
        // If there are duplicate keys, the values from p2 take precedence.
        val result: MutableMap<String?, String?> = HashMap<String?, String?>(p1)
        result.putAll(p2)
        return result
    }

    private fun rootDir(wrapperJar: File): File? {
        return wrapperJar.getParentFile().getParentFile().getParentFile()
    }

    private fun wrapperProperties(wrapperJar: File): File {
        return File(wrapperJar.getParent(), wrapperJar.getName().replaceFirst("\\.jar$".toRegex(), ".properties"))
    }

    private fun wrapperJar(): File {
        val location: URI?
        try {
            location = GradleWrapperMain::class.java.getProtectionDomain().getCodeSource().getLocation().toURI()
        } catch (e: URISyntaxException) {
            throw RuntimeException(e)
        }
        if (location.getScheme() != "file") {
            throw RuntimeException(String.format("Cannot determine classpath for wrapper Jar from codebase '%s'.", location))
        }
        try {
            return Paths.get(location).toFile()
        } catch (e: NoClassDefFoundError) {
            return File(location.getPath())
        }
    }

    private fun gradleUserHome(options: ParsedCommandLine): File {
        if (options.hasOption(GRADLE_USER_HOME_OPTION)) {
            return File(options.option(GRADLE_USER_HOME_OPTION).getValue())
        }
        return GradleUserHomeLookup.gradleUserHome()
    }

    private fun logger(options: ParsedCommandLine): Logger {
        return Logger(options.hasOption(GRADLE_QUIET_OPTION))
    }

    @NullMarked
    private fun interface Action {
        @Throws(Exception::class)
        fun execute()
    }
}
