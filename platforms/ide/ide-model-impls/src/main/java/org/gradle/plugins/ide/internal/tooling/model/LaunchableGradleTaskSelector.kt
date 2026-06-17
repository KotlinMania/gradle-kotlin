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
package org.gradle.plugins.ide.internal.tooling.model

import org.gradle.TaskExecutionRequest
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.internal.protocol.InternalLaunchable
import java.io.File
import java.io.Serializable

/**
 * Data used for [org.gradle.tooling.model.TaskSelector].
 */
class LaunchableGradleTaskSelector : InternalLaunchable, TaskExecutionRequest, Serializable {
    var name: String? = null
        private set
    var path: String? = null
        private set
    var displayName: String? = null
        private set
    var description: String? = null
        private set
    private var taskName: String? = null
    var isPublic: Boolean = false
        private set
    private var projectIdentifier: DefaultProjectIdentifier? = null

    fun setName(name: String?): LaunchableGradleTaskSelector {
        this.name = name
        return this
    }

    fun setDescription(description: String?): LaunchableGradleTaskSelector {
        this.description = description
        return this
    }

    fun setDisplayName(displayName: String?): LaunchableGradleTaskSelector {
        this.displayName = displayName
        return this
    }

    override fun getArgs(): MutableList<String?> {
        return mutableListOf<String?>(taskName)
    }

    fun setTaskName(taskName: String?): LaunchableGradleTaskSelector {
        this.taskName = taskName
        return this
    }

    override fun getProjectPath(): String? {
        return projectIdentifier!!.getProjectPath()
    }

    fun setPublic(isPublic: Boolean): LaunchableGradleTaskSelector {
        this.isPublic = isPublic
        return this
    }

    fun getProjectIdentifier(): DefaultProjectIdentifier {
        return projectIdentifier!!
    }

    override fun getRootDir(): File? {
        return projectIdentifier!!.getBuildIdentifier().getRootDir()
    }

    fun setProjectIdentifier(projectIdentifier: DefaultProjectIdentifier): LaunchableGradleTaskSelector {
        this.projectIdentifier = projectIdentifier
        return this
    }

    fun setPath(path: String?): LaunchableGradleTaskSelector {
        this.path = path
        return this
    }

    override fun toString(): String {
        return ("LaunchableGradleTaskSelector{"
                + "name='" + name + "' "
                + "description='" + description + "'}")
    }
}
