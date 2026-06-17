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

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.artifacts.CapabilityResolutionDetails
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser
import org.gradle.api.provider.Provider
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.component.external.model.DefaultImmutableCapability.Companion.of
import java.util.function.Supplier

/**
 * Default implementation of [CapabilitiesResolutionInternal].
 */
class DefaultCapabilitiesResolution(private val capabilityNotationParser: CapabilityNotationParser) : CapabilitiesResolutionInternal {
    private var actions: MutableList<RegisteredAction>? = null

    override fun all(action: Action<in CapabilityResolutionDetails>) {
        doAddAction(null, action)
    }

    override fun withCapability(capability: Capability, action: Action<in CapabilityResolutionDetails>) {
        doAddAction(Supplier? { capability }, action)
    }

    override fun withCapability(group: String, name: String, action: Action<in CapabilityResolutionDetails>) {
        doAddAction(Supplier? { DefaultImmutableCapability(group, name, null) }, action)
    }

    override fun withCapability(notation: Any, action: Action<in CapabilityResolutionDetails>) {
        val capabilitySupplier = if (notation is Provider<*>)
            Supplier { notation.map<Capability>({ notation: N? -> capabilityNotationParser.parseNotation(notation) }).get() }
        else
            Supplier { capabilityNotationParser.parseNotation(notation) }
        doAddAction(capabilitySupplier, action)
    }

    fun doAddAction(
        notation: Supplier<Capability>?,
        action: Action<in CapabilityResolutionDetails>
    ) {
        if (actions == null) {
            actions = ArrayList<RegisteredAction>()
        }
        actions!!.add(RegisteredAction(notation, action))
    }

    override fun getRules(): ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule> {
        if (actions == null || actions!!.isEmpty()) {
            return ImmutableList.of<CapabilitiesResolutionInternal.CapabilityResolutionRule>()
        }

        val builder = ImmutableList.builderWithExpectedSize<CapabilitiesResolutionInternal.CapabilityResolutionRule>(actions!!.size)
        for (registeredAction in actions) {
            builder.add(registeredAction.asCapabilityResolutionAction())
        }
        return builder.build()
    }

    /**
     * Holds a supplier to the capability notation, allowing us to defer
     * parsing it until we actually need it.
     */
    private class RegisteredAction(
        private val notation: Supplier<Capability>?,
        private val action: Action<in CapabilityResolutionDetails>
    ) {
        fun asCapabilityResolutionAction(): CapabilitiesResolutionInternal.CapabilityResolutionRule {
            if (notation == null) {
                return CapabilitiesResolutionInternal.CapabilityResolutionRule(null, action)
            }
            return CapabilitiesResolutionInternal.CapabilityResolutionRule(of(notation.get()), action)
        }
    }
}
