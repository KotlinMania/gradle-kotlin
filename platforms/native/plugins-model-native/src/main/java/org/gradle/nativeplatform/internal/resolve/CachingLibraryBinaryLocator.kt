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
package org.gradle.nativeplatform.internal.resolve

import org.gradle.api.DomainObjectSet
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.nativeplatform.NativeLibraryBinary

class CachingLibraryBinaryLocator(private val delegate: LibraryBinaryLocator, domainObjectCollectionFactory: DomainObjectCollectionFactory) : LibraryBinaryLocator {
    private val libraries: MutableMap<LibraryIdentifier?, DomainObjectSet<NativeLibraryBinary?>?> = HashMap<LibraryIdentifier?, DomainObjectSet<NativeLibraryBinary?>?>()

    init {
        if (nullResult == null) {
            nullResult = domainObjectCollectionFactory.newDomainObjectSet<NativeLibraryBinary?>(NativeLibraryBinary::class.java)
        }
    }

    override fun getBinaries(library: LibraryIdentifier?): DomainObjectSet<NativeLibraryBinary?>? {
        var libraryBinaries: DomainObjectSet<NativeLibraryBinary?>? = libraries.get(library)
        if (libraryBinaries == null) {
            libraryBinaries = delegate.getBinaries(library)
            if (libraryBinaries == null) {
                libraryBinaries = nullResult
            }
            libraries.put(library, libraryBinaries)
        }
        return if (libraryBinaries === nullResult) null else libraryBinaries
    }

    companion object {
        private var nullResult: DomainObjectSet<NativeLibraryBinary?>? = null
    }
}
