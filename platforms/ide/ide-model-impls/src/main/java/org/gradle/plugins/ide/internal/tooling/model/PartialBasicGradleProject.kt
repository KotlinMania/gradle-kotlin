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

import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.internal.gradle.GradleProjectIdentity
import java.io.File
import java.io.Serializable

open class PartialBasicGradleProject : Serializable, GradleProjectIdentity {
    var name: String? = null
        private set
    private var projectIdentifier: DefaultProjectIdentifier? = null
    var parent: PartialBasicGradleProject? = null
        private set
    private val children: MutableSet<PartialBasicGradleProject?> = LinkedHashSet<PartialBasicGradleProject?>()

    override fun toString(): String {
        return "GradleProject{path='" + this.path + "\'}"
    }

    val path: String
        get() = projectIdentifier!!.getProjectPath()

    fun setParent(parent: PartialBasicGradleProject?): PartialBasicGradleProject {
        this.parent = parent
        return this
    }

    open fun setName(name: String?): PartialBasicGradleProject? {
        this.name = name
        return this
    }

    open fun getChildren(): MutableSet<out PartialBasicGradleProject?> {
        return children
    }

    fun addChild(child: PartialBasicGradleProject?): PartialBasicGradleProject {
        children.add(child)
        return this
    }

    fun getProjectIdentifier(): DefaultProjectIdentifier {
        return projectIdentifier!!
    }

    override fun getProjectPath(): String {
        return projectIdentifier!!.getProjectPath()
    }

    override fun getRootDir(): File? {
        return projectIdentifier!!.getBuildIdentifier().getRootDir()
    }

    open fun setProjectIdentifier(projectIdentifier: DefaultProjectIdentifier): PartialBasicGradleProject? {
        this.projectIdentifier = projectIdentifier
        return this
    }
}
