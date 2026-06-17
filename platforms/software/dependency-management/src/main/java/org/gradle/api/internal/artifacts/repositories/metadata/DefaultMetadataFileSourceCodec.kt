/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.metadata

import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.filestore.ArtifactIdentifierFileStore
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier
import org.gradle.internal.component.model.PersistentModuleSource
import org.gradle.internal.hash.HashCode
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import java.io.IOException

/**
 * A codec for [MetadataFileSource]. This codec is particular because of the persistent cache
 * which must be relocatable. As a consequence, it would be an error to serialize the file path because
 * it would contain an absolute path to the descriptor file.
 *
 * Therefore, the deserialized metadata file source reconstructs the file path from the component
 * module artifact identifier.
 */
class DefaultMetadataFileSourceCodec(private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory, private val fileStore: ArtifactIdentifierFileStore) :
    PersistentModuleSource.Codec<MetadataFileSource?> {
    @Throws(IOException::class)
    override fun encode(moduleSource: MetadataFileSource, encoder: Encoder) {
        val artifactId = moduleSource.getArtifactId()
        val componentIdentifier = artifactId.getComponentIdentifier()
        encoder.writeString(componentIdentifier.getGroup())
        encoder.writeString(componentIdentifier.getModule())
        encoder.writeString(componentIdentifier.getVersion())
        encoder.writeString(artifactId.fileName)
        encoder.writeBinary(moduleSource.getSha1().toByteArray())
    }

    @Throws(IOException::class)
    override fun decode(decoder: Decoder): MetadataFileSource {
        val group = decoder.readString()
        val module = decoder.readString()
        val version = decoder.readString()
        val name = decoder.readString()
        val sha1 = decoder.readBinary()
        val source = createSource(sha1!!, group!!, module!!, version!!, name!!)
        return source
    }

    private fun createSource(sha1: ByteArray, group: String, module: String, version: String, name: String): DefaultMetadataFileSource {
        val artifactId: ModuleComponentArtifactIdentifier = createArtifactId(group, module, version, name)
        val hashCode = HashCode.fromBytes(sha1)
        val metadataFile = fileStore.whereIs(artifactId, hashCode.toString())
        return DefaultMetadataFileSource(
            artifactId,
            metadataFile!!,
            hashCode
        )
    }

    private fun createArtifactId(group: String, module: String, version: String, name: String): ModuleComponentFileArtifactIdentifier {
        return ModuleComponentFileArtifactIdentifier(
            DefaultModuleComponentIdentifier.newId(
                moduleIdentifierFactory.module(group, module)!!,
                version
            ),
            name
        )
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        return o != null && javaClass == o.javaClass
    }

    override fun hashCode(): Int {
        return moduleIdentifierFactory.hashCode()
    }
}
