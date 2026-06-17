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
package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.file.FileSystemLocation
import org.gradle.api.internal.artifacts.PublishArtifactInternal
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.tasks.TaskDependency
import java.io.File
import java.util.Date

class FileSystemPublishArtifact(private val fileSystemLocation: FileSystemLocation, private val version: String?) : PublishArtifactInternal {
    private var artifactFile: ArtifactFile? = null

    override fun getName(): String {
        return this.value.getName()
    }

    override fun getExtension(): String {
        return this.value.getExtension()
    }

    override fun getType(): String {
        return ""
    }

    override fun getClassifier(): String {
        return this.value.getClassifier()
    }

    override fun getFile(): File {
        return fileSystemLocation.getAsFile()
    }

    override fun getDate(): Date {
        return Date()
    }

    override fun getBuildDependencies(): TaskDependency {
        return TaskDependencyInternal.EMPTY
    }

    private val value: ArtifactFile
        get() {
            if (artifactFile == null) {
                artifactFile = ArtifactFile(getFile(), version)
            }
            return artifactFile!!
        }

    override fun shouldBePublished(): Boolean {
        return true
    }
}
