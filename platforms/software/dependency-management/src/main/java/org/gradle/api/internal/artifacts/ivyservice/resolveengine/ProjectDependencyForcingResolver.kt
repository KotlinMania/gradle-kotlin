/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine

import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultConflictResolverDetails
import org.gradle.internal.Cast.uncheckedCast

class ProjectDependencyForcingResolver<T : ComponentResolutionState?>(private val delegate: ModuleConflictResolver<T?>) : ModuleConflictResolver<T?> {
    override fun select(details: ConflictResolverDetails<T?>) {
        // the collection will only be initialized if more than one project candidate is found
        var projectCandidates: MutableCollection<T?>? = null
        var foundProjectCandidate: T? = null
        // fine one or more project dependencies among conflicting modules
        for (candidate in details.getCandidates()) {
            if (candidate!!.getComponentId() is ProjectComponentIdentifier) {
                if (foundProjectCandidate == null) {
                    // found the first project dependency
                    foundProjectCandidate = candidate
                } else {
                    // found more than one
                    if (projectCandidates == null) {
                        projectCandidates = ArrayList<T?>()
                        projectCandidates.add(foundProjectCandidate)
                    }
                    projectCandidates.add(candidate)
                }
            }
        }
        // if more than one conflicting project dependencies
        // let the delegate resolver select among them
        if (projectCandidates != null) {
            val projectDetails: ConflictResolverDetails<T?> = DefaultConflictResolverDetails<T?>(uncheckedCast<MutableList<T?>?>(projectCandidates))
            delegate.select(projectDetails)
            details.select(projectDetails.getSelected())
            return
        }
        // if found only one project dependency - return it, otherwise call the next resolver
        if (foundProjectCandidate != null) {
            details.select(foundProjectCandidate)
        } else {
            delegate.select(details)
        }
    }
}
