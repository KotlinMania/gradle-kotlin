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

import com.google.common.collect.ImmutableSet
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.capabilities.ImmutableCapability
import org.gradle.internal.component.model.ImmutableModuleSources.Companion.of
import org.gradle.internal.component.model.MutableModuleSources.Companion.of
import java.util.Collections

/**
 * A deeply immutable implementation of [CapabilitiesMetadata].
 *
 * This type will ensure that all contents are immutable upon construction,
 * in order to allow instances of this type to be safely reused whenever possible
 * to avoid unnecessary memory allocations.
 *
 * Note that while this class is not itself `final`, all fields are private, so
 * subclassing should not break the immutability contract.
 */
class ImmutableCapabilities(private val capabilities: ImmutableSet<ImmutableCapability?>) : Iterable<ImmutableCapability?> {
    fun asSet(): ImmutableSet<ImmutableCapability?> {
        return capabilities
    }

    override fun iterator(): MutableIterator<ImmutableCapability?> {
        if (capabilities.isEmpty()) {
            // Avoid allocating an iterator object for the empty set
            return Collections.emptyIterator<ImmutableCapability?>()
        }
        return capabilities.iterator()
    }

    val isEmpty: Boolean
        get() = capabilities.isEmpty()

    /**
     * Returns this instance if it contains any capabilities, or returns a new instance containing the default capability
     * if this is empty.
     *
     * @param defaultCapability the default capability to include in the result if the given capabilities set is empty
     * @return `this` if it contains any capabilities; otherwise a new instance containing the given capability
     */
    fun orElse(defaultCapability: ImmutableCapability?): ImmutableCapabilities? {
        if (capabilities.isEmpty()) {
            return of(defaultCapability)
        } else {
            return this
        }
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as ImmutableCapabilities
        return capabilities == that.capabilities
    }

    override fun hashCode(): Int {
        return capabilities.hashCode()
    }

    companion object {
        @JvmField
        val EMPTY: ImmutableCapabilities = ImmutableCapabilities(ImmutableSet.of<ImmutableCapability?>())

        @JvmStatic
        fun of(capability: Capability?): ImmutableCapabilities? {
            if (capability == null) {
                return EMPTY
            }
            return ImmutableCapabilities(ImmutableSet.of<ImmutableCapability?>(DefaultImmutableCapability.Companion.of(capability)))
        }

        fun of(capabilities: MutableCollection<out Capability?>?): ImmutableCapabilities? {
            if (capabilities == null || capabilities.isEmpty()) {
                return EMPTY
            }
            if (capabilities.size == 1) {
                val single: Capability? = capabilities.iterator().next()
                return of(single)
            }

            val builder = ImmutableSet.builderWithExpectedSize<ImmutableCapability?>(capabilities.size)
            for (capability in capabilities) {
                builder.add(DefaultImmutableCapability.Companion.of(capability))
            }
            return ImmutableCapabilities(builder.build())
        }
    }
}
