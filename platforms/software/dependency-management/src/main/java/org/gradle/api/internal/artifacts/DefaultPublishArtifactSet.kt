/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts

import org.gradle.api.Describable
import org.gradle.api.DomainObjectSet
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.DelegatingDomainObjectSet
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.Describables
import java.io.File
import java.util.function.Consumer

class DefaultPublishArtifactSet(
    private val displayName: Describable,
    backingSet: DomainObjectSet<PublishArtifact?>,
    fileCollectionFactory: FileCollectionFactory,
    taskDependencyFactory: TaskDependencyFactory
) : DelegatingDomainObjectSet<PublishArtifact?>(backingSet), PublishArtifactSet {
    private val builtBy: TaskDependencyInternal
    private val files: FileCollection

    constructor(
        displayName: String,
        backingSet: DomainObjectSet<PublishArtifact?>,
        fileCollectionFactory: FileCollectionFactory,
        taskDependencyFactory: TaskDependencyFactory
    ) : this(Describables.of(displayName), backingSet, fileCollectionFactory, taskDependencyFactory)

    init {
        this.builtBy = taskDependencyFactory.visitingDependencies(Consumer { context: TaskDependencyResolveContext? ->
            for (publishArtifact in this@DefaultPublishArtifactSet) {
                context!!.add(publishArtifact)
            }
        })
        this.files = fileCollectionFactory.create(builtBy, DefaultPublishArtifactSet.ArtifactsFileCollection())
    }

    override fun toString(): String {
        return displayName.getDisplayName()
    }

    override fun getFiles(): FileCollection {
        return files
    }

    override fun getBuildDependencies(): TaskDependency {
        return builtBy
    }

    private inner class ArtifactsFileCollection : MinimalFileSet {
        override fun getDisplayName(): String {
            return displayName.getDisplayName()
        }

        override fun getFiles(): MutableSet<File?> {
            val files: MutableSet<File?> = LinkedHashSet<File?>()
            for (artifact in this@DefaultPublishArtifactSet) {
                files.add(artifact.getFile())
            }
            return files
        }
    }
}
