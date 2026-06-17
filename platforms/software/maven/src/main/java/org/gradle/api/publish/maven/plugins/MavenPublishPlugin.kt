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
package org.gradle.api.publish.maven.plugins

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.NamedDomainObjectList
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.ConfigurationCacheDegradation
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.internal.versionmapping.DefaultVersionMappingStrategy
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.artifact.MavenArtifactNotationParserFactory
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.reflect.Instantiator
import java.util.concurrent.Callable
import javax.inject.Inject

/**
 * Adds the ability to publish in the Maven format to Maven repositories.
 *
 * @since 1.4
 * @see [Maven Publishing reference](https://docs.gradle.org/current/userguide/publishing_maven.html)
 */
abstract class MavenPublishPlugin @Inject constructor(
    private val instantiatorFactory: InstantiatorFactory, private val objectFactory: ObjectFactory, private val dependencyMetaDataProvider: DependencyMetaDataProvider?,
    private val fileResolver: FileResolver?,
    private val taskDependencyFactory: TaskDependencyFactory?
) : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(PublishingPlugin::class.java)

        val tasks = project.getTasks()
        tasks.register(PUBLISH_LOCAL_LIFECYCLE_TASK_NAME, Action { publish: Task? ->
            publish!!.setDescription("Publishes all Maven publications produced by this project to the local Maven cache.")
            publish.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP)
        })

        project.getExtensions().configure<PublishingExtension?>(PublishingExtension::class.java, Action { extension: PublishingExtension? ->
            extension!!.publications.registerFactory<MavenPublication?>(
                MavenPublication::class.java, MavenPublishPlugin.MavenPublicationFactory(
                    dependencyMetaDataProvider,
                    instantiatorFactory.decorateLenient(),
                    fileResolver
                )
            )
            realizePublishingTasksLater(project, extension)
        })
    }

    private fun realizePublishingTasksLater(project: Project, extension: PublishingExtension) {
        val mavenPublications = extension.publications!!.withType<MavenPublicationInternal?>(MavenPublicationInternal::class.java)
        val tasks = project.getTasks()
        val buildDirectory = project.getLayout().getBuildDirectory()

        val publishLifecycleTask: TaskProvider<Task?> = tasks.named(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME)
        val publishLocalLifecycleTask: TaskProvider<Task?> = tasks.named(PUBLISH_LOCAL_LIFECYCLE_TASK_NAME)
        val repositories = extension.repositories!!.withType<MavenArtifactRepository?>(MavenArtifactRepository::class.java)

        repositories.all(Action { repository: MavenArtifactRepository? ->
            tasks.register(publishAllToSingleRepoTaskName(repository!!), Action { publish: Task? ->
                publish!!.setDescription("Publishes all Maven publications produced by this project to the " + repository.getName() + " repository.")
                publish.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP)
            })
        })

        mavenPublications.all(Action { publication: MavenPublicationInternal? ->
            createGenerateMetadataTask(tasks, publication!!, mavenPublications, buildDirectory)
            createGeneratePomTask(tasks, publication, buildDirectory)
            createLocalInstallTask(tasks, publishLocalLifecycleTask, publication)
            createPublishTasksForEachMavenRepo(tasks, publishLifecycleTask, publication, repositories)
        })
    }

    private fun publishAllToSingleRepoTaskName(repository: MavenArtifactRepository): String {
        return "publishAllPublicationsTo" + StringUtils.capitalize(repository.getName()) + "Repository"
    }

    private fun createPublishTasksForEachMavenRepo(
        tasks: TaskContainer,
        publishLifecycleTask: TaskProvider<Task?>,
        publication: MavenPublicationInternal,
        repositories: NamedDomainObjectList<MavenArtifactRepository?>
    ) {
        val publicationName = publication.getName()
        repositories.all(Action { repository: MavenArtifactRepository? ->
            val repositoryName = repository!!.getName()
            val publishTaskName = "publish" + StringUtils.capitalize(publicationName) + "PublicationTo" + StringUtils.capitalize(repositoryName) + "Repository"
            tasks.register<PublishToMavenRepository?>(publishTaskName, PublishToMavenRepository::class.java, Action { publishTask: PublishToMavenRepository? ->
                publishTask!!.setPublication(publication)
                publishTask.setRepository(repository)
                publishTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP)
                publishTask.setDescription("Publishes Maven publication '" + publicationName + "' to Maven repository '" + repositoryName + "'.")
            })
            tasks.withType<PublishToMavenRepository?>(PublishToMavenRepository::class.java).configureEach(
                Action { task: PublishToMavenRepository? -> ConfigurationCacheDegradation.requireDegradation<PublishToMavenRepository?>(task, usingExplicitCredentials(task!!)) }
            )

            publishLifecycleTask.configure(Action { task: Task? -> task!!.dependsOn(publishTaskName) })
            tasks.named(publishAllToSingleRepoTaskName(repository), Action { publish: Task? -> publish!!.dependsOn(publishTaskName) })
        })
    }

    private fun usingExplicitCredentials(task: PublishToMavenRepository): Provider<String?> {
        return DefaultProvider<AuthenticationSupportedInternal?>(Callable { task.getRepository() as AuthenticationSupportedInternal? })
            .flatMap<Boolean?>(Transformer { obj: AuthenticationSupportedInternal? -> obj!!.isUsingCredentialsProvider() })
            .map<String?>(Transformer { isUsingCredentialsProvider: Boolean? -> if (isUsingCredentialsProvider) null else "Explicit credentials are unsupported with the Configuration Cache" })
    }

    private fun createLocalInstallTask(tasks: TaskContainer, publishLocalLifecycleTask: TaskProvider<Task?>, publication: MavenPublicationInternal) {
        val publicationName = publication.getName()
        val installTaskName = "publish" + StringUtils.capitalize(publicationName) + "PublicationToMavenLocal"

        tasks.register<PublishToMavenLocal?>(installTaskName, PublishToMavenLocal::class.java, Action { publishLocalTask: PublishToMavenLocal? ->
            publishLocalTask!!.setPublication(publication)
            publishLocalTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP)
            publishLocalTask.setDescription("Publishes Maven publication '" + publicationName + "' to the local Maven repository.")
        })
        publishLocalLifecycleTask.configure(Action { task: Task? -> task!!.dependsOn(installTaskName) })
    }

    private fun createGeneratePomTask(tasks: TaskContainer, publication: MavenPublicationInternal, buildDir: DirectoryProperty) {
        val publicationName = publication.getName()
        val descriptorTaskName = "generatePomFileFor" + StringUtils.capitalize(publicationName) + "Publication"
        val generatorTask = tasks.register<GenerateMavenPom?>(descriptorTaskName, GenerateMavenPom::class.java, Action { generatePomTask: GenerateMavenPom? ->
            generatePomTask!!.setDescription("Generates the Maven POM file for publication '" + publicationName + "'.")
            generatePomTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP)
            generatePomTask.setPom(publication.getPom())
            if (generatePomTask.getDestination() == null) {
                generatePomTask.setDestination(buildDir.file("publications/" + publication.getName() + "/pom-default.xml"))
            }
        })
        publication.setPomGenerator(generatorTask)
    }

    private fun createGenerateMetadataTask(tasks: TaskContainer, publication: MavenPublicationInternal, publications: MutableSet<out MavenPublicationInternal?>?, buildDir: DirectoryProperty) {
        val publicationName = publication.getName()
        val descriptorTaskName = "generateMetadataFileFor" + StringUtils.capitalize(publicationName) + "Publication"
        val generatorTask = tasks.register<GenerateModuleMetadata?>(descriptorTaskName, GenerateModuleMetadata::class.java, Action { generateTask: GenerateModuleMetadata? ->
            generateTask!!.setDescription("Generates the Gradle metadata file for publication '" + publicationName + "'.")
            generateTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP)
            generateTask.getPublication()!!.set(publication)
            generateTask.getPublications()!!.set(publications)
            generateTask.outputFile.convention(buildDir.file("publications/" + publication.getName() + "/module.json"))
        })
        publication.setModuleDescriptorGenerator(generatorTask)
    }

    private inner class MavenPublicationFactory(
        private val dependencyMetaDataProvider: DependencyMetaDataProvider?,
        private val instantiator: Instantiator?,
        private val fileResolver: FileResolver?
    ) : NamedDomainObjectFactory<MavenPublication?> {
        override fun create(name: String): MavenPublication {
            val artifactNotationParser = MavenArtifactNotationParserFactory(instantiator, fileResolver, taskDependencyFactory).create()
            val versionMappingStrategy: VersionMappingStrategyInternal = objectFactory.newInstance<DefaultVersionMappingStrategy>(DefaultVersionMappingStrategy::class.java)
            return objectFactory.newInstance<DefaultMavenPublication>(
                DefaultMavenPublication::class.java,
                name,
                dependencyMetaDataProvider!!,
                artifactNotationParser!!,
                versionMappingStrategy
            )
        }
    }

    companion object {
        const val PUBLISH_LOCAL_LIFECYCLE_TASK_NAME: String = "publishToMavenLocal"
    }
}
