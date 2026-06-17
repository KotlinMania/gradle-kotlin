/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.tooling.model.gradle

import org.gradle.api.Incubating
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.ProjectModel

/**
 * Provides some basic details about a Gradle project.
 *
 * @since 1.8
 */
interface BasicGradleProject : Model, ProjectModel {
    /**
     * Returns the identifier for this Gradle project.
     *
     * @since 2.13
     */
    override fun getProjectIdentifier(): ProjectIdentifier?

    /**
     * Returns the name of this project. Note that the name is not a unique identifier for the project.
     *
     * @return The name of this project.
     */
    val name: String?

    /**
     * Returns the path of this project. The path can be used as a unique identifier for the project within a given build.
     *
     * @return The path of this project.
     */
    val path: String?

    /**
     * Returns the project directory for this project.
     *
     * @return The project directory.
     */
    val projectDirectory: File?

    /**
     * Returns the parent of this project, or `null` if this is the root project.
     *
     * @return The parent of this project, or `null` if this is the root project.
     */
    val parent: BasicGradleProject?

    /**
     * Returns the child projects of this project, or the empty set if there are no child projects.
     *
     * @return The child projects of this project, or the empty set if there are no child projects.
     */
    val children: DomainObjectSet<out BasicGradleProject?>?

    @get:Incubating
    val buildTreePath: String?
}
