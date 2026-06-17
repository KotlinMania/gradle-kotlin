/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.component.resolution.failure

import org.gradle.internal.component.resolution.failure.describer.AmbiguousArtifactTransformsFailureDescriber
import org.gradle.internal.component.resolution.failure.describer.AmbiguousArtifactsFailureDescriber
import org.gradle.internal.component.resolution.failure.describer.AmbiguousVariantsFailureDescriber
import org.gradle.internal.component.resolution.failure.describer.ConfigurationDoesNotExistFailureDescriber
import org.gradle.internal.component.resolution.failure.describer.ConfigurationNotCompatibleFailureDescriber
import org.gradle.internal.component.resolution.failure.describer.IncompatibleMultipleNodesValidationFailureDescriber
import org.gradle.internal.component.resolution.failure.describer.MissingAttributeAmbiguousVariantsFailureDescriber
import org.gradle.internal.component.resolution.failure.describer.ModuleRejectedFailureDescriber
import org.gradle.internal.component.resolution.failure.describer.ModuleRejectedIncompatibleConstraintsFailureDescriber
import org.gradle.internal.component.resolution.failure.describer.NoCompatibleArtifactFailureDescriber
import org.gradle.internal.component.resolution.failure.describer.NoCompatibleVariantsFailureDescriber
import org.gradle.internal.component.resolution.failure.describer.NoVariantsWithMatchingCapabilitiesFailureDescriber
import org.gradle.internal.component.resolution.failure.describer.ResolutionFailureDescriber
import org.gradle.internal.component.resolution.failure.describer.UnknownArtifactSelectionFailureDescriber
import org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure
import org.gradle.internal.component.resolution.failure.type.AmbiguousArtifactTransformsFailure
import org.gradle.internal.component.resolution.failure.type.AmbiguousArtifactsFailure
import org.gradle.internal.component.resolution.failure.type.AmbiguousVariantsFailure
import org.gradle.internal.component.resolution.failure.type.ConfigurationDoesNotExistFailure
import org.gradle.internal.component.resolution.failure.type.ConfigurationNotCompatibleFailure
import org.gradle.internal.component.resolution.failure.type.IncompatibleMultipleNodesValidationFailure
import org.gradle.internal.component.resolution.failure.type.ModuleRejectedFailure
import org.gradle.internal.component.resolution.failure.type.NoCompatibleArtifactFailure
import org.gradle.internal.component.resolution.failure.type.NoCompatibleVariantsFailure
import org.gradle.internal.component.resolution.failure.type.NoVariantsWithMatchingCapabilitiesFailure
import org.gradle.internal.component.resolution.failure.type.UnknownArtifactSelectionFailure
import org.gradle.internal.instantiation.InstanceGenerator
import java.util.function.Consumer

/**
 * An ordered registry of [ResolutionFailureDescriber] instances that can be queried
 * by the [ResolutionFailure] type they can describe.
 */
class ResolutionFailureDescriberRegistry private constructor(private val instanceGenerator: InstanceGenerator) {
    private val describers = LinkedHashMap<Class<out ResolutionFailure>, MutableList<ResolutionFailureDescriber<*>>>()

    /**
     * Returns the list of [ResolutionFailureDescriber]s registered for the given [ResolutionFailure] type.
     *
     * @param failureType The type of failure to describe
     * @param <FAILURE> The type of failure to describe
     * @return The list of describers registered for the given failure type
    </FAILURE> */
    fun <FAILURE : ResolutionFailure?> getDescribers(failureType: Class<FAILURE?>): MutableList<ResolutionFailureDescriber<FAILURE?>> {
        val result: MutableList<ResolutionFailureDescriber<FAILURE?>> = ArrayList<ResolutionFailureDescriber<FAILURE?>>()
        describers.getOrDefault(failureType, mutableListOf<ResolutionFailureDescriber<*>>()).forEach(Consumer { d: ResolutionFailureDescriber<*>? ->
            val typedDescriber = d as ResolutionFailureDescriber<FAILURE?>
            result.add(typedDescriber)
        })
        return result
    }

    /**
     * Adds a [ResolutionFailureDescriber] to the custom describers
     * contained in this registry for the given [ResolutionFailure] type.
     *
     * @param failureType The type of failure to describe
     * @param describerType A describer that can potentially describe failures of the given type
     * @param <FAILURE> The type of failure to describe
    </FAILURE> */
    fun <FAILURE : ResolutionFailure?> registerDescriber(failureType: Class<FAILURE?>, describerType: Class<out ResolutionFailureDescriber<FAILURE?>>) {
        val describer: ResolutionFailureDescriber<*> = instanceGenerator.newInstance(describerType)
        describers.computeIfAbsent(failureType) { k: Class<out ResolutionFailure?>? -> ArrayList<ResolutionFailureDescriber<*>?>() }.add(describer)
    }

    companion object {
        /**
         * Creates a new, empty registry of [ResolutionFailureDescriber]s.
         *
         * @param instanceGenerator The instance generator to use to create describers
         * @return a new, empty registry instance
         */
        fun emptyRegistry(instanceGenerator: InstanceGenerator): ResolutionFailureDescriberRegistry {
            return ResolutionFailureDescriberRegistry(instanceGenerator)
        }

        /**
         * Creates a new, registry of [ResolutionFailureDescriber]s containing the default internal list of describers
         * that can describe the [complete set][org.gradle.internal.component.resolution.failure.interfaces] of [ResolutionFailure]
         * types used by Gradle.
         *
         *
         * This list should be ordered according to the order in which the failures can occur during the resolution process.
         *
         * @param instanceGenerator The instance generator to use to create describers
         * @return a new registry instance with the default describers registered
         */
        fun standardRegistry(instanceGenerator: InstanceGenerator): ResolutionFailureDescriberRegistry {
            val registry = ResolutionFailureDescriberRegistry(instanceGenerator)

            // Component Selection failure
            registry.registerDescriber<ModuleRejectedFailure>(ModuleRejectedFailure::class.java, ModuleRejectedIncompatibleConstraintsFailureDescriber::class.java)
            registry.registerDescriber<ModuleRejectedFailure>(ModuleRejectedFailure::class.java, ModuleRejectedFailureDescriber::class.java)

            // Variant Selection failure
            registry.registerDescriber<AmbiguousVariantsFailure>(
                AmbiguousVariantsFailure::class.java,
                MissingAttributeAmbiguousVariantsFailureDescriber::class.java
            ) // Added ahead of AmbiguousVariantsFailureDescriber so the more specific ambiguity case is checked first
            registry.registerDescriber<AmbiguousVariantsFailure>(AmbiguousVariantsFailure::class.java, AmbiguousVariantsFailureDescriber::class.java)
            registry.registerDescriber<NoCompatibleVariantsFailure>(NoCompatibleVariantsFailure::class.java, NoCompatibleVariantsFailureDescriber::class.java)
            registry.registerDescriber<ConfigurationNotCompatibleFailure>(ConfigurationNotCompatibleFailure::class.java, ConfigurationNotCompatibleFailureDescriber::class.java)
            registry.registerDescriber<ConfigurationDoesNotExistFailure>(ConfigurationDoesNotExistFailure::class.java, ConfigurationDoesNotExistFailureDescriber::class.java)

            // Graph Validation failures
            registry.registerDescriber<NoVariantsWithMatchingCapabilitiesFailure>(NoVariantsWithMatchingCapabilitiesFailure::class.java, NoVariantsWithMatchingCapabilitiesFailureDescriber::class.java)

            // Artifact Selection failures
            registry.registerDescriber<AmbiguousArtifactsFailure>(AmbiguousArtifactsFailure::class.java, AmbiguousArtifactsFailureDescriber::class.java)
            registry.registerDescriber<NoCompatibleArtifactFailure>(NoCompatibleArtifactFailure::class.java, NoCompatibleArtifactFailureDescriber::class.java)
            registry.registerDescriber<IncompatibleMultipleNodesValidationFailure>(
                IncompatibleMultipleNodesValidationFailure::class.java,
                IncompatibleMultipleNodesValidationFailureDescriber::class.java
            )
            registry.registerDescriber<AmbiguousArtifactTransformsFailure>(AmbiguousArtifactTransformsFailure::class.java, AmbiguousArtifactTransformsFailureDescriber::class.java)
            registry.registerDescriber<UnknownArtifactSelectionFailure>(UnknownArtifactSelectionFailure::class.java, UnknownArtifactSelectionFailureDescriber::class.java)

            return registry
        }
    }
}
