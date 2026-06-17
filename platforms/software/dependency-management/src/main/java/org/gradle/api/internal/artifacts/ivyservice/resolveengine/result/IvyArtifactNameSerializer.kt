/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import java.io.IOException

/**
 * Serializes and de-serializes [IvyArtifactName]s.
 */
class IvyArtifactNameSerializer private constructor() : AbstractSerializer<IvyArtifactName?>() {
    @Throws(IOException::class)
    override fun read(decoder: Decoder): IvyArtifactName {
        val artifactName = decoder.readString()
        val type = decoder.readString()
        val extension = decoder.readNullableString()
        val classifier = decoder.readNullableString()
        return DefaultIvyArtifactName(artifactName!!, type!!, extension, classifier)
    }

    @Throws(IOException::class)
    override fun write(encoder: Encoder, value: IvyArtifactName) {
        encoder.writeString(value.name)
        encoder.writeString(value.type)
        encoder.writeNullableString(value.extension)
        encoder.writeNullableString(value.classifier)
    }

    @Throws(IOException::class)
    fun writeNullable(encoder: Encoder, value: IvyArtifactName?) {
        if (value == null) {
            encoder.writeBoolean(false)
        } else {
            encoder.writeBoolean(true)
            write(encoder, value)
        }
    }

    @Throws(IOException::class)
    fun readNullable(decoder: Decoder): IvyArtifactName? {
        val hasArtifact = decoder.readBoolean()
        if (hasArtifact) {
            return read(decoder)
        }
        return null
    }

    companion object {
        val INSTANCE: IvyArtifactNameSerializer = IvyArtifactNameSerializer()
    }
}
