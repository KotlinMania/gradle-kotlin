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

import org.gradle.api.Buildable
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.tasks.TaskDependency
import org.gradle.language.base.LanguageSourceSet
import org.gradle.language.nativeplatform.HeaderExportingSourceSet
import org.gradle.nativeplatform.NativeDependencySet
import java.io.File

class SourceSetNativeDependencyResolver(private val delegate: NativeDependencyResolver, private val fileCollectionFactory: FileCollectionFactory) : NativeDependencyResolver {
    override fun resolve(nativeBinaryResolveResult: NativeBinaryResolveResult) {
        for (resolution in nativeBinaryResolveResult.getPendingResolutions()) {
            if (resolution.getInput() is LanguageSourceSet) {
                val input = resolution.getInput() as LanguageSourceSet?
                resolution.setNativeDependencySet(createNativeDependencySet(input))
            }
        }
        delegate.resolve(nativeBinaryResolveResult)
    }

    private fun createNativeDependencySet(sourceSet: LanguageSourceSet?): NativeDependencySet {
        if (sourceSet is HeaderExportingSourceSet) {
            return LanguageSourceSetNativeDependencySet(sourceSet, fileCollectionFactory)
        }
        return EmptyNativeDependencySet.Companion.INSTANCE
    }

    private class EmptyNativeDependencySet : NativeDependencySet {
        override fun getIncludeRoots(): FileCollection {
            return FileCollectionFactory.empty()
        }

        override fun getLinkFiles(): FileCollection {
            return FileCollectionFactory.empty()
        }

        override fun getRuntimeFiles(): FileCollection {
            return FileCollectionFactory.empty()
        }

        companion object {
            private val INSTANCE: NativeDependencySet = EmptyNativeDependencySet()
        }
    }

    private class LanguageSourceSetNativeDependencySet(private val sourceSet: HeaderExportingSourceSet, private val fileCollectionFactory: FileCollectionFactory) : NativeDependencySet {
        override fun getIncludeRoots(): FileCollection {
            return fileCollectionFactory.create(LanguageSourceSetNativeDependencySet.HeaderFileCollection())
        }

        override fun getLinkFiles(): FileCollection {
            return FileCollectionFactory.empty()
        }

        override fun getRuntimeFiles(): FileCollection {
            return FileCollectionFactory.empty()
        }

        private inner class HeaderFileCollection : MinimalFileSet, Buildable {
            override fun getDisplayName(): String {
                return "Include roots of " + sourceSet.getName()
            }

            override fun getFiles(): MutableSet<File?> {
                return sourceSet.exportedHeaders.getSrcDirs()
            }

            override fun getBuildDependencies(): TaskDependency {
                return sourceSet.getBuildDependencies()
            }
        }
    }
}
