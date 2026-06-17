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
import org.gradle.api.publish.internal.PublicationInternal
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.tasks.TaskDependency

class DerivedIvyArtifact(private val original: IvyArtifact, private val derived: PublicationInternal.DerivedArtifact, taskDependencyFactory: TaskDependencyFactory) :
    AbstractIvyArtifact(taskDependencyFactory) {
    override fun getDefaultName(): String {
        return original.getName()
    }

    override fun getDefaultType(): String {
        return Files.getFileExtension(file.getName())
    }

    override fun getDefaultExtension(): String {
        return original.getExtension() + "." + getType()
    }

    override fun getDefaultClassifier(): String {
        return original.getClassifier()
    }

    override fun getDefaultConf(): String {
        return original.getConf()
    }

    override fun getDefaultBuildDependencies(): TaskDependency {
        return original.getBuildDependencies()
    }

    val file: File
        get() = derived.create()

    override fun shouldBePublished(): Boolean {
        return derived.shouldBePublished()
    }
}
