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
package org.gradle.plugin.devel.plugins

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import org.gradle.internal.service.ServiceRegistryBuilder.provider
import org.gradle.plugin.devel.GradlePluginDevelopmentExtension
import org.gradle.plugin.devel.PluginDeclaration
import org.gradle.plugin.use.resolve.internal.ArtifactRepositoriesPluginResolver
import java.util.concurrent.Callable
import javax.inject.Inject

internal abstract class MavenPluginPublishPlugin @Inject constructor() : Plugin<Project?> {
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

    private fun addMainPublication(publishing: PublishingExtension, mainComponent: SoftwareComponent?): MavenPublication {
        val publication = publishing.getPublications().maybeCreate<MavenPublication>("pluginMaven", MavenPublication::class.java)
        publication.from(mainComponent)
        return publication
    }

    private fun addMarkerPublications(mainPublication: MavenPublication, publishing: PublishingExtension, pluginDevelopment: GradlePluginDevelopmentExtension) {
        for (declaration in pluginDevelopment.getPlugins()) {
            createMavenMarkerPublication(declaration, mainPublication, publishing.getPublications())
        }
    }

    private fun createMavenMarkerPublication(declaration: PluginDeclaration, coordinates: MavenPublication, publications: PublicationContainer) {
        val pluginId = declaration.getId()
        val publication = publications.create<MavenPublication?>(declaration.getName() + "PluginMarkerMaven", MavenPublication::class.java) as MavenPublicationInternal
        publication.setAlias(true)
        publication.setArtifactId(pluginId + ArtifactRepositoriesPluginResolver.PLUGIN_MARKER_SUFFIX)
        publication.setGroupId(pluginId)

        val groupProvider = this.providerFactory.provider<String?>(Callable { coordinates.getGroupId() })
        val artifactIdProvider = this.providerFactory.provider<String?>(Callable { coordinates.getArtifactId() })
        val versionProvider = this.providerFactory.provider<String?>(Callable { coordinates.getVersion() })
        publication.getPom().withXml(object : Action<XmlProvider?> {
            override fun execute(xmlProvider: XmlProvider) {
                val root = xmlProvider.asElement()
                val document = root.getOwnerDocument()
                val dependencies = root.appendChild(document.createElement("dependencies"))
                val dependency = dependencies.appendChild(document.createElement("dependency"))
                val groupId = dependency.appendChild(document.createElement("groupId"))
                groupId.setTextContent(groupProvider.get())
                val artifactId = dependency.appendChild(document.createElement("artifactId"))
                artifactId.setTextContent(artifactIdProvider.get())
                val version = dependency.appendChild(document.createElement("version"))
                version.setTextContent(versionProvider.get())
            }
        })

        publication.getPom().getName().convention(declaration.getDisplayName())
        publication.getPom().getDescription().convention(declaration.getDescription())
    }
}
