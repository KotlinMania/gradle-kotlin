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
package org.gradle.api.publish

import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.jspecify.annotations.NullMarked
import javax.inject.Inject

/**
 * The configuration of how to "publish" the different components of a project.
 *
 * @since 1.3
 */
interface PublishingExtension {
    /**
     * The container of possible repositories to publish to.
     *
     *
     * See [.repositories] for more information.
     *
     * @return The container of possible repositories to publish to.
     */
    val repositories: RepositoryHandler?

    /**
     * Configures the container of possible repositories to publish to.
     *
     * <pre class='autoTested'>
     * plugins {
     * id 'publishing'
     * }
     *
     * publishing {
     * repositories {
     * // Create an ivy publication destination named "releases"
     * ivy {
     * name = "releases"
     * url = "http://my.org/ivy-repos/releases"
     * }
     * }
     * }
    </pre> *
     *
     * The `repositories` block is backed by a [RepositoryHandler], which is the same DSL as that that is used for declaring repositories to consume dependencies from. However,
     * certain types of repositories that can be created by the repository handler are not valid for publishing, such as [RepositoryHandler.mavenCentral].
     *
     *
     * At this time, only repositories created by the `ivy()` factory method have any effect. Please see [org.gradle.api.publish.ivy.IvyPublication]
     * for information on how this can be used for publishing to Ivy repositories.
     *
     * @param configure The action to configure the container of repositories with.
     */
    fun repositories(configure: Action<in RepositoryHandler?>?)

    /**
     * The publications of the project.
     *
     *
     * See [.publications] for more information.
     *
     * @return The publications of this project.
     */
    val publications: PublicationContainer?

    /**
     * Configures the publications of this project.
     *
     *
     * The publications container defines the outgoing publications of the project. That is, the consumable representations of things produced
     * by building the project. An example of a publication would be an Ivy Module (i.e. `ivy.xml` and artifacts), or
     * Maven Project (i.e. `pom.xml` and artifacts).
     *
     *
     * Actual publication implementations and the ability to create them are provided by different plugins. The "publishing" plugin itself does not provide any publication types.
     * For example, given that the 'maven-publish' plugin provides a [org.gradle.api.publish.maven.MavenPublication] type, you can create a publication like:
     * <pre class='autoTested'>
     * plugins {
     * id 'maven-publish'
     * }
     *
     * publishing {
     * publications {
     * myPublicationName(MavenPublication) {
     * // Configure the publication here
     * }
     * }
     * }
    </pre> *
     *
     *
     * Please see [org.gradle.api.publish.ivy.IvyPublication] and [org.gradle.api.publish.maven.MavenPublication] for more information on publishing in these specific formats.
     *
     * @param configure The action or closure to configure the publications with.
     */
    fun publications(configure: Action<in PublicationContainer?>?)

    @get:NullMarked
    @get:Inject
    val softwareComponentFactory: SoftwareComponentFactory?

    companion object {
        /**
         * The name of this extension when installed by the [org.gradle.api.publish.plugins.PublishingPlugin] ({@value}).
         */
        const val NAME: String = "publishing"
    }
}
