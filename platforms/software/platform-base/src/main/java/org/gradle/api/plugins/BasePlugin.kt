/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.internal.DomainObjectCollectionInternal
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.internal.plugins.BuildConfigurationRule
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.deprecation.DeprecationLogger.deprecateConfiguration
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.util.concurrent.Callable

/**
 *
 * A [Plugin] which defines a basic project lifecycle and some common convention properties.
 *
 * @see [Base plugin reference](https://docs.gradle.org/current/userguide/base_plugin.html)
 */
abstract class BasePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(LifecycleBasePlugin::class.java)
        val baseExtension = project.getExtensions().create<BasePluginExtension>("base", BasePluginExtension::class.java)
        configureExtension(project, baseExtension)
        configureBuildConfigurationRule(project)
        configureArchiveDefaults(project, baseExtension)
        configureConfigurations(project)
    }

    private fun configureExtension(project: Project, extension: BasePluginExtension) {
        extension.archivesName.convention(project.getName())
        extension.libsDirectory.convention(project.getLayout().getBuildDirectory().dir("libs"))
        extension.distsDirectory.convention(project.getLayout().getBuildDirectory().dir("distributions"))
    }

    private fun configureArchiveDefaults(project: Project, extension: BasePluginExtension) {
        project.getTasks().withType(AbstractArchiveTask::class.java).configureEach(Action { task: AbstractArchiveTask ->
            task.getDestinationDirectory().convention(extension.distsDirectory)
            task.getArchiveVersion().convention(
                project.provider<String>(Callable { if (project.getVersion() === Project.DEFAULT_VERSION) null else project.getVersion().toString() })
            )
            task.getArchiveBaseName().convention(extension.archivesName)
        })
    }

    private fun configureBuildConfigurationRule(project: Project) {
        project.getTasks().addRule(BuildConfigurationRule(project.getConfigurations(), project.getTasks()))
    }

    private fun configureConfigurations(project: Project) {
        val configurations = project.getConfigurations() as RoleBasedConfigurationContainerInternal
        (project as ProjectInternal).getInternalStatus().convention("integration")

        val archivesConfiguration = configurations.migratingLocked(Dependency.ARCHIVES_CONFIGURATION, ConfigurationRolesForMigration.CONSUMABLE_TO_RETIRED, Action { conf: Configuration ->
            conf.setDescription("Configuration for archive artifacts.")
            val artifacts: DomainObjectCollectionInternal<PublishArtifact> = uncheckedCast<DomainObjectCollectionInternal<PublishArtifact>>(conf.getArtifacts())!!
            artifacts.beforeCollectionChanges(Action { artifact: String? ->
                deprecateConfiguration(Dependency.ARCHIVES_CONFIGURATION)
                    .forArtifactDeclaration()
                    .withAdvice("Add artifacts as direct task dependencies of the 'assemble' task instead of declaring them in the " + Dependency.ARCHIVES_CONFIGURATION + " configuration.")!!
                    .willBecomeAnErrorInNextMajorGradleVersion()
                    .withUpgradeGuideSection(9, "sec:archives-configuration")!!
                    .nagUser()
            })
        })

        configurations.consumable(Dependency.DEFAULT_CONFIGURATION, Action { conf: ConsumableConfiguration ->
            conf.setDescription("Configuration for default artifacts.")
        })

        project.getTasks().named(ASSEMBLE_TASK_NAME, Action { task: Task -> task.dependsOn(archivesConfiguration.getAllArtifacts().getBuildDependencies()) }
        )
    }

    companion object {
        val CLEAN_TASK_NAME: String = LifecycleBasePlugin.CLEAN_TASK_NAME
        val ASSEMBLE_TASK_NAME: String = LifecycleBasePlugin.ASSEMBLE_TASK_NAME
        val BUILD_GROUP: String = LifecycleBasePlugin.BUILD_GROUP
    }
}
