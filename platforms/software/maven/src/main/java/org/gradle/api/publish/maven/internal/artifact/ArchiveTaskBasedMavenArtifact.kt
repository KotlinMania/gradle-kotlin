/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.publish.maven.internal.artifact

import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.tasks.bundling.AbstractArchiveTask

class ArchiveTaskBasedMavenArtifact(private val archiveTask: AbstractArchiveTask, taskDependencyFactory: TaskDependencyFactory) : AbstractMavenArtifact(taskDependencyFactory) {
    private val buildDependencies: TaskDependencyInternal?

    init {
        this.buildDependencies = taskDependencyFactory.configurableDependency(ImmutableSet.of<Any?>(archiveTask))
    }

    val file: File
        get() = archiveTask.getArchiveFile().get().getAsFile()

    override fun getDefaultExtension(): String? {
        return archiveTask.getArchiveExtension().getOrNull()
    }

    override fun getDefaultClassifier(): String? {
        return archiveTask.getArchiveClassifier().getOrNull()
    }

    override fun getDefaultBuildDependencies(): TaskDependencyInternal? {
        return buildDependencies
    }

    override fun shouldBePublished(): Boolean {
        return archiveTask.isEnabled()
    }
}
