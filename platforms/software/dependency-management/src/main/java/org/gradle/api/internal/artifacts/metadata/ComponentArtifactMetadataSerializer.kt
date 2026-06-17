/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.base.Objects
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.IvyArtifactNameSerializer
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder

class ComponentArtifactMetadataSerializer : AbstractSerializer<ComponentArtifactMetadata?>() {
    private val componentIdentifierSerializer = ComponentIdentifierSerializer()

    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: ComponentArtifactMetadata) {
        if (value is ModuleComponentArtifactMetadata) {
            val moduleComponentArtifactMetadata = value
            componentIdentifierSerializer.write(encoder, moduleComponentArtifactMetadata.getComponentId())
            IvyArtifactNameSerializer.INSTANCE.write(encoder, moduleComponentArtifactMetadata.getName())
        } else {
            throw IllegalArgumentException("Unknown artifact metadata type.")
        }
    }

    @Throws(Exception::class)
    override fun read(decoder: Decoder): ComponentArtifactMetadata {
        val componentIdentifier = componentIdentifierSerializer.read(decoder) as ModuleComponentIdentifier
        val name = IvyArtifactNameSerializer.INSTANCE.read(decoder)
        return DefaultModuleComponentArtifactMetadata(componentIdentifier, name)
    }

    public override fun equals(obj: Any): Boolean {
        if (!super.equals(obj)) {
            return false
        }

        val rhs = obj as ComponentArtifactMetadataSerializer
        return Objects.equal(componentIdentifierSerializer, rhs.componentIdentifierSerializer)
    }

    public override fun hashCode(): Int {
        return Objects.hashCode(super.hashCode(), componentIdentifierSerializer)
    }
}
