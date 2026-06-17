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
package org.gradle.api.publish.ivy.plugins

import org.apache.commons.lang3.StringUtils
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.NamedDomainObjectList
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.internal.ConfigurationCacheDegradation
import org.gradle.api.internal.artifacts.Module
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.internal.versionmapping.VersionMappingStrategyInternal
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.IvyPublication
import org.gradle.api.publish.ivy.internal.artifact.IvyArtifactNotationParserFactory
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublication
import org.gradle.api.publish.ivy.internal.publication.IvyPublicationInternal
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationCoordinates
import org.gradle.api.publish.ivy.internal.versionmapping.DefaultVersionMappingStrategy
import org.gradle.api.publish.ivy.tasks.GenerateIvyDescriptor
import org.gradle.api.publish.ivy.tasks.PublishToIvyRepository
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskContainer
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.model.Path
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Adds the ability to publish in the Ivy format to Ivy repositories.
 *
 * @see [Ivy Publishing reference](https://docs.gradle.org/current/userguide/publishing_ivy.html)
 *
 * @since 1.3
 */
abstract class IvyPublishPlugin @Inject constructor(
    private val instantiator: Instantiator,
    private val objectFactory: ObjectFactory,
    private val dependencyMetaDataProvider: DependencyMetaDataProvider,
    private val fileResolver: FileResolver,
    private val providerFactory: ProviderFactory
) : Plugin<Project?> {
    override fun apply(project: Project) {
        project.getPluginManager().apply(PublishingPlugin::class.java)

        project.getExtensions().configure<PublishingExtension?>(PublishingExtension::class.java, Action { extension: PublishingExtension? ->
            extension!!.publications.this!!.registerFactory<IvyPublication?>(
                IvyPublication::class.java,
                IvyPublicationFactory(
                    dependencyMetaDataProvider,
                    instantiator,
                    objectFactory,
                    fileResolver,
                    (project as ProjectInternal).getTaskDependencyFactory(),
                    providerFactory
                )
            )
            createTasksLater(project, extension, project.getLayout().getBuildDirectory())
        })
    }

    private fun createTasksLater(project: Project, publishingExtension: PublishingExtension, buildDir: DirectoryProperty) {
        val tasks = project.getTasks()
        val publications = publishingExtension.publications!!.withType<IvyPublicationInternal?>(IvyPublicationInternal::class.java)
        val repositories = publishingExtension.repositories!!.withType<IvyArtifactRepository?>(IvyArtifactRepository::class.java)
        repositories.all(Action { repository: IvyArtifactRepository? ->
            tasks.register(publishAllToSingleRepoTaskName(repository!!), Action { publish: Task? ->
                publish!!.setDescription("Publishes all Ivy publications produced by this project to the " + repository.getName() + " repository.")
                publish.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP)
            })
        })

        publications.all(Action { publication: IvyPublicationInternal? ->
            val publicationName = publication!!.getName()
            createGenerateIvyDescriptorTask(tasks, publicationName, publication, buildDir)
            createGenerateMetadataTask(tasks, publication, publications, buildDir, repositories)
            createPublishTaskForEachRepository(tasks, publication, publicationName, repositories)
        })
    }

    private fun publishAllToSingleRepoTaskName(repository: IvyArtifactRepository): String {
        return "publishAllPublicationsTo" + StringUtils.capitalize(repository.getName()) + "Repository"
    }

    private fun createPublishTaskForEachRepository(tasks: TaskContainer, publication: IvyPublicationInternal?, publicationName: String?, repositories: NamedDomainObjectList<IvyArtifactRepository?>) {
        repositories.all(Action { repository: IvyArtifactRepository? ->
            val repositoryName = repository!!.getName()
            val publishTaskName = "publish" + StringUtils.capitalize(publicationName) + "PublicationTo" + StringUtils.capitalize(repositoryName) + "Repository"
            createPublishToRepositoryTask(tasks, publication, publicationName, repository, repositoryName, publishTaskName)
        })
    }

    private fun createPublishToRepositoryTask(
        tasks: TaskContainer,
        publication: IvyPublicationInternal?,
        publicationName: String?,
        repository: IvyArtifactRepository,
        repositoryName: String?,
        publishTaskName: String
    ) {
        tasks.register<PublishToIvyRepository?>(publishTaskName, PublishToIvyRepository::class.java, Action { publishTask: PublishToIvyRepository? ->
            publishTask!!.setPublication(publication)
            publishTask.setRepository(repository)
            publishTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP)
            publishTask.setDescription("Publishes Ivy publication '" + publicationName + "' to Ivy repository '" + repositoryName + "'.")
        })
        tasks.withType<PublishToIvyRepository?>(PublishToIvyRepository::class.java)
            .configureEach(Action { task: PublishToIvyRepository? -> ConfigurationCacheDegradation.requireDegradation<PublishToIvyRepository?>(task, usingExplicitCredentials(task!!)) })

        tasks.named(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME, Action { task: Task? -> task!!.dependsOn(publishTaskName) })
        tasks.named(publishAllToSingleRepoTaskName(repository), Action { publish: Task? -> publish!!.dependsOn(publishTaskName) })
    }

    private fun usingExplicitCredentials(task: PublishToIvyRepository): Provider<String?> {
        return providerFactory.provider<AuthenticationSupportedInternal?>(Callable { task.getRepository() as AuthenticationSupportedInternal? })
            .flatMap<Boolean?>(Transformer { obj: AuthenticationSupportedInternal? -> obj!!.isUsingCredentialsProvider() })
            .map<String?>(Transformer { isUsingCredentialsProvider: Boolean? -> if (isUsingCredentialsProvider) null else "Explicit credentials are unsupported with the Configuration Cache" })
    }

    private fun createGenerateIvyDescriptorTask(tasks: TaskContainer, publicationName: String?, publication: IvyPublicationInternal, @Path("buildDir") buildDir: DirectoryProperty) {
        val descriptorTaskName = "generateDescriptorFileFor" + StringUtils.capitalize(publicationName) + "Publication"

        val generatorTask = tasks.register<GenerateIvyDescriptor?>(descriptorTaskName, GenerateIvyDescriptor::class.java, Action { descriptorTask: GenerateIvyDescriptor? ->
            descriptorTask!!.setDescription("Generates the Ivy Module Descriptor XML file for publication '" + publicationName + "'.")
            descriptorTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP)
            descriptorTask.setDescriptor(publication.descriptor)
            if (descriptorTask.getDestination() == null) {
                descriptorTask.setDestination(buildDir.file("publications/" + publicationName + "/ivy.xml"))
            }
        })
        publication.setIvyDescriptorGenerator(generatorTask)
    }

    private fun createGenerateMetadataTask(
        tasks: TaskContainer,
        publication: IvyPublicationInternal,
        publications: MutableSet<IvyPublicationInternal?>?,
        buildDir: DirectoryProperty,
        repositories: NamedDomainObjectList<IvyArtifactRepository?>
    ) {
        val publicationName = publication.getName()
        val descriptorTaskName = "generateMetadataFileFor" + StringUtils.capitalize(publicationName) + "Publication"
        val generatorTask = tasks.register<GenerateModuleMetadata?>(descriptorTaskName, GenerateModuleMetadata::class.java, Action { generateTask: GenerateModuleMetadata? ->
            generateTask!!.setDescription("Generates the Gradle metadata file for publication '" + publicationName + "'.")
            generateTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP)
            generateTask.getPublication()!!.set(publication)
            generateTask.getPublications()!!.set(publications)
            generateTask.outputFile.convention(buildDir.file("publications/" + publicationName + "/module.json"))
            Companion.disableGradleMetadataGenerationIfCustomLayout(repositories, generateTask)
        })
        publication.setModuleDescriptorGenerator(generatorTask)
    }

    private class CheckStandardLayoutSpec(private val standard: Provider<Boolean?>) : Spec<GenerateModuleMetadata?> {
        private val didWarn = AtomicBoolean()

        override fun isSatisfiedBy(element: GenerateModuleMetadata?): Boolean {
            if (!standard.get()!! && !didWarn.getAndSet(true)) {
                LOGGER!!.warn("Publication of Gradle Module Metadata is disabled because you have configured an Ivy repository with a non-standard layout")
            }
            return standard.get()!!
        }
    }

    private class IvyPublicationFactory(
        private val dependencyMetaDataProvider: DependencyMetaDataProvider, private val instantiator: Instantiator, private val objectFactory: ObjectFactory, private val fileResolver: FileResolver,
        private val taskDependencyFactory: TaskDependencyFactory, private val providerFactory: ProviderFactory
    ) : NamedDomainObjectFactory<IvyPublication?> {
        override fun create(name: String): IvyPublication {
            val module: Module = dependencyMetaDataProvider.module
            val publicationIdentity = objectFactory.newInstance<IvyPublicationCoordinates>(IvyPublicationCoordinates::class.java)
            publicationIdentity.getOrganisation().set(providerFactory.provider<String?>(module::getGroup))
            publicationIdentity.getModule().set(providerFactory.provider<String?>(module::getName))
            publicationIdentity.getRevision().set(providerFactory.provider<String?>(module::getVersion))

            val notationParser: NotationParser<Any?, IvyArtifact?> = IvyArtifactNotationParserFactory(instantiator, fileResolver, publicationIdentity, taskDependencyFactory).create()
            val versionMappingStrategy: VersionMappingStrategyInternal = objectFactory.newInstance<DefaultVersionMappingStrategy>(DefaultVersionMappingStrategy::class.java)

            return objectFactory.newInstance<DefaultIvyPublication>(DefaultIvyPublication::class.java, name, publicationIdentity, notationParser, versionMappingStrategy)
        }
    }

    companion object {
        private val LOGGER = getLogger(IvyPublishPlugin::class.java)

        private fun disableGradleMetadataGenerationIfCustomLayout(repositories: NamedDomainObjectList<IvyArtifactRepository?>, generateTask: GenerateModuleMetadata) {
            val standard: Provider<Boolean?> =
                DefaultProvider<Boolean?>(Callable { repositories.stream().allMatch { ivyArtifactRepository: IvyArtifactRepository? -> hasStandardPattern(ivyArtifactRepository) } })
            generateTask.onlyIf("The Ivy repositories follow the standard layout", uncheckedCast<Spec<in Task?>?>(CheckStandardLayoutSpec(standard))!!)
        }

        private fun hasStandardPattern(ivyArtifactRepository: ArtifactRepository?): Boolean {
            val repo = ivyArtifactRepository as DefaultIvyArtifactRepository
            return repo.hasStandardPattern()
        }
    }
}
