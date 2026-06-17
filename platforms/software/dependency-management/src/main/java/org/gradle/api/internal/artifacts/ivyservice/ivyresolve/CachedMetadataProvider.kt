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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataAdapter
import org.gradle.internal.component.external.model.ExternalComponentResolveMetadata
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult

internal class CachedMetadataProvider(private val cachedResult: BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState?>) : MetadataProvider {
    private val cachedComponentMetadata: ComponentMetadata?
    private val usable: Boolean

    init {
        usable = cachedResult.state === BuildableModuleComponentMetaDataResolveResult.State.Resolved
        if (usable) {
            @Suppress("deprecation") val legacyMetadata: ExternalComponentResolveMetadata = cachedResult.metaData.getLegacyMetadata()
            cachedComponentMetadata = ComponentMetadataAdapter(legacyMetadata)
        } else {
            cachedComponentMetadata = null
        }
    }

    override fun getComponentMetadata(): ComponentMetadata? {
        return cachedComponentMetadata
    }

    override fun getIvyModuleDescriptor(): IvyModuleDescriptor? {
        @Suppress("deprecation") val legacyMetadata: ExternalComponentResolveMetadata? = cachedResult.metaData.getLegacyMetadata()
        if (legacyMetadata is IvyModuleResolveMetadata) {
            val ivyMetadata = legacyMetadata
            return DefaultIvyModuleDescriptor(ivyMetadata.extraAttributes, ivyMetadata.branch, ivyMetadata.status!!)
        }
        return null
    }

    override fun isUsable(): Boolean {
        return usable
    }
}
