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
package org.gradle.api.publish.internal

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.publish.PublicationArtifact
import java.io.File
import java.util.function.Consumer

class DefaultPublicationArtifactSet<T : PublicationArtifact?>(
    type: Class<T?>,
    private val name: String,
    fileCollectionFactory: FileCollectionFactory,
    collectionCallbackActionDecorator: CollectionCallbackActionDecorator
) : DefaultDomainObjectSet<T?>(type, collectionCallbackActionDecorator), PublicationArtifactSet<T?> {
    private val files: FileCollection

    init {
        files = fileCollectionFactory.create(
            object : MinimalFileSet {
                override fun getDisplayName(): String {
                    return this@DefaultPublicationArtifactSet.name
                }

                override fun getFiles(): MutableSet<File?> {
                    val result: MutableSet<File?> = LinkedHashSet<File?>()
                    for (artifact in this@DefaultPublicationArtifactSet) {
                        result.add(artifact!!.getFile())
                    }
                    return result
                }
            },
            Consumer { context: TaskDependencyResolveContext? ->
                for (artifact in this@DefaultPublicationArtifactSet) {
                    context!!.add(artifact!!.getBuildDependencies())
                }
            }
        )
    }

    override fun getFiles(): FileCollection {
        return files
    }
}
