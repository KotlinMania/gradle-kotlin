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
package org.gradle.api.publish.internal.validation

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.net.URI

@ServiceScope(Scope.Build::class)
class DuplicatePublicationTracker {
    private val published: Multimap<String?, PublicationWithProject> = LinkedHashMultimap.create<String?, PublicationWithProject?>()

    @Synchronized
    fun checkCanPublish(projectDisplayName: String?, publicationName: String?, projectIdentity: ModuleVersionIdentifier?, repositoryLocation: URI?, repositoryName: String?) {
        // Don't track publications to repositories configured without a base URL
        if (repositoryLocation == null) {
            return
        }

        val repositoryKey = normalizeLocation(repositoryLocation)

        val publicationWithProject = PublicationWithProject(projectDisplayName, publicationName, projectIdentity)
        if (published.get(repositoryKey).contains(publicationWithProject)) {
            LOG!!.warn("Publication '" + projectIdentity + "' is published multiple times to the same location. It is likely that repository '" + repositoryName + "' is duplicated.")
            return
        }

        for (previousPublicationWithProject in published.get(repositoryKey)) {
            if (previousPublicationWithProject.getCoordinates() == projectIdentity) {
                var firstPublication = previousPublicationWithProject.toString()
                var secondPublication = publicationWithProject.toString()
                if (secondPublication.compareTo(firstPublication) < 0) {
                    val temp = firstPublication
                    firstPublication = secondPublication
                    secondPublication = temp
                }
                LOG!!.warn("Multiple publications with coordinates '" + projectIdentity + "' are published to repository '" + repositoryName + "'. The publications " + firstPublication + " and " + secondPublication + " will overwrite each other!")
            }
        }

        published.put(repositoryKey, publicationWithProject)
    }

    private fun normalizeLocation(location: URI): String {
        val repoUrl = location.toString()
        if (!repoUrl.endsWith("/")) {
            return repoUrl + "/"
        }
        return repoUrl
    }

    companion object {
        private val LOG = getLogger(DuplicatePublicationTracker::class.java)
    }
}
