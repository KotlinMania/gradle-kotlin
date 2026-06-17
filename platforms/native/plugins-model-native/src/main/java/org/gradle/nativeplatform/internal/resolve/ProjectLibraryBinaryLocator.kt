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
package org.gradle.nativeplatform.internal.resolve

import org.gradle.api.DomainObjectSet
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.internal.resolve.ProjectModelResolver
import org.gradle.nativeplatform.NativeBinarySpec
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.platform.base.ComponentSpecContainer

class ProjectLibraryBinaryLocator(private val projectModelResolver: ProjectModelResolver, private val domainObjectCollectionFactory: DomainObjectCollectionFactory) : LibraryBinaryLocator {
    // Converts the binaries of a project library into regular binary instances
    override fun getBinaries(libraryIdentifier: LibraryIdentifier): DomainObjectSet<NativeLibraryBinary?>? {
        val projectModel = projectModelResolver.resolveProjectModel(libraryIdentifier.getProjectPath())
        val components = projectModel!!.find<ComponentSpecContainer?>("components", ComponentSpecContainer::class.java)
        if (components == null) {
            return null
        }
        val libraryName = libraryIdentifier.getLibraryName()
        val library = components.withType<NativeLibrarySpec?>(NativeLibrarySpec::class.java).get(libraryName)
        if (library == null) {
            return null
        }
        val projectBinaries = library.getBinaries().withType<NativeBinarySpec?>(NativeBinarySpec::class.java)
        val binaries = domainObjectCollectionFactory.newDomainObjectSet<NativeLibraryBinary?>(NativeLibraryBinary::class.java)
        for (nativeBinarySpec in projectBinaries.values()) {
            binaries.add(nativeBinarySpec as NativeLibraryBinary?)
        }
        return binaries
    }
}
