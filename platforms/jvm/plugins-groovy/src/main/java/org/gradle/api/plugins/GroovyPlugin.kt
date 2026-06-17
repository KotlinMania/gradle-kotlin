/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.internal.JavaPluginHelper
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal
import org.gradle.api.tasks.GroovySourceDirectorySet
import org.gradle.api.tasks.javadoc.Groovydoc

/**
 *
 * A [Plugin] which extends the [JavaPlugin] to provide support for compiling and documenting Groovy
 * source files.
 *
 * @see [Groovy plugin reference](https://docs.gradle.org/current/userguide/groovy_plugin.html)
 */
abstract class GroovyPlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(GroovyBasePlugin::class.java)
        project.getPluginManager().apply(JavaPlugin::class.java)

        configureGroovydoc(project)
    }

    private fun configureGroovydoc(project: Project) {
        project.getTasks().register<Groovydoc?>(GROOVYDOC_TASK_NAME, Groovydoc::class.java, Action { groovyDoc: Groovydoc? ->
            groovyDoc!!.setDescription("Generates Groovydoc API documentation for the main source code.")
            groovyDoc.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP)

            val mainFeature: JvmFeatureInternal = JavaPluginHelper.getJavaComponent(project).mainFeature
            groovyDoc.classpath = mainFeature.sourceSet.getOutput().plus(mainFeature.sourceSet.getCompileClasspath())

            val groovySourceSet: SourceDirectorySet = mainFeature.sourceSet.getExtensions().getByType(GroovySourceDirectorySet::class.java)
            groovyDoc.setSource(groovySourceSet)
        })
    }

    companion object {
        const val GROOVYDOC_TASK_NAME: String = "groovydoc"
    }
}
