/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.SetMultimap
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.project.HoldsProjectState
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.util.Path

class DefaultProjectPublicationRegistry : ProjectPublicationRegistry, HoldsProjectState {
    private val publicationsByProjectId: SetMultimap<Path, ProjectPublication> = LinkedHashMultimap.create<Path, ProjectPublication>()
    private val publicationsByBuildId: SetMultimap<BuildIdentifier, ProjectPublicationRegistry.PublicationForProject<*>> =
        LinkedHashMultimap.create<BuildIdentifier, ProjectPublicationRegistry.PublicationForProject<*>>()

    override fun <T : ProjectPublication?> getPublicationsForProject(type: Class<T?>, projectIdentityPath: Path): MutableCollection<T?> {
        synchronized(publicationsByProjectId) {
            val projectPublications: MutableCollection<ProjectPublication> = publicationsByProjectId.get(projectIdentityPath)
            if (projectPublications.isEmpty()) {
                return mutableListOf<T?>()
            }
            val result: MutableList<T?> = ArrayList<T?>(projectPublications.size)
            for (publication in projectPublications) {
                if (type.isInstance(publication)) {
                    result.add(type.cast(publication))
                }
            }
            return result
        }
    }

    override fun <T : ProjectPublication?> getPublicationsForBuild(type: Class<T?>, buildIdentity: BuildIdentifier): MutableCollection<ProjectPublicationRegistry.PublicationForProject<T?>> {
        synchronized(publicationsByBuildId) {
            val buildPublications: MutableCollection<ProjectPublicationRegistry.PublicationForProject<*>> = publicationsByBuildId.get(buildIdentity)
            if (buildPublications.isEmpty()) {
                return mutableListOf<ProjectPublicationRegistry.PublicationForProject<T?>>()
            }
            val result: MutableList<ProjectPublicationRegistry.PublicationForProject<T?>> = ArrayList<ProjectPublicationRegistry.PublicationForProject<T?>>(buildPublications.size)
            for (reference in buildPublications) {
                if (type.isInstance(reference.getPublication())) {
                    result.add(uncheckedCast<ProjectPublicationRegistry.PublicationForProject<T?>?>(reference)!!)
                }
            }
            return result
        }
    }

    override fun registerPublication(projectIdentity: ProjectIdentity, publication: ProjectPublication) {
        synchronized(publicationsByProjectId) {
            publicationsByProjectId.put(projectIdentity.getBuildTreePath(), publication)
        }
        synchronized(publicationsByBuildId) {
            val publicationReference = DefaultPublicationForProject(publication, projectIdentity)
            publicationsByBuildId.put(DefaultBuildIdentifier(projectIdentity.getBuildPath()), publicationReference)
        }
    }

    override fun discardAll() {
        publicationsByProjectId.clear()
        publicationsByBuildId.clear()
    }

    private class DefaultPublicationForProject(private val publication: ProjectPublication, private val projectId: ProjectIdentity) :
        ProjectPublicationRegistry.PublicationForProject<ProjectPublication?> {
        override fun getPublication(): ProjectPublication {
            return publication
        }

        override fun getProducingProjectId(): ProjectIdentity {
            return projectId
        }
    }
}
