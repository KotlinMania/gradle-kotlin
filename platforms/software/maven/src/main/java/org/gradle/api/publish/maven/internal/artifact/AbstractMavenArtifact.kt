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
package org.gradle.api.publish.maven.internal.artifact

import com.google.common.base.Strings
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.publish.internal.PublicationArtifactInternal
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.tasks.TaskDependency
import java.util.function.Consumer

abstract class AbstractMavenArtifact protected constructor(taskDependencyFactory: TaskDependencyFactory) : MavenArtifact, PublicationArtifactInternal {
    private val allBuildDependencies: TaskDependency
    private val additionalBuildDependencies: DefaultTaskDependency
    private var extension: String? = null
    private var classifier: String? = null

    init {
        this.additionalBuildDependencies = DefaultTaskDependency()
        this.allBuildDependencies = taskDependencyFactory.visitingDependencies(Consumer { context: TaskDependencyResolveContext? ->
            context!!.add(this.defaultBuildDependencies)
            additionalBuildDependencies.visitDependencies(context)
        })
    }

    abstract val file: File

    override fun getExtension(): String? {
        return if (extension != null) extension else this.defaultExtension
    }

    protected abstract val defaultExtension: String?

    override fun setExtension(extension: String?) {
        this.extension = Strings.nullToEmpty(extension)
    }

    override fun getClassifier(): String? {
        return Strings.emptyToNull(if (classifier != null) classifier else this.defaultClassifier)
    }

    protected abstract val defaultClassifier: String?

    override fun setClassifier(classifier: String?) {
        this.classifier = Strings.nullToEmpty(classifier)
    }

    override fun builtBy(vararg tasks: Any?) {
        additionalBuildDependencies.add(*tasks)
    }

    override fun getBuildDependencies(): TaskDependency {
        return allBuildDependencies
    }

    protected abstract val defaultBuildDependencies: TaskDependency?

    override fun toString(): String {
        return javaClass.getSimpleName() + " " + getExtension() + ":" + getClassifier()
    }
}
