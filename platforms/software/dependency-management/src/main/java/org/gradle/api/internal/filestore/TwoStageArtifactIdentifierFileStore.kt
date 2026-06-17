/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.filestore

import com.google.common.collect.ImmutableSet
import org.gradle.api.Action
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.file.FileAccessTracker
import org.gradle.internal.resource.local.FileStoreException
import org.gradle.internal.resource.local.LocallyAvailableResource
import java.io.File

class TwoStageArtifactIdentifierFileStore(private val readOnlyStore: ArtifactIdentifierFileStore, private val writableStore: ArtifactIdentifierFileStore) : ArtifactIdentifierFileStore {
    private val fileAccessTracker: FileAccessTracker

    init {
        this.fileAccessTracker = TwoStageArtifactIdentifierFileStore.DelegatingFileAccessTracker()
    }

    override fun whereIs(artifactId: ModuleComponentArtifactIdentifier?, checksum: String?): File {
        val file = writableStore.whereIs(artifactId, checksum)
        if (file.exists()) {
            return file
        }
        return readOnlyStore.whereIs(artifactId, checksum)
    }

    override fun getFileAccessTracker(): FileAccessTracker {
        return fileAccessTracker
    }

    @Throws(FileStoreException::class)
    override fun move(key: ModuleComponentArtifactIdentifier?, source: File?): LocallyAvailableResource? {
        return writableStore.move(key, source)
    }

    @Throws(FileStoreException::class)
    override fun add(key: ModuleComponentArtifactIdentifier?, addAction: Action<File?>?): LocallyAvailableResource? {
        return writableStore.add(key, addAction)
    }

    override fun search(key: ModuleComponentArtifactIdentifier?): MutableSet<out LocallyAvailableResource?> {
        val builder = ImmutableSet.builder<LocallyAvailableResource?>()
        builder.addAll(writableStore.search(key))
        builder.addAll(readOnlyStore.search(key))
        return builder.build()
    }

    private inner class DelegatingFileAccessTracker : FileAccessTracker {
        override fun markAccessed(file: File) {
            readOnlyStore.getFileAccessTracker().markAccessed(file)
            writableStore.getFileAccessTracker().markAccessed(file)
        }
    }
}
