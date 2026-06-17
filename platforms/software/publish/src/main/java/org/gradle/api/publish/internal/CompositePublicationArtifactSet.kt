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
import org.gradle.api.internal.CompositeDomainObjectSet
import org.gradle.api.internal.DelegatingDomainObjectSet
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.UnionFileCollection
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.publish.PublicationArtifact

class CompositePublicationArtifactSet<T : PublicationArtifact?> @SafeVarargs constructor(
    taskDependencyFactory: TaskDependencyFactory,
    type: Class<T?>,
    vararg artifactSets: PublicationArtifactSet<T?>?
) : DelegatingDomainObjectSet<T?>(
    CompositeDomainObjectSet.create<T?>(type, *artifactSets)
), PublicationArtifactSet<T?> {
    private val files: FileCollection

    init {
        val fileCollections = arrayOfNulls<FileCollectionInternal>(artifactSets.size)
        for (i in artifactSets.indices) {
            fileCollections[i] = artifactSets[i]!!.getFiles() as FileCollectionInternal?
        }
        files = UnionFileCollection(taskDependencyFactory, *fileCollections)
    }

    override fun getFiles(): FileCollection {
        return files
    }
}
