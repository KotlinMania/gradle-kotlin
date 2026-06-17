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
import org.gradle.language.nativeplatform.NativeResourceSet
import org.gradle.nativeplatform.SharedLibraryBinary
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.nativeplatform.tasks.ObjectFilesToBinary
import org.gradle.platform.base.BinaryTasksCollection
import org.gradle.platform.base.internal.BinaryTasksCollectionWrapper
import java.io.File

class DefaultSharedLibraryBinarySpec : AbstractNativeLibraryBinarySpec(), SharedLibraryBinary, SharedLibraryBinarySpecInternal {
    private val tasks = DefaultTasksCollection(super.getTasks())
    private var sharedLibraryFile: File? = null
    private var sharedLibraryLinkFile: File? = null

    override fun getSharedLibraryFile(): File? {
        return sharedLibraryFile
    }

    override fun setSharedLibraryFile(sharedLibraryFile: File?) {
        this.sharedLibraryFile = sharedLibraryFile
    }

    override fun getSharedLibraryLinkFile(): File? {
        return sharedLibraryLinkFile
    }

    override fun setSharedLibraryLinkFile(sharedLibraryLinkFile: File?) {
        this.sharedLibraryLinkFile = sharedLibraryLinkFile
    }

    override fun getPrimaryOutput(): File? {
        return getSharedLibraryFile()
    }

    override fun getLinkFiles(): FileCollection {
        return getFileCollectionFactory().create(DefaultSharedLibraryBinarySpec.SharedLibraryLinkOutputs())
    }

    override fun getRuntimeFiles(): FileCollection {
        return getFileCollectionFactory().create(DefaultSharedLibraryBinarySpec.SharedLibraryRuntimeOutputs())
    }

    override fun getCreateOrLink(): ObjectFilesToBinary? {
        return tasks.getLink()
    }

    override fun getTasks(): SharedLibraryBinarySpec.TasksCollection {
        return tasks
    }

    private class DefaultTasksCollection(delegate: BinaryTasksCollection?) : BinaryTasksCollectionWrapper(delegate), SharedLibraryBinarySpec.TasksCollection {
        override fun getLink(): LinkSharedLibrary? {
            return findSingleTaskWithType<LinkSharedLibrary?>(LinkSharedLibrary::class.java)
        }
    }

    private inner class SharedLibraryLinkOutputs : LibraryOutputs() {
        override fun getDisplayName(): String {
            return "Link files for " + this@DefaultSharedLibraryBinarySpec.getDisplayName()
        }

        override fun hasOutputs(): Boolean {
            return hasSources() && !this.isResourceOnly
        }

        override fun getOutputs(): MutableSet<File?> {
            return mutableSetOf<File?>(getSharedLibraryLinkFile())
        }

        val isResourceOnly: Boolean
            get() = hasResources() && !hasExportedSymbols()

        fun hasResources(): Boolean {
            for (windowsResourceSet in getInputs().withType<NativeResourceSet?>(NativeResourceSet::class.java)) {
                if (!windowsResourceSet!!.getSource().isEmpty()) {
                    return true
                }
            }
            return false
        }

        fun hasExportedSymbols(): Boolean {
            for (languageSourceSet in getInputs()) {
                if (languageSourceSet !is NativeResourceSet) {
                    if (!languageSourceSet.getSource().isEmpty()) {
                        return true
                    }
                }
            }
            return false
        }
    }

    private inner class SharedLibraryRuntimeOutputs : LibraryOutputs() {
        override fun getDisplayName(): String {
            return "Runtime files for " + this@DefaultSharedLibraryBinarySpec.getDisplayName()
        }

        override fun hasOutputs(): Boolean {
            return hasSources()
        }

        override fun getOutputs(): MutableSet<File?> {
            return mutableSetOf<File?>(getSharedLibraryFile())
        }
    }
}
