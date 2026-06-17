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
package org.gradle.plugins.ide.internal.configurer

import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateLookup

abstract class AbstractUniqueProjectNameProvider protected constructor(protected val projectStateLookup: ProjectStateLookup) : UniqueProjectNameProvider {
    /**
     * Finds the "parent" project based on the build-tree path of the current project.
     *
     *
     * This is different from looking up the [parent project][ProjectState.getParent] inside a given build,
     * because a root project of a build does not have a parent. In the context of project hiearachy shown in the IDE, however,
     * we are looking for the "parent" project based on the build-tree path.
     * This means that the **"parent" project might belong to a different build.**
     */
    protected fun findParentInBuildTree(projectIdentity: ProjectIdentity): ProjectIdentity? {
        val parentInBuildTreePath = projectIdentity.getBuildTreePath().getParent()
        if (parentInBuildTreePath == null) {
            return null
        }
        val parentInBuildTree = projectStateLookup.findProject(parentInBuildTreePath)
        if (parentInBuildTree == null) {
            return null
        }
        return parentInBuildTree.getIdentity()
    }
}
