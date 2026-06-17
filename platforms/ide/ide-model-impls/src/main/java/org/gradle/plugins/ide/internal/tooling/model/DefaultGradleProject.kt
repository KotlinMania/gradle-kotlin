/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.tooling.internal.gradle.GradleProjectIdentity
import java.io.File
import java.io.Serializable
import java.util.LinkedList

/**
 * Structurally implements [org.gradle.tooling.model.GradleProject] model.
 */
class DefaultGradleProject : Serializable, GradleProjectIdentity {
    val buildScript: DefaultGradleScript = DefaultGradleScript()
    var buildDirectory: File? = null
        private set
    var projectDirectory: File? = null
        private set
    private var tasks: MutableList<LaunchableGradleProjectTask?> = LinkedList<LaunchableGradleProjectTask?>()
    var name: String? = null
        private set
    var description: String? = null
        private set
    private var projectIdentifier: DefaultProjectIdentifier? = null
    var parent: DefaultGradleProject? = null
        private set
    private var children: MutableList<out DefaultGradleProject> = LinkedList<DefaultGradleProject>()
    var buildTreePath: String? = null
        private set

    fun setName(name: String?): DefaultGradleProject {
        this.name = name
        return this
    }

    fun setDescription(description: String?): DefaultGradleProject {
        this.description = description
        return this
    }

    fun setParent(parent: DefaultGradleProject?): DefaultGradleProject {
        this.parent = parent
        return this
    }

    fun getChildren(): MutableCollection<out DefaultGradleProject> {
        return children
    }

    fun setChildren(children: MutableList<out DefaultGradleProject>): DefaultGradleProject {
        this.children = ImmutableList.copyOf(children) // also ensures it's serializable
        return this
    }

    val path: String?
        get() = projectIdentifier!!.getProjectPath()

    fun getProjectIdentifier(): DefaultProjectIdentifier {
        return projectIdentifier!!
    }

    override fun getProjectPath(): String? {
        return projectIdentifier!!.getProjectPath()
    }

    override fun getRootDir(): File? {
        return projectIdentifier!!.getBuildIdentifier().getRootDir()
    }

    fun setProjectIdentifier(projectIdentifier: DefaultProjectIdentifier): DefaultGradleProject {
        this.projectIdentifier = projectIdentifier
        return this
    }

    fun findByPath(path: String): DefaultGradleProject? {
        if (path == this.path) {
            return this
        }
        for (child in children) {
            val found = child.findByPath(path)
            if (found != null) {
                return found
            }
        }

        return null
    }

    override fun toString(): String {
        return ("GradleProject{"
                + "path='" + this.path + '\''
                + '}')
    }

    fun getTasks(): MutableCollection<LaunchableGradleProjectTask?> {
        return tasks
    }

    fun setTasks(tasks: MutableList<LaunchableGradleProjectTask>): DefaultGradleProject {
        this.tasks = ImmutableList.copyOf<LaunchableGradleProjectTask?>(tasks) // also ensures it's serializable
        return this
    }

    fun setBuildDirectory(buildDirectory: File?): DefaultGradleProject {
        this.buildDirectory = buildDirectory
        return this
    }

    fun setProjectDirectory(projectDirectory: File?): DefaultGradleProject {
        this.projectDirectory = projectDirectory
        return this
    }

    fun setBuildTreePath(buildTreePath: String?): DefaultGradleProject {
        this.buildTreePath = buildTreePath
        return this
    }
}
