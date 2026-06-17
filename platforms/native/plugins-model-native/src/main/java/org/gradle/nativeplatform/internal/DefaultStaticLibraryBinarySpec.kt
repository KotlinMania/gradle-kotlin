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
package org.gradle.nativeplatform.internal

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.nativeplatform.StaticLibraryBinary
import org.gradle.nativeplatform.StaticLibraryBinarySpec
import org.gradle.nativeplatform.tasks.CreateStaticLibrary
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary
import org.gradle.platform.base.BinaryTasksCollection
import org.gradle.platform.base.internal.BinaryTasksCollectionWrapper
import java.io.File

class DefaultStaticLibraryBinarySpec : AbstractNativeLibraryBinarySpec(), StaticLibraryBinary, StaticLibraryBinarySpecInternal {
    private val additionalLinkFiles: MutableList<FileCollection?> = ArrayList<FileCollection?>()
    private val tasks = DefaultTasksCollection(super.getTasks())
    private var staticLibraryFile: File? = null

    override fun getStaticLibraryFile(): File? {
        return staticLibraryFile
    }

    override fun setStaticLibraryFile(staticLibraryFile: File?) {
        this.staticLibraryFile = staticLibraryFile
    }

    override fun getPrimaryOutput(): File? {
        return getStaticLibraryFile()
    }

    override fun additionalLinkFiles(files: FileCollection?) {
        this.additionalLinkFiles.add(files)
    }

    override fun getLinkFiles(): FileCollection {
        val result = getFileCollectionFactory().configurableFiles("Link files for " + getDisplayName())
        result.from(getFileCollectionFactory().create(DefaultStaticLibraryBinarySpec.StaticLibraryLinkOutputs()))
        result.from(additionalLinkFiles)
        return result
    }

    override fun getRuntimeFiles(): FileCollection {
        return FileCollectionFactory.empty("Runtime files for " + getDisplayName())
    }

    override fun getCreateOrLink(): ObjectFilesToBinary? {
        return tasks.getCreateStaticLib()
    }

    override fun getTasks(): StaticLibraryBinarySpec.TasksCollection {
        return tasks
    }

    internal class DefaultTasksCollection(delegate: BinaryTasksCollection?) : BinaryTasksCollectionWrapper(delegate), StaticLibraryBinarySpec.TasksCollection {
        override fun getCreateStaticLib(): CreateStaticLibrary? {
            return findSingleTaskWithType<CreateStaticLibrary?>(CreateStaticLibrary::class.java)
        }
    }

    private inner class StaticLibraryLinkOutputs : LibraryOutputs() {
        override fun getDisplayName(): String {
            return "Static library file for " + this@DefaultStaticLibraryBinarySpec.getDisplayName()
        }

        override fun hasOutputs(): Boolean {
            return hasSources()
        }

        override fun getOutputs(): MutableSet<File?> {
            val allFiles: MutableSet<File?> = LinkedHashSet<File?>()
            if (hasSources()) {
                allFiles.add(getStaticLibraryFile())
            }
            return allFiles
        }
    }
}
