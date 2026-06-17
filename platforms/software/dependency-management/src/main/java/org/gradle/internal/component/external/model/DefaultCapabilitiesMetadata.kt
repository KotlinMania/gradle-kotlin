/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.capabilities.CapabilitiesMetadata
import org.gradle.api.capabilities.Capability

/**
 * An immutable implementation of [CapabilitiesMetadata].
 *
 *
 * If possible, use [ImmutableCapabilities] instead of this type. This type
 * should only be used when interfacing with the public API.
 */
class DefaultCapabilitiesMetadata(private val capabilities: ImmutableCapabilities) : CapabilitiesMetadata {
    override fun getCapabilities(): MutableList<out Capability?> {
        return capabilities.asSet().asList()
    }
}
