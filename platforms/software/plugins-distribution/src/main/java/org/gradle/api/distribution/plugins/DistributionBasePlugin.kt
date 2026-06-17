/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.distribution.plugins

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.distribution.Distribution
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.distribution.internal.DefaultDistributionContainer
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Tar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.deprecation.DeprecationLogger.whileDisabled
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.internal.TextUtil
import java.util.concurrent.Callable
import java.util.function.Consumer
import javax.inject.Inject

/**
 * A plugin that configures rules allowing projects to be packaged as a distribution.
 *
 *
 * As a base plugin, this plugin adds no distributions by default.
 * The [DistributionPlugin] adds a `main` distribution as a convention.
 *
 * @see [Distribution plugin reference](https://docs.gradle.org/current/userguide/distribution_plugin.html)
 *
 *
 * @since 8.13
 */
abstract class DistributionBasePlugin @Inject constructor(
    private val instantiator: Instantiator?,
    private val fileOperations: FileOperations?,
    private val callbackActionDecorator: CollectionCallbackActionDecorator?
) : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(BasePlugin::class.java)

        val distributions = project.getExtensions().create<DistributionContainer>(
            DistributionContainer::class.java,
            "distributions",
            DefaultDistributionContainer::class.java,
            Distribution::class.java,
            instantiator!!,
            project.getObjects(),
            fileOperations!!,
            callbackActionDecorator!!
        )
        distributions.all(Action { dist: Distribution? -> Companion.configureDistribution(project as ProjectInternal, dist!!) })

        // TODO: Maintain old behavior of checking for empty-string distribution base names.
        // It would be nice if we could do this as validation on the property itself.
        project.afterEvaluate(Action { p: Project? ->
            distributions.forEach(Consumer { distribution: Distribution? ->
                if (distribution!!.getDistributionBaseName().get() == "") {
                    throw GradleException(String.format("Distribution '%s' must not have an empty distributionBaseName.", distribution.getName()))
                }
            })
        })
    }

    companion object {
        private const val DISTRIBUTION_GROUP = "distribution"
        private const val TASK_DIST_ZIP_NAME = "distZip"
        private const val TASK_DIST_TAR_NAME = "distTar"
        private const val TASK_ASSEMBLE_NAME = "assembleDist"

        /**
         * Configures conventions and associated domain objects for a single distribution.
         */
        private fun configureDistribution(
            project: ProjectInternal,
            dist: Distribution
        ) {
            dist.getContents().from("src/" + dist.getName() + "/dist")

            val zipTaskName: String?
            val tarTaskName: String?
            val installTaskName: String?
            val assembleTaskName: String?

            if (dist.getName() == DistributionPlugin.Companion.MAIN_DISTRIBUTION_NAME) {
                zipTaskName = TASK_DIST_ZIP_NAME
                tarTaskName = TASK_DIST_TAR_NAME
                installTaskName = DistributionPlugin.Companion.TASK_INSTALL_NAME
                assembleTaskName = TASK_ASSEMBLE_NAME
                dist.getDistributionBaseName().convention(project.getName())
            } else {
                zipTaskName = dist.getName() + "DistZip"
                tarTaskName = dist.getName() + "DistTar"
                installTaskName = "install" + StringUtils.capitalize(dist.getName()) + "Dist"
                assembleTaskName = "assemble" + StringUtils.capitalize(dist.getName()) + "Dist"
                dist.getDistributionBaseName().convention(String.format("%s-%s", project.getName(), dist.getName()))
            }

            val zipTask: TaskProvider<Zip?> = Companion.addArchiveTask<Zip?>(project, zipTaskName, Zip::class.java, dist)
            val tarTask: TaskProvider<Tar?> = Companion.addArchiveTask<Tar?>(project, tarTaskName, Tar::class.java, dist)
            Companion.addInstallTask(project, installTaskName!!, dist)
            addAssembleTask(project, dist, assembleTaskName, zipTask, tarTask)

            // Build zips and tars by default when running the build-wide assemble task.
            val archivesArtifacts = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION).getArtifacts()
            whileDisabled(Runnable {
                archivesArtifacts.add(LazyPublishArtifact(zipTask, project.getFileResolver(), project.getTaskDependencyFactory()))
                archivesArtifacts.add(LazyPublishArtifact(tarTask, project.getFileResolver(), project.getTaskDependencyFactory()))
            })
        }

        /**
         * Adds a task that archives the contents of the distribution into an archive file.
         */
        private fun <T : AbstractArchiveTask?> addArchiveTask(
            project: Project,
            taskName: String,
            type: Class<T?>,
            distribution: Distribution
        ): TaskProvider<T?> {
            return project.getTasks().register<T?>(taskName, type, Action { task: T? ->
                task!!.setDescription("Bundles the project as a distribution.")
                task.setGroup(DISTRIBUTION_GROUP)
                task.getArchiveBaseName().convention(distribution.getDistributionBaseName())
                task.getArchiveClassifier().convention(distribution.getDistributionClassifier())

                val childSpec = project.copySpec()
                childSpec.with(distribution.getContents())
                childSpec.into(Callable { TextUtil.removeTrailing(task.getArchiveFileName().get(), "." + task.getArchiveExtension().get()) }
                )
                task.with(childSpec)
            })
        }

        /**
         * Adds a task that syncs the contents of the distribution into a directory within the build dir.
         */
        private fun addInstallTask(project: Project, taskName: String, distribution: Distribution) {
            project.getTasks().register<Sync?>(taskName, Sync::class.java, Action { installTask: Sync? ->
                installTask!!.setDescription("Installs the project as a distribution as-is.")
                installTask.setGroup(DISTRIBUTION_GROUP)
                installTask.with(distribution.getContents())
                val installDirectoryName = project.provider<String?>(Callable {
                    val baseName = distribution.getDistributionBaseName().get()
                    val classifier = distribution.getDistributionClassifier().getOrNull()
                    "install/" + baseName + (if (classifier != null) "-" + classifier else "")
                })
                installTask.into(project.getLayout().getBuildDirectory().dir(installDirectoryName))
            })
        }

        /**
         * Adds a task that builds all archives for distribution.
         */
        private fun addAssembleTask(
            project: ProjectInternal,
            dist: Distribution,
            distAssembleTaskName: String,
            zipTask: TaskProvider<Zip?>,
            tarTask: TaskProvider<Tar?>
        ) {
            project.getTasks().register<DefaultTask?>(distAssembleTaskName, DefaultTask::class.java, Action { assembleTask: DefaultTask? ->
                assembleTask!!.setDescription("Assembles the " + dist.getName() + " distributions")
                assembleTask.setGroup(DISTRIBUTION_GROUP)
                assembleTask.dependsOn(zipTask)
                assembleTask.dependsOn(tarTask)
            })
        }
    }
}
