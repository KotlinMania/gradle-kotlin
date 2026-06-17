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
package org.gradle.internal.component.external.model

import com.google.common.base.Objects
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.capabilities.ImmutableCapability

open class DefaultImmutableCapability(group: String, name: String, version: String?) : ImmutableCapability {
    private val group: String?
    private val name: String?
    private val version: String?
    private val hashCode: Int
    private val cachedId: String

    init {
        this.group = group
        this.name = name
        this.version = version

        this.hashCode = computeHashcode(group, name, version)

        // Using a string instead of a plain ID here might look strange, but this turned out to be
        // the fastest of several experiments, including:
        //
        //    using ModuleIdentifier (initial implementation)
        //    using ModuleIdentifier through ImmutableModuleIdentifierFactory (for interning)
        //    using a 2-level map (by group, then by name)
        //    using an interned string for the cachedId (interning turned out to cost as much as what we gain from faster checks in maps)
        //
        // And none of them reached the performance of just using a good old string
        this.cachedId = group + ":" + name
    }

    private fun computeHashcode(group: String, name: String, version: String?): Int {
        // Do NOT change the order of members used in hash code here, it's been empirically
        // tested to reduce the number of collisions on a large dependency graph (performance test)
        var hash: Int = safeHash(version)
        hash = 31 * hash + name.hashCode()
        hash = 31 * hash + group.hashCode()
        return hash
    }

    override fun getGroup(): String? {
        return group
    }

    override fun getName(): String? {
        return name
    }

    override fun getVersion(): String? {
        return version
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o !is Capability) {
            return false
        }
        val that = o
        return Objects.equal(group, that.getGroup())
                && Objects.equal(name, that.getName())
                && Objects.equal(version, that.getVersion())
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun toString(): String {
        return ("capability "
                + "group='" + group + '\''
                + ", name='" + name + '\''
                + ", version='" + version + '\'')
    }

    override fun getCapabilityId(): String {
        return cachedId
    }

    companion object {
        @JvmStatic
        fun of(capability: Capability): ImmutableCapability {
            if (capability is ImmutableCapability) {
                return capability
            }
            return DefaultImmutableCapability(capability.getGroup(), capability.getName(), capability.getVersion())
        }

        @JvmStatic
        fun defaultCapabilityForComponent(identifier: ModuleVersionIdentifier): DefaultImmutableCapability {
            return DefaultImmutableCapability(identifier.getGroup(), identifier.getName(), identifier.getVersion())
        }

        private fun safeHash(o: String?): Int {
            return if (o == null) 0 else o.hashCode()
        }
    }
}
