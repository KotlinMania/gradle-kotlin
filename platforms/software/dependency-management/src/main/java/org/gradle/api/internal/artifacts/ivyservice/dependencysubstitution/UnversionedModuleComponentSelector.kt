/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution

import com.google.common.base.Objects
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.component.ComponentSelectorInternal
import org.gradle.api.internal.attributes.ImmutableAttributes

internal class UnversionedModuleComponentSelector(val moduleIdentifier: ModuleIdentifier) : ComponentSelectorInternal {
    override fun getDisplayName(): String {
        return moduleIdentifier.toString() + ":*"
    }

    override fun matchesStrictly(identifier: ComponentIdentifier): Boolean {
        return false
    }

    override fun getAttributes(): ImmutableAttributes {
        return ImmutableAttributes.EMPTY
    }

    override fun getRequestedCapabilities(): MutableList<Capability> {
        return mutableListOf<Capability>()
    }

    override fun getCapabilitySelectors(): ImmutableSet<CapabilitySelector> {
        return ImmutableSet.of<CapabilitySelector>()
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as UnversionedModuleComponentSelector
        return Objects.equal(moduleIdentifier, that.moduleIdentifier)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(moduleIdentifier)
    }
}
