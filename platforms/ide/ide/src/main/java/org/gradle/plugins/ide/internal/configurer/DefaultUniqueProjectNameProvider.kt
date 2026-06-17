/*
 * Copyright 2017 the original author or authors.
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
import java.util.stream.Collectors

class DefaultUniqueProjectNameProvider(projectStateLookup: ProjectStateLookup) : AbstractUniqueProjectNameProvider(projectStateLookup) {
    private var deduplicated: MutableMap<ProjectIdentity, String>? = null

    override fun getUniqueName(projectIdentity: ProjectIdentity): String {
        val uniqueName = this.deduplicatedNames.get(projectIdentity)
        return if (uniqueName != null) uniqueName else projectIdentity.getProjectName()
    }

    @get:Synchronized
    private val deduplicatedNames: MutableMap<ProjectIdentity, String>
        get() {
            if (deduplicated == null) {
                val deduplicator =
                    HierarchicalElementDeduplicator<ProjectIdentity>(DefaultUniqueProjectNameProvider.ProjectPathDeduplicationAdapter())
                val allProjects = projectStateLookup.getAllProjects().stream()
                    .map<ProjectIdentity> { obj: ProjectState? -> obj!!.getIdentity() }
                    .collect(Collectors.toList())
                this.deduplicated = deduplicator.deduplicate(allProjects)
            }
            return deduplicated
        }

    private inner class ProjectPathDeduplicationAdapter : HierarchicalElementAdapter<ProjectIdentity> {
        override fun getName(element: ProjectIdentity): String {
            return element.getProjectName()
        }

        override fun getIdentityName(element: ProjectIdentity): String {
            val identityName = element.getBuildTreePath().getName()
            return if (identityName != null) identityName else element.getProjectName()
        }

        override fun getParent(element: ProjectIdentity): ProjectIdentity? {
            return findParentInBuildTree(element)
        }
    }
}
