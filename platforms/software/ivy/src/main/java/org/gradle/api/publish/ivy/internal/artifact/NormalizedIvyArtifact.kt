/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.internal.provider.DefaultProvider
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskDependency
import java.io.File
import java.io.Serializable
import java.util.concurrent.Callable

class NormalizedIvyArtifact(artifact: IvyArtifactInternal) : IvyArtifactInternal, Serializable {
    val file: File
    private val extension: String
    private val classifier: String?
    private val name: String
    private val type: String
    private val conf: String?
    private val shouldBePublished: Provider<Boolean>

    init {
        this.name = artifact.getName()
        this.type = artifact.getType()
        this.conf = artifact.getConf()
        this.file = artifact.file
        this.extension = artifact.getExtension()
        this.classifier = artifact.getClassifier()
        this.shouldBePublished = DefaultProvider<Boolean>(Callable { artifact.shouldBePublished() })
    }

    public override fun getName(): String {
        return name
    }

    public override fun setName(name: String) {
        throw IllegalStateException()
    }

    public override fun getType(): String {
        return type
    }

    public override fun setType(type: String) {
        throw IllegalStateException()
    }

    public override fun getConf(): String? {
        return conf
    }

    public override fun setConf(conf: String?) {
        throw IllegalStateException()
    }

    public override fun getExtension(): String {
        return extension
    }

    public override fun setExtension(extension: String) {
        throw IllegalStateException()
    }

    public override fun getClassifier(): String? {
        return classifier
    }

    public override fun setClassifier(classifier: String?) {
        throw IllegalStateException()
    }

    override fun builtBy(vararg tasks: Any) {
        throw IllegalStateException()
    }

    override fun getBuildDependencies(): TaskDependency {
        throw IllegalStateException()
    }

    override fun shouldBePublished(): Boolean {
        return shouldBePublished.get()
    }

    override fun asNormalisedArtifact(): NormalizedIvyArtifact {
        return this
    }
}
