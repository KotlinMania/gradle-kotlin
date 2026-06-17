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
package org.gradle.plugins.ide.internal.configurer

import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateLookup
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import java.util.Map
import java.util.function.Function
import java.util.stream.Collectors

class EclipseModelAwareUniqueProjectNameProvider(projectStateLookup: ProjectStateLookup) : AbstractUniqueProjectNameProvider(projectStateLookup) {
    private var deduplicated: MutableMap<ProjectIdentity, String>? = null
    private var reservedNames = mutableListOf<ProjectStateWrapper>()
    private var projectToInformationMap = mutableMapOf<ProjectIdentity, ProjectStateWrapper>()

    @Synchronized
    fun setReservedProjectNames(reservedNames: MutableList<String>) {
        this.reservedNames = reservedNames.stream().map<ProjectStateWrapper> { name: String? -> ProjectStateWrapper(name!!) }.collect(Collectors.toList())
        deduplicated = null
    }

    override fun getUniqueName(projectIdentity: ProjectIdentity): String {
        val uniqueName = this.deduplicatedNames.get(projectIdentity)
        if (uniqueName != null) {
            return uniqueName
        }

        // ProjectStateWrapper might contain the configured eclipse project name
        val information = projectToInformationMap.get(projectIdentity)
        if (information != null) {
            return information.name
        }
        return projectIdentity.getProjectName()
    }

    @get:Synchronized
    private val deduplicatedNames: MutableMap<ProjectIdentity, String>
        get() {
            if (deduplicated == null) {
                projectToInformationMap =
                    HashMap<ProjectIdentity, ProjectStateWrapper>()
                for (state in projectStateLookup.getAllProjects()) {
                    val projectNameForEclipse: String = getName(state)
                    projectToInformationMap.put(
                        state.getIdentity(),
                        ProjectStateWrapper(projectNameForEclipse, state)
                    )
                }

                val deduplicator =
                    HierarchicalElementDeduplicator<ProjectStateWrapper>(
                        EclipseModelAwareUniqueProjectNameProvider.ProjectPathDeduplicationAdapter(projectToInformationMap)
                    )
                val allElements: MutableList<ProjectStateWrapper> =
                    ArrayList<ProjectStateWrapper>()
                allElements.addAll(reservedNames)
                allElements.addAll(projectToInformationMap.values)

                this.deduplicated = deduplicator.deduplicate(allElements).entries.stream()
                    .collect(
                        Collectors.toMap(
                            Function { e: MutableMap.MutableEntry<ProjectStateWrapper?, String?>? -> e!!.key.project!!.getIdentity() },
                            Function { Map.Entry.value })
                    )
            }
            return deduplicated
        }

    private class ProjectStateWrapper @JvmOverloads constructor(private val name: String, private val project: ProjectState? = null)

    private inner class ProjectPathDeduplicationAdapter(private val projectToInformationMap: MutableMap<ProjectIdentity, ProjectStateWrapper>) : HierarchicalElementAdapter<ProjectStateWrapper> {
        override fun getName(element: ProjectStateWrapper): String {
            return element.name
        }

        override fun getIdentityName(element: ProjectStateWrapper): String {
            return element.name
        }

        override fun getParent(element: ProjectStateWrapper): ProjectStateWrapper? {
            if (element.project == null) {
                return null
            }

            val parentInBuildTree = findParentInBuildTree(element.project.getIdentity())
            return if (parentInBuildTree == null) null else projectToInformationMap.get(parentInBuildTree)
        }
    }

    companion object {
        private fun getName(state: ProjectState): String {
            // try to get the name from EclipseProject.name
            state.getOwner().ensureProjectsConfigured()

            val modelName: String? = state.fromMutableState<String>(Function { project: ProjectInternal? ->
                val model = project!!.getExtensions().findByType<EclipseModel>(EclipseModel::class.java)
                if (model != null) {
                    return@fromMutableState model.getProject().getName()
                }
                null
            })
            if (modelName != null) {
                return modelName
            }

            // fallback: take the name from the ProjectState
            return state.getName()
        }
    }
}
