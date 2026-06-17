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
package org.gradle.internal.component.model

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.capabilities.ImmutableCapability
import org.gradle.internal.component.external.model.DefaultImmutableCapability

abstract class AbstractComponentGraphResolveState<T : ComponentGraphResolveMetadata?>(private val instanceId: Long, private val graphMetadata: T?) : ComponentGraphResolveState,
    ComponentArtifactResolveState {
    private val implicitCapability: ImmutableCapability

    init {
        this.implicitCapability = DefaultImmutableCapability.defaultCapabilityForComponent(graphMetadata!!.getModuleVersionId())
    }

    override fun toString(): String {
        return getId().toString()
    }

    override fun getInstanceId(): Long {
        return instanceId
    }

    override fun getId(): ComponentIdentifier {
        return graphMetadata!!.getId()
    }

    override fun getMetadata(): T? {
        return graphMetadata
    }

    override fun isAdHoc(): Boolean {
        return false
    }

    override fun prepareForArtifactResolution(): ComponentArtifactResolveState {
        return this
    }

    override fun getDefaultCapability(): ImmutableCapability {
        return implicitCapability
    }
}
