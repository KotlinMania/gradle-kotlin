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
package org.gradle.api.publish.plugins

import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.artifacts.ArtifactPublicationServices
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublicationContainer
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.internal.DefaultPublicationContainer
import org.gradle.api.publish.internal.DefaultPublishingExtension
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.reflect.Instantiator
import javax.inject.Inject

/**
 * Installs a [PublishingExtension] with name {@value org.gradle.api.publish.PublishingExtension#NAME}.
 *
 * @since 1.3
 * @see [Publishing reference](https://docs.gradle.org/current/userguide/publishing_setup.html.publishing_overview)
 */
abstract class PublishingPlugin @Inject constructor(
    private val publicationServices: ArtifactPublicationServices,
    private val instantiator: Instantiator,
    private val projectPublicationRegistry: ProjectPublicationRegistry,
    private val collectionCallbackActionDecorator: CollectionCallbackActionDecorator?
) : Plugin<Project?> {
    override fun apply(project: Project) {
        val repositories = publicationServices.createRepositoryHandler()
        val publications: PublicationContainer = instantiator.newInstance<DefaultPublicationContainer>(DefaultPublicationContainer::class.java, instantiator, collectionCallbackActionDecorator)
        val extension = project.getExtensions()
            .create<PublishingExtension>(PublishingExtension::class.java, PublishingExtension.Companion.NAME, DefaultPublishingExtension::class.java, repositories!!, publications)
        project.getTasks().register(PUBLISH_LIFECYCLE_TASK_NAME, Action { task: Task? ->
            task!!.setDescription("Publishes all publications produced by this project.")
            task.setGroup(PUBLISH_TASK_GROUP)
        })

        val projectIdentity = (project as ProjectInternal).getProjectIdentity()
        extension.getPublications().all(Action { publication: Publication? ->
            val internalPublication = uncheckedNonnullCast<PublicationInternal<*>?>(publication)
            projectPublicationRegistry.registerPublication(projectIdentity, internalPublication!!)
        })
        validatePublishingModelWhenComplete(project, extension)
    }

    private fun validatePublishingModelWhenComplete(project: Project, extension: PublishingExtension) {
        project.afterEvaluate(Action { projectAfterEvaluate: Project? ->
            for (repository in extension.getRepositories()) {
                val repositoryName = repository.getName()
                if (!repositoryName.matches(VALID_NAME_REGEX.toRegex())) {
                    throw InvalidUserDataException("Repository name '" + repositoryName + "' is not valid for publication. Must match regex " + VALID_NAME_REGEX + ".")
                }
            }
            for (publication in extension.getPublications()) {
                val publicationName = publication.getName()
                if (!publicationName.matches(VALID_NAME_REGEX.toRegex())) {
                    throw InvalidUserDataException("Publication name '" + publicationName + "' is not valid for publication. Must match regex " + VALID_NAME_REGEX + ".")
                }
            }
        })
    }

    companion object {
        const val PUBLISH_TASK_GROUP: String = "publishing"
        const val PUBLISH_LIFECYCLE_TASK_NAME: String = "publish"
        private const val VALID_NAME_REGEX = "[A-Za-z0-9_\\-.]+"
    }
}
