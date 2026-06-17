/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativeplatform.internal.prebuilt

import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.internal.resolve.ProjectModelResolver
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.PrebuiltLibraries
import org.gradle.nativeplatform.PrebuiltLibrary
import org.gradle.nativeplatform.Repositories
import org.gradle.nativeplatform.internal.resolve.LibraryBinaryLocator
import org.gradle.nativeplatform.internal.resolve.LibraryIdentifier

class PrebuiltLibraryBinaryLocator(private val projectModelResolver: ProjectModelResolver) : LibraryBinaryLocator {
    override fun getBinaries(library: LibraryIdentifier): DomainObjectSet<NativeLibraryBinary?>? {
        val projectModel = projectModelResolver.resolveProjectModel(library.getProjectPath())
        val repositories = projectModel!!.find<Repositories?>("repositories", Repositories::class.java)
        if (repositories == null) {
            return null
        }
        val prebuiltLibrary = getPrebuiltLibrary(repositories.withType<PrebuiltLibraries?>(PrebuiltLibraries::class.java), library.getLibraryName())
        return if (prebuiltLibrary != null) prebuiltLibrary.getBinaries() else null
    }

    private fun getPrebuiltLibrary(repositories: NamedDomainObjectSet<PrebuiltLibraries>, libraryName: String?): PrebuiltLibrary? {
        for (prebuiltLibraries in repositories) {
            val prebuiltLibrary = prebuiltLibraries.resolveLibrary(libraryName)
            if (prebuiltLibrary != null) {
                return prebuiltLibrary
            }
        }
        return null
    }
}
