/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.model.eclipse

import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.HasGradleProject
import org.gradle.tooling.model.HierarchicalElement
import org.gradle.tooling.model.UnsupportedMethodException

/**
 * Represents the basic information about an Eclipse project.
 *
 * @since 1.0-milestone-3
 */
interface HierarchicalEclipseProject : HierarchicalElement, HasGradleProject {
    /**
     * {@inheritDoc}
     */
    override fun getParent(): HierarchicalEclipseProject?

    /**
     * {@inheritDoc}
     */
    override fun getChildren(): DomainObjectSet<out HierarchicalEclipseProject?>?

    /**
     * Returns the project dependencies for this project.
     *
     * @return The project dependencies. Returns an empty set if the project has no project dependencies.
     * @since 1.0-milestone-3
     */
    val projectDependencies: DomainObjectSet<out EclipseProjectDependency?>?

    /**
     * Returns the source directories for this project.
     *
     * @return The source directories. Returns an empty set if the project has no source directories.
     * @since 1.0-milestone-3
     */
    val sourceDirectories: DomainObjectSet<out EclipseSourceDirectory?>?

    /**
     * Returns the linked resources for this project.
     *
     * @return The linked resources.
     * @since 1.0-milestone-4
     */
    val linkedResources: DomainObjectSet<out EclipseLinkedResource?>?

    @get:Throws(UnsupportedMethodException::class)
    val projectDirectory: File?
}
