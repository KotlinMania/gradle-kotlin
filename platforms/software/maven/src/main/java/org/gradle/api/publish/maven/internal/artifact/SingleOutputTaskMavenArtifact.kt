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

import org.gradle.api.Task
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.tasks.TaskProvider
import java.util.function.Consumer

class SingleOutputTaskMavenArtifact(private val generator: TaskProvider<out Task>, private val extension: String?, private val classifier: String?, taskDependencyFactory: TaskDependencyFactory) :
    AbstractMavenArtifact(taskDependencyFactory) {
    private val buildDependencies: TaskDependencyInternal?

    init {
        this.buildDependencies = taskDependencyFactory.visitingDependencies(Consumer { context: TaskDependencyResolveContext? -> context!!.add(getGenerator()) })
    }

    val file: File
        get() = getGenerator().getOutputs().getFiles().getSingleFile()

    private fun getGenerator(): Task {
        return generator.get()
    }

    override fun getDefaultExtension(): String? {
        return extension
    }

    override fun getDefaultClassifier(): String? {
        return classifier
    }

    override fun getDefaultBuildDependencies(): TaskDependencyInternal? {
        return buildDependencies
    }

    val isEnabled: Boolean
        get() = getGenerator().getEnabled()

    override fun shouldBePublished(): Boolean {
        return this.isEnabled
    }
}
