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
package org.gradle.api.internal.artifacts

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor
import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.CapabilitySerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectorSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedComponentResultSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedVariantResultSerializer
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactIdentifierSerializer
import org.gradle.api.internal.artifacts.metadata.ComponentFileArtifactIdentifierSerializer
import org.gradle.api.internal.artifacts.metadata.ModuleComponentFileArtifactIdentifierSerializer
import org.gradle.api.internal.artifacts.metadata.PublishArtifactLocalArtifactMetadataSerializer
import org.gradle.api.internal.artifacts.metadata.TransformedComponentFileArtifactIdentifierSerializer
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata
import org.gradle.internal.component.local.model.TransformedComponentFileArtifactIdentifier
import org.gradle.internal.resolve.caching.DesugaringAttributeContainerSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.DefaultSerializerRegistry
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.snapshot.impl.ValueSnapshotterSerializerRegistry
import java.io.File

class DependencyManagementValueSnapshotterSerializerRegistry(
    moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    attributesFactory: AttributesFactory,
    namedObjectInstantiator: NamedObjectInstantiator?,
    componentSelectionDescriptorFactory: ComponentSelectionDescriptorFactory
) : DefaultSerializerRegistry(true), ValueSnapshotterSerializerRegistry {
    init {
        val capabilitySelectorSerializer = CapabilitySelectorSerializer()
        val componentIdentifierSerializer = ComponentIdentifierSerializer()
        val attributeContainerSerializer: AttributeContainerSerializer = DesugaringAttributeContainerSerializer(attributesFactory, namedObjectInstantiator)
        val moduleVersionIdentifierSerializer = ModuleVersionIdentifierSerializer(moduleIdentifierFactory)
        val componentSelectorSerializer: Serializer<ComponentSelector?> = ComponentSelectorSerializer(attributeContainerSerializer, capabilitySelectorSerializer)

        register<Capability?>(Capability::class.java, CapabilitySerializer())
        register<ModuleVersionIdentifier?>(ModuleVersionIdentifier::class.java, moduleVersionIdentifierSerializer)
        register<PublishArtifactLocalArtifactMetadata?>(PublishArtifactLocalArtifactMetadata::class.java, PublishArtifactLocalArtifactMetadataSerializer(componentIdentifierSerializer))
        register<OpaqueComponentArtifactIdentifier?>(OpaqueComponentArtifactIdentifier::class.java, OpaqueComponentArtifactIdentifierSerializer())
        register<DefaultModuleComponentArtifactIdentifier?>(DefaultModuleComponentArtifactIdentifier::class.java, ComponentArtifactIdentifierSerializer())
        register<ModuleComponentFileArtifactIdentifier?>(ModuleComponentFileArtifactIdentifier::class.java, ModuleComponentFileArtifactIdentifierSerializer())
        register<ComponentFileArtifactIdentifier?>(ComponentFileArtifactIdentifier::class.java, ComponentFileArtifactIdentifierSerializer())
        register<TransformedComponentFileArtifactIdentifier?>(TransformedComponentFileArtifactIdentifier::class.java, TransformedComponentFileArtifactIdentifierSerializer())
        register<DefaultModuleComponentIdentifier?>(DefaultModuleComponentIdentifier::class.java, uncheckedCast<Serializer<DefaultModuleComponentIdentifier?>?>(componentIdentifierSerializer))
        register<DefaultProjectComponentIdentifier?>(DefaultProjectComponentIdentifier::class.java, uncheckedCast<Serializer<DefaultProjectComponentIdentifier?>?>(componentIdentifierSerializer))
        register<AttributeContainer?>(AttributeContainer::class.java, attributeContainerSerializer)
        registerWithFactory<ResolvedVariantResult?>(
            ResolvedVariantResult::class.java,
            DefaultSerializerRegistry.SerializerFactory { ResolvedVariantResultSerializer(componentIdentifierSerializer, attributeContainerSerializer) })
        register<ComponentSelectionDescriptorInternal?>(ComponentSelectionDescriptorInternal::class.java, ComponentSelectionDescriptorSerializer(componentSelectionDescriptorFactory))
        val componentSelectionReasonSerializer = ComponentSelectionReasonSerializer(componentSelectionDescriptorFactory)
        register<ComponentSelectionReasonInternal?>(ComponentSelectionReasonInternal::class.java, componentSelectionReasonSerializer)
        register<ComponentSelector?>(ComponentSelector::class.java, componentSelectorSerializer)
        registerWithFactory<ResolvedComponentResult?>(ResolvedComponentResult::class.java, DefaultSerializerRegistry.SerializerFactory {
            val resolvedVariantResultSerializer = ResolvedVariantResultSerializer(componentIdentifierSerializer, attributeContainerSerializer)
            ResolvedComponentResultSerializer(
                moduleVersionIdentifierSerializer,
                componentIdentifierSerializer,
                componentSelectorSerializer,
                resolvedVariantResultSerializer,
                componentSelectionReasonSerializer
            )
        })
    }

    override fun canSerialize(baseType: Class<*>): Boolean {
        return super.canSerialize(baseTypeOf(baseType))
    }

    override fun <T> build(baseType: Class<T?>): Serializer<T?>? {
        return super.build<T?>(uncheckedCast<Class<T?>>(baseTypeOf(baseType)))
    }

    /**
     * A thread-safe and reusable serializer for [OpaqueComponentArtifactIdentifier].
     */
    private class OpaqueComponentArtifactIdentifierSerializer : Serializer<OpaqueComponentArtifactIdentifier?> {
        @Throws(Exception::class)
        override fun read(decoder: Decoder): OpaqueComponentArtifactIdentifier {
            return OpaqueComponentArtifactIdentifier(File(decoder.readString()))
        }

        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: OpaqueComponentArtifactIdentifier) {
            encoder.writeString(value.file.getCanonicalPath())
        }
    }

    companion object {
        private val SUPPORTED_TYPES: MutableList<Class<*>> = ImmutableList.of<Class<*>?>(
            Capability::class.java,
            ModuleVersionIdentifier::class.java,
            PublishArtifactLocalArtifactMetadata::class.java,
            OpaqueComponentArtifactIdentifier::class.java,
            DefaultModuleComponentArtifactIdentifier::class.java,
            ModuleComponentFileArtifactIdentifier::class.java,
            ComponentFileArtifactIdentifier::class.java,
            ComponentIdentifier::class.java,
            AttributeContainer::class.java,
            ResolvedVariantResult::class.java,
            ComponentSelectionDescriptor::class.java,
            ComponentSelectionReason::class.java,
            ComponentSelector::class.java,
            ResolvedComponentResult::class.java
        )

        private fun baseTypeOf(type: Class<*>): Class<*> {
            for (supportedType in SUPPORTED_TYPES) {
                if (supportedType.isAssignableFrom(type)) {
                    return supportedType
                }
            }
            return type
        }
    }
}
