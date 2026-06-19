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
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.UnsupportedMethodException

/**
 * The complete model of an Eclipse project.
 *
 *
 * Note that the names of Eclipse projects are unique, and can be used as an identifier for the project.
 *
 * @since 1.0-milestone-3
 */
interface EclipseProject : HierarchicalEclipseProject {
    /**
     * {@inheritDoc}
     */
    override val parent: EclipseProject?

    /**
     * {@inheritDoc}
     */
    override val children: DomainObjectSet<out EclipseProject?>?

    @get:Throws(UnsupportedMethodException::class)
    val javaSourceSettings: EclipseJavaSourceSettings?

    /**
     * The gradle project that is associated with this project.
     * Typically, a single Eclipse project corresponds to a single gradle project.
     *
     *
     * See [HasGradleProject]
     *
     * @return associated gradle project
     * @since 1.0-milestone-5
     */
    override val gradleProject: GradleProject?

    /**
     * Returns the external dependencies which make up the classpath of this project.
     *
     * @return The dependencies. Returns an empty set if the project has no external dependencies.
     * @since 1.0-milestone-3
     */
    val classpath: DomainObjectSet<out EclipseExternalDependency?>?

    @get:Throws(UnsupportedMethodException::class)
    val projectNatures: DomainObjectSet<out EclipseProjectNature?>?

    @get:Throws(UnsupportedMethodException::class)
    val buildCommands: DomainObjectSet<out EclipseBuildCommand?>?

    @get:Throws(UnsupportedMethodException::class)
    val classpathContainers: DomainObjectSet<out EclipseClasspathContainer?>?

    @get:Throws(UnsupportedMethodException::class)
    val outputLocation: EclipseOutputLocation?

    /**
     * If this method returns true then Eclipse should execute the tasks configured at `eclipse.autoBuildTasks`
     * every time the auto-build is triggered for the target project.
     *
     * @return whether the project has auto-build tasks configured
     * @since 5.4
     * @see RunEclipseAutoBuildTasks
     */
    fun hasAutoBuildTasks(): Boolean
}
