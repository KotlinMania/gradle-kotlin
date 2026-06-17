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
package org.gradle.api.publish.ivy.internal.artifact

import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.publish.internal.PublicationArtifactSet
import org.gradle.api.publish.ivy.IvyArtifact
import org.gradle.api.publish.ivy.IvyArtifactSet
import org.gradle.internal.typeconversion.NotationParser
import java.io.File
import java.util.function.Consumer

class DefaultIvyArtifactSet(
    private val publicationName: String,
    private val ivyArtifactParser: NotationParser<Any, IvyArtifact>,
    fileCollectionFactory: FileCollectionFactory,
    collectionCallbackActionDecorator: CollectionCallbackActionDecorator
) : DefaultDomainObjectSet<IvyArtifact>(IvyArtifact::class.java, collectionCallbackActionDecorator), IvyArtifactSet, PublicationArtifactSet<IvyArtifact?> {
    val files: FileCollection

    init {
        this.files = fileCollectionFactory.create(
            DefaultIvyArtifactSet.ArtifactsFileCollection(), Consumer { context: TaskDependencyResolveContext? ->
                for (ivyArtifact in this) {
                    context!!.add(ivyArtifact)
                }
            }
        )
    }

    override fun artifact(source: Any): IvyArtifact {
        val artifact = ivyArtifactParser.parseNotation(source)
        add(artifact)
        return artifact
    }

    override fun artifact(source: Any, config: Action<in IvyArtifact>): IvyArtifact {
        val artifact = artifact(source)
        config.execute(artifact)
        return artifact
    }

    private inner class ArtifactsFileCollection : MinimalFileSet {
        override fun getDisplayName(): String {
            return "artifacts for Ivy publication '" + publicationName + "'"
        }

        override fun getFiles(): MutableSet<File> {
            val files: MutableSet<File> = LinkedHashSet<File>()
            for (artifact in this@DefaultIvyArtifactSet) {
                files.add(artifact.file)
            }
            return files
        }
    }
}
