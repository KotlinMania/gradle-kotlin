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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StringVersioned
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal

interface ComponentResolutionState : StringVersioned {
    val componentId: ComponentIdentifier?

    @JvmField
    val id: ModuleVersionIdentifier?

    @JvmField
    val module: CandidateModule?

    /**
     * Returns the meta-data for the component. Resolves if not already resolved.
     *
     * @return null if the meta-data is not available due to some failure.
     */
    val metadataOrNull: ComponentGraphResolveMetadata?

    fun addCause(componentSelectionDescriptor: ComponentSelectionDescriptorInternal)

    fun reject()

    val isRejected: Boolean

    @JvmField
    val platformOwners: MutableSet<VirtualPlatformState>?

    @JvmField
    val platformState: VirtualPlatformState?
}
