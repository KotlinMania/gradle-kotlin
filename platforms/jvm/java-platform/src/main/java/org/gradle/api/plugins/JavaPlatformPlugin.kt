/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.capabilities.Capability
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.api.internal.java.DefaultJavaPlatformExtension
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.internal.component.external.model.ProjectDerivedCapability
import org.gradle.internal.component.external.model.ShadowedImmutableCapability
import java.util.function.Consumer
import javax.inject.Inject

/**
 * The Java platform plugin allows building platform components
 * for Java, which are usually published as BOM files (for Maven)
 * or Gradle platforms (Gradle metadata).
 *
 * @since 5.2
 * @see [Java Platform plugin reference](https://docs.gradle.org/current/userguide/java_platform_plugin.html)
 */
abstract class JavaPlatformPlugin @Inject constructor(private val softwareComponentFactory: SoftwareComponentFactory) : Plugin<Project?> {
    override fun apply(project: Project) {
        check(!project.getPluginManager().hasPlugin("java")) {
            "The \"java-platform\" plugin cannot be applied together with the \"java\" (or \"java-library\") plugin. " +
                    "A project is either a platform or a library but cannot be both at the same time."
        }
        project.getPluginManager().apply(BasePlugin::class.java)
        project.getPluginManager().apply(JvmEcosystemPlugin::class.java)
        createConfigurations(project as ProjectInternal)
        configureExtension(project)
        configurePublishing(project)
    }

    private fun createSoftwareComponent(project: Project, apiElements: Provider<ConsumableConfiguration?>, runtimeElements: Provider<ConsumableConfiguration?>) {
        val component = softwareComponentFactory.adhoc("javaPlatform")
        project.getComponents().add(component)
        component.addVariantsFromConfiguration(apiElements, JavaConfigurationVariantMapping("compile", false))
        component.addVariantsFromConfiguration(runtimeElements, JavaConfigurationVariantMapping("runtime", false))
    }

    private fun createConfigurations(project: ProjectInternal) {
        val configurations = project.getConfigurations()
        val enforcedCapability: Capability = ShadowedImmutableCapability(ProjectDerivedCapability(project), "-derived-enforced-platform")

        // API
        val api: Configuration = configurations.dependencyScopeLocked(API_CONFIGURATION_NAME)

        val apiElements: Provider<ConsumableConfiguration?> = createConsumableApi(project, api, API_ELEMENTS_CONFIGURATION_NAME, Category.REGULAR_PLATFORM, mutableSetOf<Capability?>())
        createConsumableApi(project, api, ENFORCED_API_ELEMENTS_CONFIGURATION_NAME, Category.ENFORCED_PLATFORM, mutableSetOf<Capability?>(enforcedCapability))

        // Runtime
        val runtime: Configuration = project.getConfigurations().dependencyScopeLocked(RUNTIME_CONFIGURATION_NAME, Action { conf: Configuration? ->
            conf!!.extendsFrom(api)
        })

        val runtimeElements: Provider<ConsumableConfiguration?> = createConsumableRuntime(project, runtime, RUNTIME_ELEMENTS_CONFIGURATION_NAME, Category.REGULAR_PLATFORM, mutableSetOf<Capability?>())
        createConsumableRuntime(project, runtime, ENFORCED_RUNTIME_ELEMENTS_CONFIGURATION_NAME, Category.ENFORCED_PLATFORM, mutableSetOf<Capability?>(enforcedCapability))

        // Resolvable configuration used for publishing resolved versions.
        configurations.resolvableLocked(CLASSPATH_CONFIGURATION_NAME, Action { conf: Configuration? ->
            conf!!.extendsFrom(runtime)
            declareConfigurationUsage(conf, Usage.JAVA_RUNTIME)
            declareConfigurationLibraryElements(conf, LibraryElements.JAR)
        })

        createSoftwareComponent(project, apiElements, runtimeElements)
    }

    private fun createConsumableRuntime(
        project: ProjectInternal,
        runtime: Configuration,
        name: String,
        platformKind: String,
        capabilities: MutableSet<Capability?>
    ): Provider<ConsumableConfiguration?> {
        return project.getConfigurations().consumable(name, Action { runtimeElements: ConsumableConfiguration? ->
            runtimeElements!!.extendsFrom(runtime)
            declareConfigurationUsage(runtimeElements, Usage.JAVA_RUNTIME)
            declareConfigurationCategory(runtimeElements, platformKind)

            val outgoing = runtimeElements.getOutgoing()
            capabilities.forEach(Consumer { notation: Capability? -> outgoing.capability(notation!!) })
        })
    }

    private fun createConsumableApi(project: ProjectInternal, api: Configuration, name: String, platformKind: String, capabilities: MutableSet<Capability?>): Provider<ConsumableConfiguration?> {
        return project.getConfigurations().consumable(name, Action { apiElements: ConsumableConfiguration? ->
            apiElements!!.extendsFrom(api)
            declareConfigurationUsage(apiElements, Usage.JAVA_API)
            declareConfigurationCategory(apiElements, platformKind)

            val outgoing = apiElements.getOutgoing()
            capabilities.forEach(Consumer { notation: Capability? -> outgoing.capability(notation!!) })
        })
    }

    private fun declareConfigurationCategory(configuration: Configuration, value: String) {
        val attributes = configuration.getAttributes()
        attributes.attribute<Category?>(Category.CATEGORY_ATTRIBUTE, attributes.named<Category?>(Category::class.java, value))
    }

    private fun declareConfigurationLibraryElements(configuration: Configuration, libraryContents: String) {
        val attributes = configuration.getAttributes()
        attributes.attribute<LibraryElements?>(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, attributes.named<LibraryElements?>(LibraryElements::class.java, libraryContents))
    }

    private fun declareConfigurationUsage(configuration: Configuration, usage: String) {
        val attributes = configuration.getAttributes()
        attributes.attribute<Usage?>(Usage.USAGE_ATTRIBUTE, attributes.named<Usage?>(Usage::class.java, usage))
    }

    private fun configureExtension(project: Project) {
        val platformExtension =
            project.getExtensions().create<JavaPlatformExtension?>(JavaPlatformExtension::class.java, "javaPlatform", DefaultJavaPlatformExtension::class.java) as DefaultJavaPlatformExtension
        project.afterEvaluate(Action { project1: Project? ->
            if (!platformExtension.isAllowDependencies()) {
                checkNoDependencies(project1!!)
            }
        })
    }

    private fun checkNoDependencies(project: Project) {
        checkNoDependencies(project.getConfigurations().getByName(RUNTIME_CONFIGURATION_NAME), HashSet<Configuration?>())
    }

    private fun checkNoDependencies(configuration: Configuration, visited: MutableSet<Configuration?>) {
        if (visited.add(configuration)) {
            if (!configuration.getDependencies().isEmpty()) {
                throw InvalidUserCodeException(
                    String.format(
                        "Adding dependencies to platforms is not allowed by default.\n" +
                                "Most likely you want to add constraints instead.\n" +
                                "If you did this intentionally, you need to configure the platform extension to allow dependencies:\n" +
                                "    javaPlatform.allowDependencies()\n" +
                                "Found dependencies in the '%s' configuration.", configuration.getName()
                    )
                )
            }
            val extendsFrom = configuration.getExtendsFrom()
            for (parent in extendsFrom) {
                checkNoDependencies(parent, visited)
            }
        }
    }

    private fun configurePublishing(project: Project) {
        project.getPlugins().withType<PublishingPlugin?>(PublishingPlugin::class.java, Action { plugin: PublishingPlugin? ->
            val publishing = project.getExtensions().getByType<PublishingExtension>(PublishingExtension::class.java)
            // Set up the default configurations used when mapping to resolved versions
            publishing.getPublications().withType<IvyPublication?>(IvyPublication::class.java, Action { publication: IvyPublication? ->
                val strategy = (publication as PublicationInternal<*>).getVersionMappingStrategy()
                strategy.defaultResolutionConfiguration(Usage.JAVA_API, CLASSPATH_CONFIGURATION_NAME)
                strategy.defaultResolutionConfiguration(Usage.JAVA_RUNTIME, CLASSPATH_CONFIGURATION_NAME)
            })
            publishing.getPublications().withType<MavenPublication?>(MavenPublication::class.java, Action { publication: MavenPublication? ->
                val strategy = (publication as PublicationInternal<*>).getVersionMappingStrategy()
                strategy.defaultResolutionConfiguration(Usage.JAVA_API, CLASSPATH_CONFIGURATION_NAME)
                strategy.defaultResolutionConfiguration(Usage.JAVA_RUNTIME, CLASSPATH_CONFIGURATION_NAME)
            })
        })
    }

    companion object {
        // Dependency scopes
        const val API_CONFIGURATION_NAME: String = "api"
        const val RUNTIME_CONFIGURATION_NAME: String = "runtime"

        // Consumable configurations
        const val API_ELEMENTS_CONFIGURATION_NAME: String = "apiElements"
        const val RUNTIME_ELEMENTS_CONFIGURATION_NAME: String = "runtimeElements"
        const val ENFORCED_API_ELEMENTS_CONFIGURATION_NAME: String = "enforcedApiElements"
        const val ENFORCED_RUNTIME_ELEMENTS_CONFIGURATION_NAME: String = "enforcedRuntimeElements"

        // Resolvable configurations
        const val CLASSPATH_CONFIGURATION_NAME: String = "classpath"
    }
}
