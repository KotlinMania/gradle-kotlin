/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.InvalidUserDataException
import org.gradle.api.capabilities.CapabilitiesMetadata
import org.gradle.api.capabilities.Capability
import org.gradle.api.capabilities.MutableCapabilitiesMetadata
import org.gradle.api.internal.capabilities.ImmutableCapability

/**
 * Default implementation of [MutableCapabilitiesMetadata].
 *
 *
 * If possible, try to avoid using this type unless interfacing with the public API.
 */
class DefaultMutableCapabilitiesMetadata(capabilities: ImmutableCapabilities) : MutableCapabilitiesMetadata {
    private val descriptors: MutableSet<ImmutableCapability>

    init {
        this.descriptors = LinkedHashSet<ImmutableCapability>(capabilities.asSet())
    }

    override fun addCapability(group: String?, name: String?, version: String?) {
        for (descriptor in descriptors) {
            if (descriptor.getGroup() == group && descriptor.getName() == name && (descriptor.getVersion() != version)) {
                throw InvalidUserDataException("Cannot add capability " + group + ":" + name + " with version " + version + " because it's already defined with version " + descriptor.getVersion())
            }
        }
        descriptors.add(DefaultImmutableCapability(group, name, version))
    }

    override fun removeCapability(group: String?, name: String?) {
        descriptors.removeIf { next: ImmutableCapability? -> next!!.getGroup() == group && next.getName() == name }
    }

    override fun asImmutable(): CapabilitiesMetadata {
        return DefaultCapabilitiesMetadata(asImmutableCapabilities())
    }

    override fun getCapabilities(): ImmutableList<out Capability?> {
        return ImmutableList.copyOf<ImmutableCapability?>(descriptors)
    }

    fun asImmutableCapabilities(): ImmutableCapabilities {
        return ImmutableCapabilities(ImmutableSet.copyOf<ImmutableCapability?>(descriptors))
    }
}
