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
package org.gradle.api.plugins.jvm.internal

import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationVariant
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.HasConfigurableAttributes
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.internal.artifacts.ConfigurationVariantInternal
import org.gradle.api.internal.artifacts.publish.AbstractPublishArtifact
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.tasks.DefaultSourceSetOutput
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.instantiation.InstanceGenerator
import java.io.File
import java.util.Date
import java.util.concurrent.Callable
import java.util.stream.Collectors
import javax.inject.Inject

class DefaultJvmPluginServices @Inject constructor(
    private val providerFactory: ProviderFactory,
    private val instanceGenerator: InstanceGenerator,
    private val taskDependencyFactory: TaskDependencyFactory
) : JvmPluginServices {
    override fun configureAsCompileClasspath(configuration: HasConfigurableAttributes<*>) {
        configureAttributes(
            configuration,
            Action { details: JvmEcosystemAttributesDetails? -> details!!.library().apiUsage().withExternalDependencies().preferStandardJVM() }
        )
    }

    override fun configureAsRuntimeClasspath(configuration: HasConfigurableAttributes<*>) {
        configureAttributes(
            configuration,
            Action { details: JvmEcosystemAttributesDetails? -> details!!.library().runtimeUsage().asJar().withExternalDependencies().preferStandardJVM() }
        )
    }

    override fun configureAsSources(configuration: HasConfigurableAttributes<*>) {
        configureAttributes(
            configuration,
            Action { details: JvmEcosystemAttributesDetails? -> details!!.withExternalDependencies().asSources() }
        )
    }

    override fun configureAsApiElements(configuration: HasConfigurableAttributes<*>) {
        configureAttributes(
            configuration,
            Action { details: JvmEcosystemAttributesDetails? -> details!!.library().apiUsage().asJar().withExternalDependencies() }
        )
    }

    override fun configureAsRuntimeElements(configuration: HasConfigurableAttributes<*>) {
        configureAttributes(
            configuration,
            Action { details: JvmEcosystemAttributesDetails? -> details!!.library().runtimeUsage().asJar().withExternalDependencies() }
        )
    }

    override fun <T> configureAttributes(configurable: HasConfigurableAttributes<T?>, configuration: Action<in JvmEcosystemAttributesDetails?>) {
        val attributes = configurable.getAttributes() as AttributeContainerInternal
        val details = instanceGenerator.newInstance<DefaultJvmEcosystemAttributesDetails>(DefaultJvmEcosystemAttributesDetails::class.java, attributes)
        configuration.execute(details)
    }

    override fun replaceArtifacts(outgoingConfiguration: Configuration, vararg providers: Any) {
        clearArtifacts(outgoingConfiguration)
        val outgoing = outgoingConfiguration.getOutgoing()
        for (provider in providers) {
            outgoing.artifact(provider)
        }
    }

    private fun clearArtifacts(outgoingConfiguration: Configuration) {
        outgoingConfiguration.getOutgoing().getArtifacts().clear()
        for (configuration in outgoingConfiguration.getExtendsFrom()) {
            clearArtifacts(configuration)
        }
    }

    override fun configureResourcesDirectoryVariant(configuration: Configuration, sourceSet: SourceSet): ConfigurationVariant {
        val publications = configuration.getOutgoing()
        val variant = publications.getVariants().maybeCreate("resources") as ConfigurationVariantInternal
        variant.getDescription().convention("Directories containing assembled resource files for " + sourceSet.getName() + ".")
        val attributes: AttributeContainer = variant.getAttributes()
        attributes.attribute<LibraryElements?>(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, attributes.named<LibraryElements?>(LibraryElements::class.java, LibraryElements.RESOURCES))
        val output = uncheckedCast<DefaultSourceSetOutput?>(sourceSet.getOutput())
        val resourcesContribution = output!!.getResourcesContribution()
        if (resourcesContribution != null) {
            variant.artifact(LazyJavaDirectoryArtifact(taskDependencyFactory, ArtifactTypeDefinition.JVM_RESOURCES_DIRECTORY, resourcesContribution.getTask(), resourcesContribution.getDirectory()))
        }
        return variant
    }

    override fun configureClassesDirectoryVariant(configuration: Configuration, sourceSet: SourceSet): ConfigurationVariant {
        val publications = configuration.getOutgoing()
        val variant = publications.getVariants().maybeCreate("classes") as ConfigurationVariantInternal
        variant.getDescription().convention("Directories containing compiled class files for " + sourceSet.getName() + ".")
        val attributes: AttributeContainer = variant.getAttributes()
        attributes.attribute<LibraryElements?>(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, attributes.named<LibraryElements?>(LibraryElements::class.java, LibraryElements.CLASSES))
        variant.artifactsProvider(org.gradle.internal.Factory {
            val classesDirs = sourceSet.getOutput().getClassesDirs()
            classesDirs.getFiles().stream()
                .map<LazyJavaDirectoryArtifact?> { file: File? ->
                    DefaultJvmPluginServices.LazyJavaDirectoryArtifact(
                        taskDependencyFactory, ArtifactTypeDefinition.JVM_CLASS_DIRECTORY, classesDirs, providerFactory.provider<File?>(
                            Callable { file })
                    )
                }
                .collect(Collectors.toList())
        })
        return variant
    }

    /**
     * A custom artifact type which allows the getFile call to be done lazily only when the
     * artifact is actually needed.
     */
    private class LazyJavaDirectoryArtifact(taskDependencyFactory: TaskDependencyFactory, private val type: String, dependency: Any?, private val fileProvider: Provider<File>) :
        AbstractPublishArtifact(taskDependencyFactory, dependency) {
        override fun getName(): String {
            return getFile().getName()
        }

        override fun getExtension(): String {
            return ""
        }

        override fun getType(): String {
            return type
        }

        override fun getClassifier(): String? {
            return null
        }

        override fun getDate(): Date? {
            return null
        }

        override fun shouldBePublished(): Boolean {
            return false
        }

        override fun getFile(): File {
            return fileProvider.get()
        }
    }
}
