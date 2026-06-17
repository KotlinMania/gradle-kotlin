/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.modulecache

import com.google.common.base.Joiner
import com.google.common.collect.Interner
import org.gradle.api.Action
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor
import org.gradle.internal.resource.local.LocallyAvailableResource
import org.gradle.internal.resource.local.PathKeyFileStore
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ModuleMetadataStore(
    private val metaDataStore: PathKeyFileStore,
    private val moduleMetadataSerializer: ModuleMetadataSerializer,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory?,
    private val stringInterner: Interner<String?>?
) {
    fun getModuleDescriptor(component: ModuleComponentAtRepositoryKey): MutableModuleComponentResolveMetadata? {
        val filePath = getFilePath(component)
        val resource = metaDataStore.get(*filePath)
        if (resource != null) {
            try {
                StringDeduplicatingDecoder(KryoBackedDecoder(FileInputStream(resource.getFile())), stringInterner).use { decoder ->
                    return moduleMetadataSerializer.read(decoder, moduleIdentifierFactory, HashMap<Int?, MavenDependencyDescriptor?>())
                }
            } catch (e: Exception) {
                throw RuntimeException("Could not load module metadata from " + resource.getDisplayName(), e)
            }
        }
        return null
    }

    fun putModuleDescriptor(component: ModuleComponentAtRepositoryKey, metadata: ModuleComponentResolveMetadata?): LocallyAvailableResource? {
        val filePath = getFilePath(component)
        return metaDataStore.add(PATH_JOINER.join(filePath), Action { moduleDescriptorFile: File? ->
            try {
                KryoBackedEncoder(FileOutputStream(moduleDescriptorFile)).use { encoder ->
                    moduleMetadataSerializer.write(encoder, metadata, HashMap<ExternalDependencyDescriptor?, Int?>())
                }
            } catch (e: Exception) {
                throw throwAsUncheckedException(e)
            }
        })
    }

    private fun getFilePath(componentId: ModuleComponentAtRepositoryKey): Array<String?> {
        val moduleComponentIdentifier = componentId.getComponentId()
        return arrayOf<String?>(
            moduleComponentIdentifier.getGroup(),
            moduleComponentIdentifier.getModule(),
            moduleComponentIdentifier.getVersion(),
            componentId.getRepositoryId(),
            "descriptor.bin"
        )
    }

    companion object {
        private val PATH_JOINER = Joiner.on("/")
    }
}
