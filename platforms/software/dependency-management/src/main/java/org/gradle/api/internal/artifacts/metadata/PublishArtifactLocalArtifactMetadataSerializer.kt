/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.artifacts.metadata

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.internal.artifacts.publish.ImmutablePublishArtifact
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import java.io.File

/**
 * A thread-safe and reusable serializer for [PublishArtifactLocalArtifactMetadata].
 */
class PublishArtifactLocalArtifactMetadataSerializer(private val componentIdentifierSerializer: ComponentIdentifierSerializer) : Serializer<PublishArtifactLocalArtifactMetadata?> {
    @Throws(Exception::class)
    override fun read(decoder: Decoder): PublishArtifactLocalArtifactMetadata {
        val identifier = componentIdentifierSerializer.read(decoder)
        val artifactName = decoder.readString()
        val artifactExtension = decoder.readString()
        val artifactType = decoder.readString()
        val artifactClassifier = decoder.readNullableString()
        val artifactFile = File(decoder.readString())
        return PublishArtifactLocalArtifactMetadata(
            identifier,
            ImmutablePublishArtifact(artifactName, artifactExtension, artifactType, artifactClassifier, artifactFile)
        )
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: PublishArtifactLocalArtifactMetadata) {
        componentIdentifierSerializer.write(encoder, value.getComponentIdentifier())
        val publishArtifact = value.publishArtifact
        encoder.writeString(publishArtifact.getName())
        encoder.writeString(publishArtifact.getType())
        encoder.writeString(publishArtifact.getExtension())
        encoder.writeNullableString(publishArtifact.getClassifier())
        encoder.writeString(publishArtifact.getFile().getCanonicalPath())
    }
}
