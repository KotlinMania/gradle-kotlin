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
package org.gradle.api.plugins.scala

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.internal.JavaPluginHelper.getJavaComponent
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.ScalaSourceDirectorySet
import org.gradle.api.tasks.SourceSet.getCompileTaskName
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.language.scala.tasks.AbstractScalaCompile
import java.util.concurrent.Callable

/**
 *
 * A [Plugin] which sets up a Scala project.
 *
 * @see ScalaBasePlugin
 *
 * @see [Scala plugin reference](https://docs.gradle.org/current/userguide/scala_plugin.html)
 */
abstract class ScalaPlugin : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(ScalaBasePlugin::class.java)
        project.getPluginManager().apply(JavaPlugin::class.java)

        val mainFeature: JvmFeatureInternal = getJavaComponent(project).mainFeature

        configureScaladoc(project, mainFeature)

        val compileTaskName: String = mainFeature.sourceSet.getCompileTaskName("scala")
        val compileScala = project.getTasks().withType<AbstractScalaCompile?>(AbstractScalaCompile::class.java).named(compileTaskName)
        val compileScalaMapping: Provider<RegularFile?> = project.getLayout().getBuildDirectory().file("tmp/scala/compilerAnalysis/" + compileTaskName + ".mapping")
        compileScala.configure(Action { task: AbstractScalaCompile? -> task!!.getAnalysisMappingFile().set(compileScalaMapping) })
        project.getConfigurations().named("incrementalScalaAnalysisElements", Action { conf: Configuration? ->
            conf!!.getOutgoing().artifact(compileScalaMapping, Action { artifact: ConfigurablePublishArtifact? -> artifact!!.builtBy(compileScala) })
        })
    }

    companion object {
        const val SCALA_DOC_TASK_NAME: String = "scaladoc"

        private fun configureScaladoc(project: Project, feature: JvmFeatureInternal) {
            project.getTasks().withType<ScalaDoc?>(ScalaDoc::class.java).configureEach(Action { scalaDoc: ScalaDoc? ->
                scalaDoc!!.getConventionMapping().map("classpath", Callable {
                    val files = project.files()
                    files.from(feature.sourceSet.getOutput())
                    files.from(feature.sourceSet.getCompileClasspath())
                    files
                } as Callable<FileCollection?>)
                scalaDoc.setSource(feature.sourceSet.getExtensions().getByType(ScalaSourceDirectorySet::class.java))
                scalaDoc.getCompilationOutputs().from(feature.sourceSet.getOutput())
            })
            project.getTasks().register<ScalaDoc?>(SCALA_DOC_TASK_NAME, ScalaDoc::class.java, Action { scalaDoc: ScalaDoc? ->
                scalaDoc!!.setDescription("Generates Scaladoc for the main source code.")
                scalaDoc.setGroup(JavaBasePlugin.DOCUMENTATION_GROUP)
            })
        }
    }
}
