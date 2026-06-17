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
package org.gradle.plugins.ide.internal.tooling

import org.gradle.api.Project
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.internal.jvm.Jvm
import org.gradle.launcher.cli.internal.VersionInfoRenderer.render
import org.gradle.process.internal.CurrentProcess
import org.gradle.tooling.internal.build.DefaultBuildEnvironment
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier
import org.gradle.tooling.provider.model.ToolingModelBuilder

/**
 * Builds the GradleProject that contains the project hierarchy and task information
 */
class BuildEnvironmentBuilder(private val fileCollectionFactory: FileCollectionFactory?) : ToolingModelBuilder {
    override fun canBuild(modelName: String): Boolean {
        return modelName == "org.gradle.tooling.model.build.BuildEnvironment"
    }

    override fun buildAll(modelName: String, target: Project): Any? {
        val gradleUserHomeDir = target.getGradle().getGradleUserHomeDir()
        val gradleVersion = target.getGradle().getGradleVersion()

        val currentProcess = CurrentProcess(fileCollectionFactory)
        val javaHome = currentProcess.jvm!!.getJavaHome()
        val jvmArgs = currentProcess.jvmOptions!!.allImmutableJvmArgs

        val buildIdentifier = DefaultBuildIdentifier(target.getRootDir())
        val versionInfo = render(Jvm.current().toString())
        return DefaultBuildEnvironment(buildIdentifier, gradleUserHomeDir, gradleVersion, javaHome, jvmArgs, versionInfo)
    }
}
