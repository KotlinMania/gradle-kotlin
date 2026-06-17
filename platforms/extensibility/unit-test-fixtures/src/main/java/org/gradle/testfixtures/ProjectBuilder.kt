/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.testfixtures

import org.gradle.api.Project
import org.gradle.testfixtures.internal.ProjectBuilderImpl
import java.io.File

/**
 *
 * Creates dummy instances of [Project] which you can use in testing custom task and plugin
 * implementations.
 *
 *
 * To create a project instance:
 *
 *
 *
 *  1. Create a `ProjectBuilder` instance by calling [.builder].
 *
 *  1. Optionally, configure the builder.
 *
 *  1. Call [.build] to create the `Project` instance.
 *
 *
 *
 *
 * You can reuse a builder to create multiple `Project` instances.
 *
 *
 * The `ProjectBuilder` implementation bundled with Gradle 3.0 and 3.1 suffers from a
 * binary compatibility issue exposed by applying plugins compiled with Gradle 2.7 and earlier.
 * Applying those pre-compiled plugins in a ProjectBuilder context will result in a [ClassNotFoundException].
 */
class ProjectBuilder
/**
 * An instance should only be created via the [.builder].
 */
private constructor() {
    private var name = "test"
    private var projectDir: File? = null
    private var gradleUserHomeDir: File? = null
    private var parent: Project? = null
    private val impl = ProjectBuilderImpl()

    /**
     * Specifies the project directory for the project to build.
     *
     * @param dir The project directory
     * @return The builder
     */
    fun withProjectDir(dir: File?): ProjectBuilder {
        projectDir = dir
        return this
    }

    /**
     * Specifies the Gradle user home for the builder. If not set, an empty directory under the project directory
     * will be used.
     *
     * @return The builder
     */
    fun withGradleUserHomeDir(dir: File?): ProjectBuilder {
        gradleUserHomeDir = dir
        return this
    }

    /**
     * Specifies the name for the project
     *
     * @param name project name
     * @return The builder
     */
    fun withName(name: String): ProjectBuilder {
        this.name = name
        return this
    }

    /**
     * Specifies the parent project. Use it to create multi-module projects.
     *
     * @param parent parent project
     * @return The builder
     */
    fun withParent(parent: Project?): ProjectBuilder {
        this.parent = parent
        return this
    }

    /**
     * Creates the project.
     *
     * @return The project
     */
    fun build(): Project {
        if (parent != null) {
            return impl.createChildProject(name, parent!!, projectDir)
        }
        return impl.createProject(name, projectDir, gradleUserHomeDir)
    }

    companion object {
        /**
         * Creates a project builder.
         *
         * @return The builder
         */
        fun builder(): ProjectBuilder {
            return ProjectBuilder()
        }
    }
}
