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

import org.gradle.api.internal.capabilities.CapabilityInternal
import org.gradle.api.internal.capabilities.ImmutableCapability
import org.gradle.api.internal.capabilities.ShadowedCapability

/**
 * A capability that is shadowed by another capability.
 *
 *
 * This class remains deeply immutable.
 */
class ShadowedImmutableCapability(shadowed: CapabilityInternal, private val appendix: String) : ShadowedCapability {
    private val shadowed: ImmutableCapability

    init {
        this.shadowed = DefaultImmutableCapability.of(shadowed)
    }

    override fun getAppendix(): String {
        return appendix
    }

    override fun getShadowedCapability(): ImmutableCapability {
        return shadowed
    }

    override fun getGroup(): String {
        return shadowed.getGroup()
    }

    override fun getName(): String {
        return shadowed.getName() + appendix
    }

    override fun getVersion(): String {
        return shadowed.getVersion()!!
    }

    override fun getCapabilityId(): String {
        return shadowed.getCapabilityId() + appendix
    }
}
