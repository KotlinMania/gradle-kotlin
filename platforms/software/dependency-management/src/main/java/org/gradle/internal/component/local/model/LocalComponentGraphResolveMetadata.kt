/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.component.local.model

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.internal.component.external.model.VirtualComponentIdentifier
import org.gradle.internal.component.model.ComponentGraphResolveMetadata

/**
 * Implementation of [ComponentGraphResolveMetadata] for local components.
 */
class LocalComponentGraphResolveMetadata(
    moduleVersionId: ModuleVersionIdentifier,
    componentId: ComponentIdentifier,
    status: String,
    attributesSchema: ImmutableAttributesSchema
) : ComponentGraphResolveMetadata {
    private val componentId: ComponentIdentifier
    private val moduleVersionId: ModuleVersionIdentifier
    private val status: String
    private val attributesSchema: ImmutableAttributesSchema

    init {
        this.moduleVersionId = moduleVersionId
        this.componentId = componentId
        this.status = status
        this.attributesSchema = attributesSchema
    }

    override fun getId(): ComponentIdentifier {
        return componentId
    }

    override fun getModuleVersionId(): ModuleVersionIdentifier {
        return moduleVersionId
    }

    override fun toString(): String {
        return componentId.getDisplayName()
    }

    override fun isChanging(): Boolean {
        return false
    }

    override fun getStatus(): String {
        return status
    }

    override fun getPlatformOwners(): ImmutableList<out VirtualComponentIdentifier> {
        return ImmutableList.of<VirtualComponentIdentifier>()
    }

    override fun getAttributesSchema(): ImmutableAttributesSchema {
        return attributesSchema
    }
}
