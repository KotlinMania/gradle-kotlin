/*
 * Copyright 2014 the original author or authors.
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
 * Structurally implements [org.gradle.tooling.model.Task] model.
 */
open class LaunchableGradleTask : Serializable, InternalLaunchable, TaskExecutionRequest {
    var path: String? = null
        private set
    var name: String? = null
        private set
    var description: String? = null
        private set
    var displayName: String? = null
        private set
    var group: String? = null
        private set
    var isPublic: Boolean = false
        private set
    private var projectIdentifier: DefaultProjectIdentifier? = null
    var buildTreePath: String? = null
        private set

    fun setPath(path: String?): LaunchableGradleTask {
        this.path = path
        return this
    }

    fun setBuildTreePath(buildTreePath: String?): LaunchableGradleTask {
        this.buildTreePath = buildTreePath
        return this
    }

    fun setName(name: String?): LaunchableGradleTask {
        this.name = name
        return this
    }

    fun setDisplayName(displayName: String?): LaunchableGradleTask {
        this.displayName = displayName
        return this
    }

    fun setDescription(description: String?): LaunchableGradleTask {
        this.description = description
        return this
    }

    override fun getArgs(): MutableList<String?> {
        return mutableListOf<String?>(path)
    }

    override fun getProjectPath(): String? {
        return null
    }

    fun setGroup(group: String?): LaunchableGradleTask {
        this.group = group
        return this
    }

    fun setPublic(isPublic: Boolean): LaunchableGradleTask {
        this.isPublic = isPublic
        return this
    }

    override fun getRootDir(): File? {
        return projectIdentifier!!.getBuildIdentifier().getRootDir()
    }

    fun setProjectIdentifier(projectIdentifier: DefaultProjectIdentifier): LaunchableGradleTask {
        this.projectIdentifier = projectIdentifier
        return this
    }

    fun getProjectIdentifier(): DefaultProjectIdentifier {
        return projectIdentifier!!
    }

    override fun toString(): String {
        return javaClass.getSimpleName() + "{path='" + path + "',public=" + isPublic + "}"
    }
}
