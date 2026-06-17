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
import java.io.File

class BasicGradleProject : PartialBasicGradleProject() {
    var projectDirectory: File? = null
        private set
    private val children: MutableSet<BasicGradleProject?> = LinkedHashSet<BasicGradleProject?>()
    var buildTreePath: String? = null
        private set


    fun setProjectDirectory(projectDirectory: File?): BasicGradleProject {
        this.projectDirectory = projectDirectory
        return this
    }

    override fun setProjectIdentifier(projectIdentifier: DefaultProjectIdentifier?): BasicGradleProject {
        super.setProjectIdentifier(projectIdentifier)
        return this
    }

    override fun setName(name: String?): BasicGradleProject {
        super.setName(name)
        return this
    }

    override fun getChildren(): MutableSet<out BasicGradleProject?> {
        return children
    }

    fun addChild(child: BasicGradleProject?): BasicGradleProject {
        children.add(child)
        return this
    }

    fun setBuildTreePath(path: String?): BasicGradleProject {
        buildTreePath = path
        return this
    }
}
