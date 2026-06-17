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
package org.gradle.nativeplatform.toolchain.internal

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer
import org.gradle.internal.logging.text.DiagnosticsVisitor
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.internal.reflect.Instantiator
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.NativeToolChain
import org.gradle.platform.base.internal.toolchain.ToolSearchResult
import javax.inject.Inject

class DefaultNativeToolChainRegistry @Inject constructor(instantiator: Instantiator, collectionCallbackActionDecorator: CollectionCallbackActionDecorator) :
    DefaultPolymorphicDomainObjectContainer<NativeToolChain?>(
        NativeToolChain::class.java, instantiator, instantiator, collectionCallbackActionDecorator
    ), NativeToolChainRegistryInternal {
    private val registeredDefaults: MutableMap<String, Class<out NativeToolChain?>?> = LinkedHashMap<String, Class<out NativeToolChain?>?>()
    private val searchOrder: MutableList<NativeToolChainInternal> = ArrayList<NativeToolChainInternal>()

    init {
        whenObjectAdded(object : Action<NativeToolChain?> {
            override fun execute(toolChain: NativeToolChain?) {
                searchOrder.add((toolChain as org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal?)!!)
            }
        })
        whenObjectRemoved(object : Action<NativeToolChain?> {
            override fun execute(toolChain: NativeToolChain?) {
                searchOrder.remove(toolChain)
            }
        })
    }

    override fun handleAttemptToAddItemWithNonUniqueName(toolChain: NativeToolChain) {
        throw InvalidUserDataException(String.format("ToolChain with name '%s' added multiple times", toolChain.getName()))
    }

    override fun registerDefaultToolChain(name: String?, type: Class<out NativeToolChain?>?) {
        registeredDefaults.put(name!!, type)
    }

    override fun addDefaultToolChains() {
        for (name in registeredDefaults.keys) {
            create(name, registeredDefaults.get(name))
        }
    }

    override fun getForPlatform(targetPlatform: NativePlatform?): NativeToolChain? {
        return getForPlatform(NativeLanguage.ANY, targetPlatform as NativePlatformInternal?)
    }

    override fun getForPlatform(sourceLanguage: NativeLanguage?, targetMachine: NativePlatformInternal): NativeToolChainInternal? {
        for (toolChain in searchOrder) {
            if (toolChain.select(sourceLanguage, targetMachine)!!.isAvailable) {
                return toolChain
            }
        }

        // No tool chains can build for this platform. Assemble a description of why
        val candidates: MutableMap<String?, PlatformToolProvider?> = LinkedHashMap<String?, PlatformToolProvider?>()
        for (toolChain in searchOrder) {
            candidates.put(toolChain.displayName, toolChain.select(sourceLanguage, targetMachine))
        }

        if (NativeLanguage.ANY != sourceLanguage && candidates.values.stream().allMatch { it: PlatformToolProvider? -> !it!!.isSupported }) {
            return UnsupportedNativeToolChain(UnsupportedToolChainDescription(sourceLanguage, targetMachine, candidates))
        }
        return UnavailableNativeToolChain(UnavailableToolChainDescription(sourceLanguage, targetMachine, candidates))
    }


    private abstract class AbstractUnavailabilityToolChainSearchDescription(
        private val sourceLanguage: NativeLanguage?,
        private val targetPlatform: NativePlatform,
        private val candidates: MutableMap<String?, PlatformToolProvider?>
    ) : ToolSearchResult {
        val isAvailable: Boolean
            get() = false

        override fun explain(visitor: DiagnosticsVisitor) {
            val verb = if (sourceLanguage === NativeLanguage.ANY) "build" else "build " + sourceLanguage
            visitor.node(java.lang.String.format("No tool chain %s to %s for %s", this.unavailabilityReason, verb, targetPlatform.displayName))
            visitor.startChildren()
            for (entry in candidates.entries) {
                visitor.node(entry.key)
                visitor.startChildren()
                entry.value!!.explain(visitor)
                visitor.endChildren()
            }
            if (candidates.isEmpty()) {
                visitor.node("No tool chain plugin applied.")
            }
            visitor.endChildren()
        }

        protected abstract val unavailabilityReason: String?
    }

    private class UnavailableToolChainDescription(sourceLanguage: NativeLanguage?, targetPlatform: NativePlatform, candidates: MutableMap<String?, PlatformToolProvider?>) :
        AbstractUnavailabilityToolChainSearchDescription(sourceLanguage, targetPlatform, candidates) {
        override fun getUnavailabilityReason(): String {
            return "is available"
        }
    }

    private class UnavailableNativeToolChain(private val failure: ToolSearchResult) : NativeToolChainInternal {
        val displayName: String
            get() = getName()

        override fun getName(): String {
            return "unavailable"
        }

        override fun select(targetPlatform: NativePlatformInternal): PlatformToolProvider {
            return UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), failure)
        }

        override fun select(sourceLanguage: NativeLanguage?, targetMachine: NativePlatformInternal): PlatformToolProvider {
            return select(targetMachine)
        }

        val outputType: String
            get() = "unavailable"

        override fun assertSupported() {
            // Supported, but unavailable. Nothing to do.
        }
    }

    private class UnsupportedToolChainDescription(sourceLanguage: NativeLanguage?, targetPlatform: NativePlatform, candidates: MutableMap<String?, PlatformToolProvider?>) :
        AbstractUnavailabilityToolChainSearchDescription(sourceLanguage, targetPlatform, candidates) {
        override fun getUnavailabilityReason(): String {
            return "has support"
        }
    }

    class UnsupportedNativeToolChain internal constructor(private val failure: ToolSearchResult) : NativeToolChainInternal {
        val displayName: String
            get() = getName()

        override fun getName(): String {
            return "unsupported"
        }

        override fun select(targetPlatform: NativePlatformInternal): PlatformToolProvider? {
            return UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), failure)
        }

        override fun select(sourceLanguage: NativeLanguage?, targetMachine: NativePlatformInternal): PlatformToolProvider? {
            return select(targetMachine)
        }

        val outputType: String?
            get() = "unsupported"

        override fun assertSupported() {
            val formatter = TreeFormatter()
            failure.explain(formatter)
            throw GradleException(formatter.toString())
        }
    }
}
