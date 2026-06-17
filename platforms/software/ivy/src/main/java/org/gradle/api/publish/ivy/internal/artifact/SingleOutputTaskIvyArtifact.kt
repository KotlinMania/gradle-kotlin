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

import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.publish.ivy.internal.publisher.IvyPublicationCoordinates
import org.gradle.api.tasks.TaskDependency
import org.gradle.api.tasks.TaskProvider
import java.util.function.Consumer

class SingleOutputTaskIvyArtifact(
    private val generator: TaskProvider<out Task>,
    private val coordinates: IvyPublicationCoordinates,
    private val extension: String,
    private val type: String,
    private val classifier: String?,
    taskDependencyFactory: TaskDependencyFactory
) : AbstractIvyArtifact(taskDependencyFactory) {
    private val buildDependencies: TaskDependencyInternal

    init {
        this.buildDependencies = taskDependencyFactory.visitingDependencies(Consumer { context: TaskDependencyResolveContext? ->
            context!!.add(generator.get())
        })
    }

    override fun getDefaultName(): String {
        return coordinates.getModule().get()
    }

    override fun getDefaultType(): String {
        return type
    }

    override fun getDefaultExtension(): String {
        return extension
    }

    override fun getDefaultClassifier(): String {
        return classifier!!
    }

    override fun getDefaultConf(): String {
        return null
    }

    override fun getDefaultBuildDependencies(): TaskDependency {
        return buildDependencies
    }

    val file: File
        get() = generator.get().getOutputs().getFiles().getSingleFile()

    val isEnabled: Boolean
        get() {
            val task = generator.get() as TaskInternal
            return task.getOnlyIf().isSatisfiedBy(task)
        }

    override fun shouldBePublished(): Boolean {
        return this.isEnabled
    }
}
