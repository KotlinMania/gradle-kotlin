/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.integtests.tooling.r56

import org.gradle.tooling.*
import org.gradle.tooling.model.*
import org.gradle.tooling.model.build.*
import org.gradle.tooling.model.eclipse.*
import org.gradle.tooling.model.gradle.*
import org.gradle.tooling.model.idea.*
import org.gradle.tooling.model.kotlin.dsl.*

import org.gradle.tooling.model.eclipse.EclipseWorkspace
import java.io.File
import org.gradle.integtests.tooling.r48.*
import java.io.Serializable

class DefaultEclipseWorkspace(override val location: File?, workspaceProjects: MutableList<EclipseWorkspaceProject?>?) : EclipseWorkspace, Serializable {
    private val workspaceProjects: MutableList<EclipseWorkspaceProject?>?

    init {
        this.workspaceProjects = workspaceProjects
    }

    override val projects: MutableList<EclipseWorkspaceProject?>?
        get() = workspaceProjects
}
