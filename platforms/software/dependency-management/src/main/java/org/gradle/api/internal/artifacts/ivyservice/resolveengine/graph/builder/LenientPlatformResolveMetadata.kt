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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.internal.component.external.model.VirtualComponentIdentifier
import org.gradle.internal.component.model.ComponentGraphResolveMetadata

/**
 * Metadata for a lenient platform component.
 */
class LenientPlatformResolveMetadata internal constructor(private val moduleComponentIdentifier: ModuleComponentIdentifier, private val moduleVersionIdentifier: ModuleVersionIdentifier) :
    ComponentGraphResolveMetadata {
    override fun getId(): ModuleComponentIdentifier {
        return moduleComponentIdentifier
    }

    override fun getModuleVersionId(): ModuleVersionIdentifier {
        return moduleVersionIdentifier
    }

    override fun getAttributesSchema(): ImmutableAttributesSchema {
        return ImmutableAttributesSchema.EMPTY
    }

    override fun isChanging(): Boolean {
        return false
    }

    override fun getStatus(): String {
        return null
    }

    override fun getPlatformOwners(): ImmutableList<out VirtualComponentIdentifier> {
        return ImmutableList.of<VirtualComponentIdentifier>()
    }
}
