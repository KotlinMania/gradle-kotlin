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
package org.gradle.testkit.runner.internal

import org.gradle.testkit.runner.GradleRunner
import org.gradle.tooling.GradleConnector
import java.io.File
import java.net.URI

abstract class GradleProvider private constructor() {
    abstract fun applyTo(gradleConnector: GradleConnector?)

    abstract fun applyTo(gradleRunner: GradleRunner?)

    private class InstallationGradleProvider(private val gradleHome: File?) : GradleProvider() {
        override fun applyTo(gradleConnector: GradleConnector) {
            gradleConnector.useInstallation(gradleHome)
        }

        override fun applyTo(gradleRunner: GradleRunner) {
            gradleRunner.withGradleInstallation(gradleHome)
        }
    }

    private class UriGradleProvider(private val uri: URI?) : GradleProvider() {
        override fun applyTo(gradleConnector: GradleConnector) {
            gradleConnector.useDistribution(uri)
        }

        override fun applyTo(gradleRunner: GradleRunner) {
            gradleRunner.withGradleDistribution(uri)
        }
    }

    private class VersionGradleProvider(private val gradleVersion: String?) : GradleProvider() {
        override fun applyTo(gradleConnector: GradleConnector) {
            gradleConnector.useGradleVersion(gradleVersion)
        }

        override fun applyTo(gradleRunner: GradleRunner) {
            gradleRunner.withGradleVersion(gradleVersion)
        }
    }

    companion object {
        fun installation(gradleHome: File?): GradleProvider {
            return InstallationGradleProvider(gradleHome)
        }

        fun uri(location: URI?): GradleProvider {
            return UriGradleProvider(location)
        }

        fun version(version: String?): GradleProvider {
            return VersionGradleProvider(version)
        }
    }
}
