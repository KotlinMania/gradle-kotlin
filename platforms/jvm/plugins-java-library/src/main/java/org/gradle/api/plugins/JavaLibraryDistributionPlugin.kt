/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.plugins

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.Distribution
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.distribution.plugins.DistributionPlugin
import org.gradle.api.plugins.internal.JavaPluginHelper.getJavaComponent
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal

/**
 * A [Plugin] which package a Java project as a distribution including the JAR and runtime dependencies.
 *
 * @see [Java Library Distribution plugin reference](https://docs.gradle.org/current/userguide/java_library_distribution_plugin.html)
 */
abstract class JavaLibraryDistributionPlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(JavaLibraryPlugin::class.java)
        project.getPluginManager().apply(DistributionPlugin::class.java)

        val distributionContainer = project.getExtensions().getByName("distributions") as DistributionContainer
        distributionContainer.named(DistributionPlugin.MAIN_DISTRIBUTION_NAME).configure(Action { dist: Distribution? ->
            val mainFeature: JvmFeatureInternal = getJavaComponent(project).mainFeature
            val childSpec = project.copySpec()
            childSpec.from(mainFeature.jarTask)
            childSpec.from(project.file("src/dist"))

            val libSpec = project.copySpec()
            libSpec.into("lib")
            libSpec.from(mainFeature.runtimeClasspathConfiguration)

            childSpec.with(libSpec)
            dist!!.getContents().with(childSpec)
        })
    }
}
