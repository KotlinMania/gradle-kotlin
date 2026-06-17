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
package org.gradle.api.publish.ivy.internal.artifact

import com.google.common.io.Files
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationCoordinates
import org.gradle.api.tasks.TaskDependency
import java.io.File

class FileBasedIvyArtifact(val file: File, private val coordinates: IvyPublicationCoordinates, taskDependencyFactory: TaskDependencyFactory) : AbstractIvyArtifact(taskDependencyFactory) {
    private val extension: String

    init {
        extension = Files.getFileExtension(file.getName())
    }

    override fun getDefaultName(): String {
        return coordinates.getModule().get()
    }

    override fun getDefaultType(): String {
        return extension
    }

    override fun getDefaultExtension(): String {
        return extension
    }

    override fun getDefaultClassifier(): String {
        return null
    }

    override fun getDefaultConf(): String {
        return null
    }

    override fun getDefaultBuildDependencies(): TaskDependency {
        return TaskDependencyInternal.EMPTY
    }

    override fun shouldBePublished(): Boolean {
        return true
    }
}
