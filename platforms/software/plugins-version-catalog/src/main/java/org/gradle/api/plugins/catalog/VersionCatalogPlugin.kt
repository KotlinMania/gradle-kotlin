/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.catalog

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.catalog.internal.CatalogExtensionInternal
import org.gradle.api.plugins.catalog.internal.DefaultVersionCatalogPluginExtension
import org.gradle.api.plugins.catalog.internal.TomlFileGenerator
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import javax.inject.Inject

/**
 *
 * A [Plugin] makes it possible to generate a version catalog,  which is a set of versions and
 * coordinates for dependencies and plugins to import in the settings of a Gradle build.
 *
 * @since 7.0
 */
abstract class VersionCatalogPlugin @Inject constructor(private val softwareComponentFactory: SoftwareComponentFactory) : Plugin<Project?> {
    override fun apply(project: Project?) {
        val projectInternal = project as ProjectInternal
        val extension = createExtension(projectInternal)
        val generator: TaskProvider<TomlFileGenerator?> = createGenerator(project, extension)
        createPublication(project, generator)
    }

    private fun createPublication(project: ProjectInternal, generator: TaskProvider<TomlFileGenerator?>) {
        val exported: Provider<ConsumableConfiguration?> = project.getConfigurations().consumable(VERSION_CATALOG_ELEMENTS, Action { cnf: ConsumableConfiguration? ->
            cnf!!.setDescription("Artifacts for the version catalog")
            cnf.getOutgoing().artifact(generator)
            cnf.attributes(Action { attrs: AttributeContainer? ->
                attrs!!.attribute<Category?>(Category.CATEGORY_ATTRIBUTE, attrs.named<Category?>(Category::class.java, Category.REGULAR_PLATFORM))
                attrs.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attrs.named<Usage?>(Usage::class.java, Usage.VERSION_CATALOG))
            })
        })

        project.getPlugins().withType<BasePlugin?>(BasePlugin::class.java, Action { plugin: BasePlugin? ->
            project.getTasks().named(BasePlugin.ASSEMBLE_TASK_NAME).configure(Action { assemble: Task? ->
                assemble!!.dependsOn(exported.get()!!.getArtifacts())
            })
        })

        val versionCatalog = softwareComponentFactory.adhoc("versionCatalog")
        project.getComponents().add(versionCatalog)
        versionCatalog.addVariantsFromConfiguration(exported, JavaConfigurationVariantMapping("compile", true))
    }

    private fun createGenerator(project: Project, extension: CatalogExtensionInternal): TaskProvider<TomlFileGenerator?> {
        return project.getTasks()
            .register<TomlFileGenerator?>(GENERATE_CATALOG_FILE_TASKNAME, TomlFileGenerator::class.java, Action { t: TomlFileGenerator? -> configureTask(project, extension, t!!) })
    }

    private fun configureTask(project: Project, extension: CatalogExtensionInternal, task: TomlFileGenerator) {
        task.setGroup(BasePlugin.BUILD_GROUP)
        task.setDescription("Generates a TOML file for a version catalog")
        task.getOutputFile().convention(project.getLayout().getBuildDirectory().file("version-catalog/libs.versions.toml"))
        task.getDependenciesModel().convention(extension.getVersionCatalog())
    }

    private fun createExtension(project: ProjectInternal): CatalogExtensionInternal {
        val dependenciesConfiguration: Configuration = project.getConfigurations().dependencyScopeLocked(GRADLE_PLATFORM_DEPENDENCIES)
        return project.getExtensions()
            .create<CatalogPluginExtension?>(CatalogPluginExtension::class.java, "catalog", DefaultVersionCatalogPluginExtension::class.java, dependenciesConfiguration) as CatalogExtensionInternal
    }

    companion object {
        const val GENERATE_CATALOG_FILE_TASKNAME: String = "generateCatalogAsToml"
        const val GRADLE_PLATFORM_DEPENDENCIES: String = "versionCatalog"
        const val VERSION_CATALOG_ELEMENTS: String = "versionCatalogElements"
    }
}
