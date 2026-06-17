/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.java.WebApplication
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.internal.JavaPluginHelper
import org.gradle.api.plugins.internal.JavaPluginHelper.getJavaComponent
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal
import org.gradle.api.tasks.bundling.War
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 *
 * A [Plugin] which extends the [JavaPlugin] to add tasks which assemble a web application into a WAR
 * file.
 *
 * @see [WAR plugin reference](https://docs.gradle.org/current/userguide/war_plugin.html)
 */
abstract class WarPlugin @Inject constructor(private val objectFactory: ObjectFactory, private val attributesFactory: AttributesFactory) : Plugin<Project?> {
    private var project: Project? = null
    private var mainFeature: JvmFeatureInternal? = null

    override fun apply(project: Project) {
        project.getPluginManager().apply(JavaPlugin::class.java)
        this.project = project
        this.mainFeature = getJavaComponent(project).mainFeature

        project.getTasks().withType<War?>(War::class.java).configureEach(Action { task: War? ->
            task!!.getWebAppDirectory().convention(project.getLayout().getProjectDirectory().dir("src/main/webapp"))
            task.from(task.getWebAppDirectory())
            task.dependsOn(Callable { mainFeature!!.sourceSet.getRuntimeClasspath() } as Callable<FileCollection?>)
            task.classpath(Callable {
                val providedRuntime = project.getConfigurations().getByName(PROVIDED_RUNTIME_CONFIGURATION_NAME)
                mainFeature!!.sourceSet.getRuntimeClasspath().minus(providedRuntime)
            } as Callable<FileCollection?>)
        })

        val war = project.getTasks().register<War?>(WAR_TASK_NAME, War::class.java, Action { warTask: War? ->
            warTask!!.setDescription("Generates a war archive with all the compiled classes, the web-app content and the libraries.")
            warTask.setGroup(BasePlugin.BUILD_GROUP)
        })

        val warArtifact: PublishArtifact = LazyPublishArtifact(war, (project as ProjectInternal).getFileResolver(), project.getTaskDependencyFactory())
        whileDisabled(Runnable {
            project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION).getArtifacts().add(warArtifact)
        })
        configureConfigurations(project.getConfigurations(), mainFeature!!)
        configureComponent(project, warArtifact)
    }

    @Suppress("deprecation")
    private fun configureConfigurations(configurationContainer: RoleBasedConfigurationContainerInternal, mainFeature: JvmFeatureInternal) {
        val providedCompileConfiguration = configurationContainer.resolvableDependencyScopeLocked(PROVIDED_COMPILE_CONFIGURATION_NAME, Action { conf: Configuration? ->
            conf!!.setDescription("Additional compile classpath for libraries that should not be part of the WAR archive.")
        })

        val providedRuntimeConfiguration = configurationContainer.resolvableDependencyScopeLocked(PROVIDED_RUNTIME_CONFIGURATION_NAME, Action { conf: Configuration? ->
            conf!!.extendsFrom(providedCompileConfiguration)
            conf.setDescription("Additional runtime classpath for libraries that should not be part of the WAR archive.")
        })

        mainFeature.implementationConfiguration.extendsFrom(providedCompileConfiguration)
        mainFeature.runtimeClasspathConfiguration.extendsFrom(providedRuntimeConfiguration)
        mainFeature.runtimeElementsConfiguration!!.configure({ conf -> conf!!.extendsFrom(providedRuntimeConfiguration) }
        )

        val defaultTestSuite = JavaPluginHelper.getDefaultTestSuite(project!!)
        configurationContainer.getByName(defaultTestSuite.sources!!.runtimeClasspathConfigurationName!!).extendsFrom(providedRuntimeConfiguration)
    }

    private fun configureComponent(project: Project, warArtifact: PublishArtifact) {
        val attributes: AttributeContainer = attributesFactory.mutable()
        attributes.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attributes.named<Usage?>(Usage::class.java, Usage.JAVA_RUNTIME))
        project.getComponents().add(objectFactory.newInstance<WebApplication?>(WebApplication::class.java, warArtifact, "master", attributes)!!)
    }

    companion object {
        const val PROVIDED_COMPILE_CONFIGURATION_NAME: String = "providedCompile"
        const val PROVIDED_RUNTIME_CONFIGURATION_NAME: String = "providedRuntime"
        const val WAR_TASK_NAME: String = "war"
    }
}
