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

import org.gradle.api.Buildable
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.tasks.TaskDependency
import org.gradle.language.nativeplatform.HeaderExportingSourceSet
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.platform.base.LibraryBinarySpec
import java.io.File

abstract class AbstractNativeLibraryBinarySpec : AbstractNativeBinarySpec(), LibraryBinarySpec {
    override fun getComponent(): NativeLibrarySpec? {
        return getComponentAs<NativeLibrarySpec?>(NativeLibrarySpec::class.java)
    }

    override fun getLibrary(): NativeLibrarySpec? {
        return getComponentAs<NativeLibrarySpec?>(NativeLibrarySpec::class.java)
    }

    protected fun hasSources(): Boolean {
        for (sourceSet in getInputs()) {
            if (!sourceSet.getSource().isEmpty()) {
                return true
            }
            if (sourceSet.hasBuildDependencies()) {
                return true
            }
        }
        return false
    }

    val headerDirs: FileCollection
        get() = getFileCollectionFactory().create(AbstractNativeLibraryBinarySpec.HeaderFileSet())

    protected abstract inner class LibraryOutputs : MinimalFileSet, Buildable {
        override fun getFiles(): MutableSet<File?> {
            if (hasOutputs()) {
                return this.outputs
            }
            return mutableSetOf<File?>()
        }

        override fun getBuildDependencies(): TaskDependency {
            if (hasOutputs()) {
                return this@AbstractNativeLibraryBinarySpec.getBuildDependencies()
            }
            return TaskDependencyInternal.EMPTY
        }

        protected abstract fun hasOutputs(): Boolean

        protected abstract val outputs: MutableSet<File?>
    }

    private inner class HeaderFileSet : MinimalFileSet, Buildable {
        override fun getDisplayName(): String {
            return "Headers for " + this@AbstractNativeLibraryBinarySpec.getDisplayName()
        }

        override fun getFiles(): MutableSet<File?> {
            val headerDirs: MutableSet<File?> = LinkedHashSet<File?>()
            for (sourceSet in getInputs().withType<HeaderExportingSourceSet?>(HeaderExportingSourceSet::class.java)) {
                headerDirs.addAll(sourceSet!!.exportedHeaders.getSrcDirs())
            }
            return headerDirs
        }

        override fun getBuildDependencies(): TaskDependency {
            val dependency = DefaultTaskDependency()
            for (sourceSet in getInputs().withType<HeaderExportingSourceSet?>(HeaderExportingSourceSet::class.java)) {
                dependency.add(sourceSet!!.getBuildDependencies())
            }
            return dependency
        }
    }
}
