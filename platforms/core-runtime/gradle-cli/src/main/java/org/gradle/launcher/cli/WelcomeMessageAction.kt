/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.launcher.cli

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Function
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration
import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.internal.IoActions
import org.gradle.launcher.bootstrap.ExecutionListener
import org.gradle.launcher.configuration.BuildLayoutResult
import org.gradle.util.GradleVersion
import org.gradle.util.internal.GFileUtils
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.StringWriter

class WelcomeMessageAction @VisibleForTesting internal constructor(
    private val logger: Logger,
    private val buildLayout: BuildLayoutResult,
    private val welcomeMessageConfiguration: WelcomeMessageConfiguration?,
    private val gradleVersion: GradleVersion,
    private val inputStreamProvider: Function<String?, InputStream?>,
    private val action: Action<ExecutionListener?>
) : Action<ExecutionListener?> {
    internal constructor(
        buildLayout: BuildLayoutResult,
        welcomeMessageConfiguration: WelcomeMessageConfiguration?,
        action: Action<ExecutionListener?>
    ) : this(Logging.getLogger(WelcomeMessageAction::class.java), buildLayout, welcomeMessageConfiguration, GradleVersion.current(), object : Function<String?, InputStream?> {
        override fun apply(input: String?): InputStream? {
            return javaClass.getClassLoader().getResourceAsStream(input)
        }
    }, action)

    override fun execute(executionListener: ExecutionListener?) {
        if (this.isEnabledBySystemProperty && this.isEnabledByGradleProperty) {
            val markerFile = this.markerFile

            if (!markerFile.exists() && logger.isLifecycleEnabled) {
                logger.lifecycle("")
                logger.lifecycle("Welcome to Gradle " + gradleVersion.getVersion() + "!")

                val featureList: String? = readReleaseFeatures()

                if (StringUtils.isNotBlank(featureList)) {
                    logger.lifecycle("")
                    logger.lifecycle("Here are the highlights of this release:")
                    logger.lifecycle(StringUtils.stripEnd(featureList, " \n\r"))
                }

                if (!gradleVersion.isSnapshot()) {
                    logger.lifecycle("")
                    logger.lifecycle("For more details see https://docs.gradle.org/" + gradleVersion.getVersion() + "/release-notes.html")
                }

                logger.lifecycle("")

                writeMarkerFile(markerFile)
            }
        }
        action.execute(executionListener)
    }

    private val isEnabledBySystemProperty: Boolean
        /**
         * The system property is set for the purpose of internal testing.
         * In user environments the system property will never be available.
         */
        get() {
            val messageEnabled = System.getProperty(WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY)

            if (messageEnabled == null) {
                return true
            }

            return messageEnabled.toBoolean()
        }

    private val isEnabledByGradleProperty: Boolean
        get() {
            if (welcomeMessageConfiguration != null) {
                return welcomeMessageConfiguration.welcomeMessageDisplayMode == WelcomeMessageDisplayMode.ONCE
            }
            return true
        }

    private val markerFile: File
        get() {
            val gradleUserHomeDir = buildLayout.getGradleUserHomeDir()
            val notificationsDir = File(gradleUserHomeDir, "notifications")
            val versionedNotificationsDir = File(notificationsDir, gradleVersion.getVersion())
            return File(versionedNotificationsDir, "release-features.rendered")
        }

    private fun readReleaseFeatures(): String? {
        val inputStream: InputStream? = inputStreamProvider.apply("release-features.txt")

        if (inputStream != null) {
            val writer = StringWriter()

            try {
                IOUtils.copy(inputStream, writer, "UTF-8")
                return writer.toString()
            } catch (e: IOException) {
                // do not fail the build as feature is non-critical
            } finally {
                IoActions.closeQuietly(inputStream)
            }
        }

        return null
    }

    private fun writeMarkerFile(markerFile: File) {
        GFileUtils.mkdirs(markerFile.getParentFile())
        GFileUtils.touch(markerFile)
    }

    companion object {
        const val WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY: String = "org.gradle.internal.launcher.welcomeMessageEnabled"
    }
}
