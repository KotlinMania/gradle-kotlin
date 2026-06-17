/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.plugin.devel.plugins

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.ivy.IvyModuleDescriptorDescription
import org.gradle.api.publish.ivy.IvyModuleDescriptorSpec
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.internal.publication.IvyPublicationInternal
import org.gradle.internal.service.ServiceRegistryBuilder.provider
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.PluginDeclaration
import org.gradle.plugin.use.resolve.internal.ArtifactRepositoriesPluginResolver
import java.util.concurrent.Callable
import javax.inject.Inject

internal abstract class IvyPluginPublishingPlugin @Inject constructor() : Plugin<Project?> {
    @get:Inject
    protected abstract val providerFactory: ProviderFactory?

    override fun apply(project: Project) {
        project.afterEvaluate(object : Action<Project?> {
            override fun execute(project: Project) {
                configurePublishing(project)
            }
        })
    }

    private fun configurePublishing(project: Project) {
        project.getExtensions().configure<PublishingExtension?>(PublishingExtension::class.java, object : Action<PublishingExtension?> {
            override fun execute(publishing: PublishingExtension) {
                val pluginDevelopment = project.getExtensions().getByType<GradlePluginDevelopmentExtension>(GradlePluginDevelopmentExtension::class.java)
                if (!pluginDevelopment.isAutomatedPublishing()) {
                    return
                }
                val mainComponent = project.getComponents().getByName("java")
                val mainPublication = addMainPublication(publishing, mainComponent)
                addMarkerPublications(mainPublication, publishing, pluginDevelopment)
            }
        })
    }

    private fun addMainPublication(publishing: PublishingExtension, mainComponent: SoftwareComponent?): IvyPublication {
        val publication = publishing.getPublications().maybeCreate<IvyPublication>("pluginIvy", IvyPublication::class.java)
        publication.from(mainComponent)
        return publication
    }

    private fun addMarkerPublications(mainPublication: IvyPublication, publishing: PublishingExtension, pluginDevelopment: GradlePluginDevelopmentExtension) {
        for (declaration in pluginDevelopment.getPlugins()) {
            createIvyMarkerPublication(declaration, mainPublication, publishing.getPublications())
        }
    }

    private fun createIvyMarkerPublication(declaration: PluginDeclaration, mainPublication: IvyPublication, publications: PublicationContainer) {
        val pluginId = declaration.getId()
        val publication = publications.create<IvyPublication?>(declaration.getName() + "PluginMarkerIvy", IvyPublication::class.java) as IvyPublicationInternal
        publication.setAlias(true)
        publication.setOrganisation(pluginId)
        publication.setModule(pluginId + ArtifactRepositoriesPluginResolver.PLUGIN_MARKER_SUFFIX)

        val organisation = this.providerFactory.provider<String?>(Callable { mainPublication.getOrganisation() })
        val module = this.providerFactory.provider<String?>(Callable { mainPublication.getModule() })
        val revision = this.providerFactory.provider<String?>(Callable { mainPublication.getRevision() })

        publication.descriptor(object : Action<IvyModuleDescriptorSpec?> {
            override fun execute(descriptor: IvyModuleDescriptorSpec) {
                descriptor.description(object : Action<IvyModuleDescriptorDescription?> {
                    override fun execute(description: IvyModuleDescriptorDescription) {
                        description.getText().set(declaration.getDescription())
                    }
                })
                descriptor.withXml(object : Action<XmlProvider?> {
                    override fun execute(xmlProvider: XmlProvider) {
                        val root = xmlProvider.asElement()
                        val document = root.getOwnerDocument()
                        val dependencies = root.getElementsByTagName("dependencies").item(0)
                        val dependency = dependencies.appendChild(document.createElement("dependency"))
                        val org = document.createAttribute("org")
                        org.setValue(organisation.get())
                        dependency.getAttributes().setNamedItem(org)
                        val name = document.createAttribute("name")
                        name.setValue(module.get())
                        dependency.getAttributes().setNamedItem(name)
                        val rev = document.createAttribute("rev")
                        rev.setValue(revision.get())
                        dependency.getAttributes().setNamedItem(rev)
                    }
                })
            }
        })
    }
}
