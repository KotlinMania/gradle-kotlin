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

import com.google.common.base.MoreObjects
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.internal.gradle.GradleProjectIdentity
import org.gradle.tooling.model.GradleModuleVersion
import java.io.File
import java.io.Serializable

/**
 * Structurally implements [org.gradle.tooling.model.gradle.GradlePublication] model.
 */
class DefaultGradlePublication : Serializable, GradleProjectIdentity {
    var id: GradleModuleVersion? = null
        private set
    private var projectIdentifier: DefaultProjectIdentifier? = null

    fun setId(id: GradleModuleVersion?): DefaultGradlePublication {
        this.id = id
        return this
    }

    fun getProjectIdentifier(): DefaultProjectIdentifier {
        return projectIdentifier!!
    }

    override fun getProjectPath(): String? {
        return projectIdentifier!!.getProjectPath()
    }

    override fun getRootDir(): File? {
        return projectIdentifier!!.getBuildIdentifier().getRootDir()
    }

    fun setProjectIdentifier(projectIdentifier: DefaultProjectIdentifier): DefaultGradlePublication {
        this.projectIdentifier = projectIdentifier
        return this
    }

    override fun toString(): String {
        return MoreObjects.toStringHelper(this)
            .add("id", id)
            .toString()
    }
}
