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
import org.gradle.nativeplatform.PrebuiltStaticLibraryBinary
import org.gradle.nativeplatform.platform.NativePlatform
import java.io.File

class DefaultPrebuiltStaticLibraryBinary(
    name: String?,
    library: PrebuiltLibrary?,
    buildType: BuildType?,
    targetPlatform: NativePlatform?,
    flavor: Flavor?,
    fileCollectionFactory: FileCollectionFactory?
) : AbstractPrebuiltLibraryBinary(name, library, buildType, targetPlatform, flavor, fileCollectionFactory), PrebuiltStaticLibraryBinary {
    private var staticLibraryFile: File? = null

    override fun getDisplayName(): String {
        return "prebuilt static library '" + getComponent().getName() + ":" + getName() + "'"
    }

    override fun setStaticLibraryFile(staticLibraryFile: File?) {
        this.staticLibraryFile = staticLibraryFile
    }

    override fun getStaticLibraryFile(): File? {
        return staticLibraryFile
    }

    override fun getLinkFiles(): FileCollection? {
        return createFileCollection(getStaticLibraryFile(), "link files", "Static library file")
    }

    override fun getRuntimeFiles(): FileCollection {
        return FileCollectionFactory.empty("runtime files for " + getDisplayName())
    }
}
