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
package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy

import org.gradle.api.Action
import org.gradle.api.artifacts.CapabilitiesResolution
import org.gradle.api.artifacts.CapabilityResolutionDetails
import org.gradle.api.internal.capabilities.ImmutableCapability

/**
 * Internal counterpart to [CapabilitiesResolution].
 */
interface CapabilitiesResolutionInternal : CapabilitiesResolution {
    val rules: ImmutableList<CapabilityResolutionRule>?

    /**
     * An action that may resolve a capability conflict.
     */
    class CapabilityResolutionRule(
        private val capability: ImmutableCapability?,
        /**
         * The action to apply.
         */
        val action: Action<in CapabilityResolutionDetails>
    ) {
        /**
         * Returns true if this rule may be executed to resolve conflicts on
         * a capability with the given group and name.
         */
        fun appliesTo(group: String, name: String): Boolean {
            return capability == null || (capability.getGroup() == group && capability.getName() == name)
        }
    }
}
