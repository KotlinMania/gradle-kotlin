/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.testfixtures.internal

import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.internal.project.ImmutableProjectDescriptor
import java.io.File
import java.util.Collections

class ProjectBuilderProjectDescriptor(
    private val identity: ProjectIdentity,
    private val projectDir: File,
    private val buildFile: File,
    private val parent: ProjectIdentity?
) : ImmutableProjectDescriptor {
    private val children: MutableList<ProjectIdentity> = ArrayList<ProjectIdentity>()

    /**
     * Allows late mutation of the children list.
     *
     *
     * This is only required for `ProjectBuilder`, because it allows
     * creating children after the parent project has been created.
     * In a normal build, all project descriptors are created at the same time.
     */
    fun addChild(child: ProjectIdentity) {
        children.add(child)
    }

    override fun getIdentity(): ProjectIdentity {
        return identity
    }

    override fun getProjectDir(): File {
        return projectDir
    }

    override fun getBuildFile(): File {
        return buildFile
    }

    override fun getParent(): ProjectIdentity? {
        return parent
    }

    override fun getChildren(): MutableList<ProjectIdentity> {
        return Collections.unmodifiableList<ProjectIdentity>(children)
    }

    override fun toString(): String {
        return identity.getProjectPath().toString()
    }
}
