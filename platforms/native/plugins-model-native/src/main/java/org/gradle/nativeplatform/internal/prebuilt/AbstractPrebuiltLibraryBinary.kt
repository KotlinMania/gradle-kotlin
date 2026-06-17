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
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.Flavor
import org.gradle.nativeplatform.NativeLibraryBinary
import org.gradle.nativeplatform.PrebuiltLibrary
import org.gradle.nativeplatform.platform.NativePlatform
import java.io.File

abstract class AbstractPrebuiltLibraryBinary(
    val name: String?,
    val component: PrebuiltLibrary,
    private val buildType: BuildType?,
    private val targetPlatform: NativePlatform?,
    private val flavor: Flavor?,
    protected val fileCollectionFactory: FileCollectionFactory
) : NativeLibraryBinary {
    override fun toString(): String {
        return getDisplayName()
    }

    override fun getBuildType(): BuildType? {
        return buildType
    }

    override fun getFlavor(): Flavor? {
        return flavor
    }

    override fun getTargetPlatform(): NativePlatform? {
        return targetPlatform
    }

    override fun getHeaderDirs(): FileCollection {
        return component.getHeaders().getSourceDirectories()
    }

    protected fun createFileCollection(file: File, fileCollectionDisplayName: String?, fileDescription: String?): FileCollection {
        return fileCollectionFactory.create(AbstractPrebuiltLibraryBinary.ValidatingFileSet(file, fileCollectionDisplayName, fileDescription))
    }

    private inner class ValidatingFileSet(private val file: File, private val fileCollectionDisplayName: String?, private val fileDescription: String?) : MinimalFileSet {
        override fun getDisplayName(): String {
            return fileCollectionDisplayName + " for " + this@AbstractPrebuiltLibraryBinary.getDisplayName()
        }

        override fun getFiles(): MutableSet<File?> {
            if (file == null) {
                throw PrebuiltLibraryResolveException(String.format("%s not set for %s.", fileDescription, this@AbstractPrebuiltLibraryBinary.getDisplayName()))
            }
            if (!file.exists() || !file.isFile()) {
                throw PrebuiltLibraryResolveException(String.format("%s %s does not exist for %s.", fileDescription, file.getAbsolutePath(), this@AbstractPrebuiltLibraryBinary.getDisplayName()))
            }
            return mutableSetOf<File?>(file)
        }
    }
}
