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
package org.gradle.plugins.ear

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.plugins.internal.JavaPluginHelper
import org.gradle.api.plugins.jvm.internal.JvmPluginServices
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.plugins.ear.descriptor.DeploymentDescriptor
import org.gradle.plugins.ear.descriptor.internal.DefaultDeploymentDescriptor
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 *
 *
 * A [Plugin] with tasks which assemble a web application into a EAR file.
 *
 *
 * @see [EAR plugin reference](https://docs.gradle.org/current/userguide/ear_plugin.html)
 */
@Suppress("deprecation")
abstract class EarPlugin
/**
 * Injects an [ObjectFactory]
 *
 * @since 4.2
 */ @Inject constructor(private val objectFactory: ObjectFactory, private val jvmPluginServices: JvmPluginServices, private val taskDependencyFactory: TaskDependencyFactory) : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(JavaBasePlugin::class.java)
        configureConfigurations(project as ProjectInternal)
        val plugins = project.getPlugins()
        setupEarTask(project, plugins)
        wireEarTaskConventions(project)
        wireEarTaskConventionsWithJavaPluginApplied(project, plugins)
    }

    private fun wireEarTaskConventionsWithJavaPluginApplied(project: Project, plugins: PluginContainer) {
        plugins.withType<JavaPlugin?>(JavaPlugin::class.java, Action { javaPlugin: JavaPlugin? ->
            val mainFeature = JavaPluginHelper.getJavaComponent(project).mainFeature
            project.getTasks().withType<Ear?>(Ear::class.java).configureEach(Action { task: Ear? ->
                task!!.dependsOn(Callable { mainFeature.sourceSet.getRuntimeClasspath() }
                )
                task.from(Callable { mainFeature.sourceSet.getOutput() } as Callable<FileCollection?>
                )
            })
        })
    }

    private fun setupEarTask(project: Project, plugins: PluginContainer) {
        val ear = project.getTasks().register<Ear?>(EAR_TASK_NAME, Ear::class.java, Action { task: Ear? ->
            task!!.setDescription("Generates a ear archive with all the modules, the application descriptor and the libraries.")
            task.setGroup(BasePlugin.BUILD_GROUP)
            task.getGenerateDeploymentDescriptor().convention(true)

            plugins.withType<JavaPlugin?>(JavaPlugin::class.java, Action { javaPlugin: JavaPlugin? ->
                val component = JavaPluginHelper.getJavaComponent(project)
                component.mainFeature.sourceSet.getResources().srcDir(task.getAppDirectory())
            })

            val deploymentDescriptor: DeploymentDescriptor = objectFactory.newInstance<DefaultDeploymentDescriptor>(DefaultDeploymentDescriptor::class.java)
            deploymentDescriptor.readFrom("META-INF/application.xml")
            deploymentDescriptor.readFrom("src/main/application/META-INF/" + deploymentDescriptor.getFileName())
            if (deploymentDescriptor != null) {
                if (deploymentDescriptor.getDisplayName() == null) {
                    deploymentDescriptor.setDisplayName(project.getName())
                }
                if (deploymentDescriptor.getDescription() == null) {
                    deploymentDescriptor.setDescription(project.getDescription())
                }
            }
            task.setDeploymentDescriptor(deploymentDescriptor)
        })

        whileDisabled(Runnable {
            project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION)
                .getArtifacts()
                .add(LazyPublishArtifact(ear, (project as ProjectInternal).getFileResolver(), taskDependencyFactory))
        })
    }

    private fun wireEarTaskConventions(project: Project) {
        project.getTasks().withType<Ear?>(Ear::class.java).configureEach(Action { task: Ear? ->
            task!!.getAppDirectory().convention(project.provider<Directory?>(Callable { project.getLayout().getProjectDirectory().dir("src/main/application") }))
            task.getConventionMapping().map("libDirName", object : Callable<String?> {
                @Throws(Exception::class)
                override fun call(): String {
                    return@configureEach DEFAULT_LIB_DIR_NAME
                }
            })
            task.getConventionMapping().map("deploymentDescriptor", object : Callable<DeploymentDescriptor?> {
                @Throws(Exception::class)
                override fun call(): DeploymentDescriptor {
                    val deploymentDescriptor: DeploymentDescriptor = objectFactory.newInstance<DefaultDeploymentDescriptor>(DefaultDeploymentDescriptor::class.java)
                    deploymentDescriptor.readFrom("META-INF/application.xml")
                    deploymentDescriptor.readFrom("src/main/application/META-INF/" + deploymentDescriptor.getFileName())
                    return@configureEach deploymentDescriptor
                }
            })

            task.from(Callable {
                if (project.getPlugins().hasPlugin(JavaPlugin::class.java)) {
                    return@Callable null
                } else {
                    return@Callable task.getAppDirectory().getAsFileTree()
                }
            } as Callable<FileCollection?>)

            task.getLib().from(Callable {
                project.getConfigurations().getByName(EARLIB_CONFIGURATION_NAME)
                    .minus(project.getConfigurations().getByName(DEPLOY_CONFIGURATION_NAME))
            })
            task.from(Callable { project.getConfigurations().getByName(DEPLOY_CONFIGURATION_NAME) } as Callable<FileCollection?>)
        })
    }

    private fun configureConfigurations(project: ProjectInternal) {
        val configurations = project.getConfigurations()

        val deployConfiguration = configurations.resolvableDependencyScopeLocked(DEPLOY_CONFIGURATION_NAME, Action { conf: Configuration? ->
            conf!!.setTransitive(false)
            conf.setDescription("Classpath for deployable modules, not transitive.")
            jvmPluginServices.configureAsRuntimeClasspath(conf)
        })

        val earlibConfiguration = configurations.resolvableDependencyScopeLocked(EARLIB_CONFIGURATION_NAME, Action { conf: Configuration? ->
            conf!!.setDescription("Classpath for module dependencies.")
            jvmPluginServices.configureAsRuntimeClasspath(conf)
        })

        configurations.named(Dependency.DEFAULT_CONFIGURATION).configure(Action { conf: Configuration? ->
            conf!!.extendsFrom(deployConfiguration, earlibConfiguration)
        })
    }

    companion object {
        const val EAR_TASK_NAME: String = "ear"

        const val DEPLOY_CONFIGURATION_NAME: String = "deploy"
        const val EARLIB_CONFIGURATION_NAME: String = "earlib"

        const val DEFAULT_LIB_DIR_NAME: String = "lib"
    }
}
