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
package org.gradle.api.internal.artifacts.configurations

import com.google.common.collect.ListMultimap
import com.google.common.collect.MultimapBuilder
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.external.model.ImmutableCapabilities.Companion.of
import org.gradle.internal.component.external.model.ProjectDerivedCapability
import org.gradle.internal.deprecation.DocumentedFailure.builder
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 * Static utility to verify a set of variants each have a unique identity in terms of attributes and capabilities.
 */
object VariantIdentityUniquenessVerifier {
    /**
     * Build a report of all possible variant uniqueness failures for the given configurations.
     */
    @JvmStatic
    fun buildReport(configurations: ConfigurationsProvider): VerificationReport {
        val byIdentity =
            MultimapBuilder.linkedHashKeys().arrayListValues().build<VariantIdentity, ConfigurationInternal>()

        configurations.visitConsumable(Consumer { configuration: ConfigurationInternal? ->
            if (!VariantIdentityUniquenessVerifier.mustHaveUniqueAttributes(configuration!!)) {
                return@visitConsumable
            }
            byIdentity.put(VariantIdentity.Companion.from(configuration), configuration)
        })

        return VariantIdentityUniquenessVerifier.VerificationReport(byIdentity)
    }

    /**
     * Consumable, non-resolvable, non-default configurations with attributes must have unique attributes.
     */
    private fun mustHaveUniqueAttributes(configuration: Configuration): Boolean {
        return !configuration.isCanBeResolved() && (Dependency.DEFAULT_CONFIGURATION != configuration.getName()) && !configuration.getAttributes().isEmpty()
    }

    /**
     * A report tracking all possible variant uniqueness failures for a component.
     */
    class VerificationReport private constructor(private val byIdentity: ListMultimap<VariantIdentity, ConfigurationInternal>) {
        /**
         * Get a failure that only checks variant uniqueness for the given configuration.
         */
        fun failureFor(configuration: ConfigurationInternal, withTaskAdvice: Boolean): GradleException? {
            val collisions =
                byIdentity.get(VariantIdentity.Companion.from(configuration)).stream()
                    .filter { it: ConfigurationInternal? -> it!!.getName() != configuration.getName() }
                    .collect(Collectors.toList())

            if (collisions.isEmpty()) {
                return null
            }

            return buildFailure(configuration, withTaskAdvice, collisions)
        }

        /**
         * Throw an exception if any variants have conflicting identities.
         */
        fun assertNoConflicts() {
            for (identity in byIdentity.keySet()) {
                val collisions = byIdentity.get(identity)
                if (collisions.size > 1) {
                    val configuration = collisions.get(0)
                    val filtered =
                        byIdentity.get(identity).stream()
                            .filter { it: ConfigurationInternal? -> it!!.getName() != configuration.getName() }
                            .collect(Collectors.toList())

                    throw buildFailure(configuration, true, filtered)
                }
            }
        }

        companion object {
            private fun buildFailure(
                configuration: ConfigurationInternal,
                withTaskAdvice: Boolean,
                collisions: MutableList<ConfigurationInternal>
            ): GradleException {
                val builder = builder()
                var advice = "Consider adding an additional attribute to one of the configurations to disambiguate them."
                if (withTaskAdvice) {
                    advice += "  Run the 'outgoingVariants' task for more details."
                }

                val message = "Consumable configurations with identical capabilities within a project (other than the default configuration) " +
                        "must have unique attributes, but " + configuration.displayName + " and " + collisions + " contain identical attribute sets."

                return builder.withSummary(message)
                    .withAdvice(advice)
                    .withUserManual("upgrading_version_7", "unique_attribute_sets")!!
                    .build()
            }
        }
    }

    /**
     * The identity of a variant -- its attributes and capabilities.
     */
    private class VariantIdentity(private val attributes: ImmutableAttributes, private val capabilities: ImmutableCapabilities) {
        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as VariantIdentity
            return attributes == that.attributes &&
                    capabilities == that.capabilities
        }

        override fun hashCode(): Int {
            return attributes.hashCode() xor capabilities.hashCode()
        }

        companion object {
            fun from(configuration: ConfigurationInternal): VariantIdentity {
                return VariantIdentity(
                    configuration.getAttributes()!!.asImmutable(),
                    allCapabilitiesIncludingDefault(configuration)
                )
            }

            private fun allCapabilitiesIncludingDefault(conf: ConfigurationInternal): ImmutableCapabilities {
                val declaredCapabilities = conf.getOutgoing().getCapabilities()
                if (!declaredCapabilities.isEmpty()) {
                    return of(declaredCapabilities)!!
                }

                // If no capabilities are declared, use the implicit capability.
                val project: ProjectInternal? = conf.domainObjectContext.getProject()
                if (project == null) {
                    return ImmutableCapabilities.EMPTY
                }
                return of(ProjectDerivedCapability(project))!!
            }
        }
    }
}
