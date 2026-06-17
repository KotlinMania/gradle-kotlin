/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.tasks

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.CompositeFileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.logging.text.TreeFormatter
import java.io.File
import java.util.concurrent.Callable
import java.util.function.Consumer
import javax.inject.Inject

abstract class DefaultSourceSetOutput @Inject constructor(
    sourceSetDisplayName: String?,
    taskDependencyFactory: TaskDependencyFactory,
    private val fileResolver: FileResolver,
    fileCollectionFactory: FileCollectionFactory
) : CompositeFileCollection(taskDependencyFactory), SourceSetOutput {
    private val outputDirectories: ConfigurableFileCollection
    private var resourcesDir: Any? = null

    private val classesDirs: ConfigurableFileCollection
    private val dirs: ConfigurableFileCollection
    private val generatedSourcesDirs: ConfigurableFileCollection

    var resourcesContribution: DirectoryContribution? = null
        private set

    init {
        this.classesDirs = fileCollectionFactory.configurableFiles(sourceSetDisplayName + " classesDirs")

        this.outputDirectories = fileCollectionFactory.configurableFiles(sourceSetDisplayName + " classes")
        outputDirectories.from(classesDirs, Callable { this.getResourcesDir() })

        this.dirs = fileCollectionFactory.configurableFiles(sourceSetDisplayName + " dirs")

        this.generatedSourcesDirs = fileCollectionFactory.configurableFiles(sourceSetDisplayName + " generatedSourcesDirs")
    }

    override fun visitChildren(visitor: Consumer<FileCollectionInternal?>) {
        visitor.accept(outputDirectories as FileCollectionInternal?)
    }

    override fun getDisplayName(): String {
        return outputDirectories.toString()
    }

    override fun appendContents(formatter: TreeFormatter) {
        formatter.node("source set: " + outputDirectories.toString())
        formatter.node("output directories")
        formatter.startChildren()
        (outputDirectories as FileCollectionInternal).describeContents(formatter)
        formatter.endChildren()
    }

    override fun getClassesDirs(): ConfigurableFileCollection {
        return classesDirs
    }

    /**
     * Set the task contributor to the provided resources directory. The provided resources
     * directory provider should resolve to the same directory set by [.setResourcesDir].
     *
     * @param directory The resources directory provider.
     * @param task The task which generates `directory`.
     */
    fun setResourcesContributor(directory: Provider<File?>?, task: TaskProvider<*>?) {
        this.resourcesContribution = DirectoryContribution(directory, task)
    }

    override fun getResourcesDir(): File? {
        if (resourcesDir == null) {
            return null
        }
        return fileResolver.resolve(resourcesDir)
    }

    override fun setResourcesDir(resourcesDir: File?) {
        this.resourcesDir = resourcesDir
    }

    override fun setResourcesDir(resourcesDir: Any?) {
        this.resourcesDir = resourcesDir
    }

    fun builtBy(vararg taskPaths: Any?) {
        outputDirectories.builtBy(*taskPaths)
    }

    override fun dir(dir: Any) {
        this.dir(mutableMapOf<String?, Any?>(), dir)
    }

    override fun dir(options: MutableMap<String?, Any?>, dir: Any) {
        this.dirs.from(dir)
        this.outputDirectories.from(dir)

        val builtBy = options.get("builtBy")
        if (builtBy != null) {
            this.builtBy(builtBy)
            this.dirs.builtBy(builtBy)
        }
    }

    override fun getDirs(): FileCollection {
        return dirs
    }

    override fun getGeneratedSourcesDirs(): ConfigurableFileCollection {
        return generatedSourcesDirs
    }

    /**
     * A mapping from a directory to the task which provides that directory.
     */
    class DirectoryContribution(val directory: Provider<File?>?, val task: TaskProvider<*>?)
}
