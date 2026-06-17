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

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.Path
import javax.annotation.concurrent.ThreadSafe

/**
 * A build tree scoped service that collects information on the local "publications" of each
 * project within a build tree. A "publication" here means some buildable thing that the project
 * produces that can be consumed outside of the project.
 *
 * The information is gathered from multiple sources (`publishing.publications` container, etc.).
 */
@ServiceScope(Scope.BuildTree::class)
@ThreadSafe
interface ProjectPublicationRegistry {
    /**
     * Register the given publication as produced by the project with the given ID.
     */
    fun registerPublication(projectIdentity: ProjectIdentity, publication: ProjectPublication)

    /**
     * Returns the known publications for the given project.
     */
    fun <T : ProjectPublication?> getPublicationsForProject(type: Class<T?>, projectIdentityPath: Path): MutableCollection<T?>?

    /**
     * Returns all known publications for the given build.
     */
    fun <T : ProjectPublication?> getPublicationsForBuild(type: Class<T?>, buildIdentity: BuildIdentifier): MutableCollection<PublicationForProject<T?>>?

    interface PublicationForProject<T : ProjectPublication?> {
        /**
         * The publication produced by the project.
         */
        @JvmField
        val publication: T?

        /**
         * The ID of the project that produced the publication.
         */
        @JvmField
        val producingProjectId: ProjectIdentity?
    }
}
