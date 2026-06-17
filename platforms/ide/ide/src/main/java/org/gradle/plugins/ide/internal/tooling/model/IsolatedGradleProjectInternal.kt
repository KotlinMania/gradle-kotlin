/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.plugins.ide.internal.tooling.model

import com.google.common.collect.ImmutableList
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.jspecify.annotations.NullMarked
import java.io.File
import java.io.Serializable

/**
 * Represents a Gradle project, isolated from the project hierarchy.
 *
 *
 * **This model is internal, and is NOT part of the public Tooling API.**
 */
@NullMarked
class IsolatedGradleProjectInternal : Serializable {
    val buildScript: DefaultGradleScript = DefaultGradleScript()
    private var buildDirectory: File? = null
    private var projectDirectory: File? = null
    private var tasks: MutableList<LaunchableGradleTask> = ImmutableList.of<LaunchableGradleTask>()
    private var name: String? = null
    var description: String? = null
        private set
    private var projectIdentifier: DefaultProjectIdentifier? = null

    fun getName(): String {
        return name!!
    }

    fun setName(name: String): IsolatedGradleProjectInternal {
        this.name = name
        return this
    }

    fun setDescription(description: String?): IsolatedGradleProjectInternal {
        this.description = description
        return this
    }

    fun getProjectIdentifier(): DefaultProjectIdentifier {
        return projectIdentifier!!
    }

    fun setProjectIdentifier(projectIdentifier: DefaultProjectIdentifier): IsolatedGradleProjectInternal {
        this.projectIdentifier = projectIdentifier
        return this
    }

    fun getTasks(): MutableCollection<LaunchableGradleTask> {
        return tasks
    }

    fun setTasks(tasks: MutableList<LaunchableGradleTask>): IsolatedGradleProjectInternal {
        this.tasks = ImmutableList.copyOf<LaunchableGradleTask>(tasks) // also ensures it's serializable
        return this
    }

    fun getBuildDirectory(): File {
        return buildDirectory!!
    }

    fun setBuildDirectory(buildDirectory: File): IsolatedGradleProjectInternal {
        this.buildDirectory = buildDirectory
        return this
    }

    fun getProjectDirectory(): File {
        return projectDirectory!!
    }

    fun setProjectDirectory(projectDirectory: File): IsolatedGradleProjectInternal {
        this.projectDirectory = projectDirectory
        return this
    }

    override fun toString(): String {
        return "IsolatedGradleProject{" + projectIdentifier + "}"
    }
}
