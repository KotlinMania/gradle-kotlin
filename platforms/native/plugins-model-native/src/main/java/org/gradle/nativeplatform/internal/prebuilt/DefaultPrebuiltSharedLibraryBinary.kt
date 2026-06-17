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

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.Flavor
import org.gradle.nativeplatform.PrebuiltLibrary
import org.gradle.nativeplatform.PrebuiltSharedLibraryBinary
import org.gradle.nativeplatform.platform.NativePlatform
import java.io.File

class DefaultPrebuiltSharedLibraryBinary(
    name: String?,
    library: PrebuiltLibrary?,
    buildType: BuildType?,
    targetPlatform: NativePlatform?,
    flavor: Flavor?,
    fileCollectionFactory: FileCollectionFactory?
) : AbstractPrebuiltLibraryBinary(name, library, buildType, targetPlatform, flavor, fileCollectionFactory), PrebuiltSharedLibraryBinary {
    private var sharedLibraryFile: File? = null
    private var sharedLibraryLinkFile: File? = null

    override fun getDisplayName(): String {
        return "prebuilt shared library '" + getComponent().getName() + ":" + getName() + "'"
    }

    override fun setSharedLibraryFile(sharedLibraryFile: File?) {
        this.sharedLibraryFile = sharedLibraryFile
    }

    override fun getSharedLibraryFile(): File? {
        return sharedLibraryFile
    }

    override fun setSharedLibraryLinkFile(sharedLibraryLinkFile: File?) {
        this.sharedLibraryLinkFile = sharedLibraryLinkFile
    }

    override fun getSharedLibraryLinkFile(): File? {
        if (sharedLibraryLinkFile != null) {
            return sharedLibraryLinkFile
        }
        return sharedLibraryFile
    }

    override fun getLinkFiles(): FileCollection? {
        return createFileCollection(getSharedLibraryLinkFile(), "link files", "Shared library link files")
    }

    override fun getRuntimeFiles(): FileCollection? {
        return createFileCollection(getSharedLibraryFile(), "runtime files", "Shared library runtime files")
    }
}
