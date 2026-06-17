/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.internal.artifacts

import org.gradle.api.internal.project.ProjectInternal

/**
 * Exposes the dependency management identity of a project.
 *
 * TODO: Once any mutable field on this class is accessed, we should consider that as the project being observed.
 * Just like we do with configurations, we should then prohibit any changes to the project that would affect the identity.
 */
class ProjectBackedModule(project: ProjectInternal) : Module {
    private val project: ProjectInternal?

    init {
        this.project = project
    }

    override fun getGroup(): String {
        return project!!.getGroup().toString()
    }

    override fun getName(): String {
        return project!!.getName()
    }

    override fun getVersion(): String {
        return project!!.getVersion().toString()
    }

    override fun getStatus(): String {
        return project!!.getStatus().toString()
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as ProjectBackedModule

        if (if (project != null) (project != that.project) else that.project != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        return if (project != null) project.hashCode() else 0
    }
}
