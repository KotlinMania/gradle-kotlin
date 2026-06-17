/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.publish.internal.PublicationArtifactSet
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.MavenArtifactSet
import org.gradle.internal.typeconversion.NotationParser
import java.io.File
import java.util.function.Consumer
import javax.inject.Inject

class DefaultMavenArtifactSet @Inject constructor(
    private val publicationName: String,
    private val mavenArtifactParser: NotationParser<Any?, MavenArtifact>,
    fileCollectionFactory: FileCollectionFactory,
    collectionCallbackActionDecorator: CollectionCallbackActionDecorator
) : DefaultDomainObjectSet<MavenArtifact?>(MavenArtifact::class.java, collectionCallbackActionDecorator), MavenArtifactSet, PublicationArtifactSet<MavenArtifact?> {
    val files: FileCollection

    init {
        this.files = fileCollectionFactory.create(
            DefaultMavenArtifactSet.ArtifactsFileCollection(), Consumer { context: TaskDependencyResolveContext? ->
                for (mavenArtifact in this@DefaultMavenArtifactSet) {
                    context!!.add(mavenArtifact)
                }
            }
        )
    }

    override fun artifact(source: Any?): MavenArtifact {
        val artifact = mavenArtifactParser.parseNotation(source)
        add(artifact)
        return artifact
    }

    override fun artifact(source: Any?, config: Action<in MavenArtifact?>): MavenArtifact {
        val artifact = artifact(source)
        config.execute(artifact)
        return artifact
    }

    private inner class ArtifactsFileCollection : MinimalFileSet {
        override fun getDisplayName(): String {
            return "artifacts for Maven publication '" + publicationName + "'"
        }

        override fun getFiles(): MutableSet<File?> {
            val files: MutableSet<File?> = LinkedHashSet<File?>()
            for (artifact in this@DefaultMavenArtifactSet) {
                files.add(artifact.file)
            }
            return files
        }
    }
}
