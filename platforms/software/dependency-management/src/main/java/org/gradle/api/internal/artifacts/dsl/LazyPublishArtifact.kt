/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.Task
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.artifacts.PublishArtifactInternal
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import java.io.File
import java.util.Date
import java.util.function.Consumer

class LazyPublishArtifact(provider: Provider<*>, private val version: String?, private val fileResolver: FileResolver, private val taskDependencyFactory: TaskDependencyFactory) :
    PublishArtifactInternal {
    private val provider: ProviderInternal<*>
    private var delegate: PublishArtifactInternal? = null
        get() {
            if (field == null) {
                val value: Any = provider.get()
                if (value is FileSystemLocation) {
                    val location = value
                    field = fromFile(location.getAsFile())
                } else if (value is File) {
                    field = fromFile((value as java.io.File?)!!)
                } else if (value is AbstractArchiveTask) {
                    field = ArchivePublishArtifact(taskDependencyFactory, value as AbstractArchiveTask?)
                } else if (value is Task) {
                    field = fromFile(value.getOutputs().getFiles().getSingleFile())
                } else {
                    field = fromFile(fileResolver.resolve(value))
                }
            }
            return field
        }

    constructor(archiveTask: Provider<*>, fileResolver: FileResolver, taskDependencyFactory: TaskDependencyFactory) : this(archiveTask, null, fileResolver, taskDependencyFactory)

    init {
        this.provider = Providers.internal(provider)
    }

    override fun getName(): String {
        return this.delegate!!.getName()
    }

    override fun getExtension(): String {
        return this.delegate!!.getExtension()
    }

    override fun getType(): String {
        return this.delegate!!.getType()
    }

    override fun getClassifier(): String {
        return this.delegate!!.getClassifier()!!
    }

    override fun getFile(): File {
        return this.delegate!!.getFile()
    }

    override fun getDate(): Date {
        return Date()
    }

    private fun fromFile(file: File): PublishArtifactInternal {
        val artifactFile = ArtifactFile(file, version)
        return DefaultPublishArtifact(taskDependencyFactory, artifactFile.getName(), artifactFile.getExtension(), artifactFile.getExtension(), artifactFile.getClassifier(), null, file)
    }

    override fun getBuildDependencies(): TaskDependency {
        return taskDependencyFactory.visitingDependencies(Consumer { context: TaskDependencyResolveContext? -> context!!.add(provider) })
    }

    override fun shouldBePublished(): Boolean {
        return this.delegate!!.shouldBePublished()
    }
}
