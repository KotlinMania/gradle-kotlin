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
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.collections.DomainObjectCollectionFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.PrebuiltLibrary

class DefaultPrebuiltLibrary(private val name: String, objectFactory: ObjectFactory, domainObjectCollectionFactory: DomainObjectCollectionFactory) : PrebuiltLibrary {
    private val headers: SourceDirectorySet
    private val binaries: DomainObjectSet<NativeLibraryBinary?>

    init {
        headers = objectFactory.sourceDirectorySet("headers", "headers for prebuilt library '" + name + "'")
        binaries = domainObjectCollectionFactory.newDomainObjectSet<NativeLibraryBinary?>(NativeLibraryBinary::class.java)
    }

    override fun toString(): String {
        return "prebuilt library '" + name + "'"
    }

    override fun getName(): String {
        return name
    }

    override fun getHeaders(): SourceDirectorySet {
        return headers
    }

    override fun getBinaries(): DomainObjectSet<NativeLibraryBinary?> {
        return binaries
    }
}
