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
package org.gradle.api.publish.internal.component

import com.google.common.collect.ImmutableSet
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.component.ConfigurationVariantDetails
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.internal.deprecation.Documentation.Companion.upgradeMinorGuide
import org.gradle.internal.exceptions.ResolutionProvider
import java.util.function.Consumer
import java.util.function.Supplier
import javax.inject.Inject

open class DefaultAdhocSoftwareComponent @Inject constructor(private val componentName: String, private val objectFactory: ObjectFactory) : AdhocComponentWithVariants, SoftwareComponentInternal {
    // Mutable state
    private val actions: MutableList<ConfigurationVariantAction>
    private var cachedVariants: ImmutableSet<UsageContext>? = null

    init {
        this.actions = ArrayList<ConfigurationVariantAction>()
    }

    override fun getName(): String {
        return componentName
    }

    override fun addVariantsFromConfiguration(outgoingConfiguration: Configuration, spec: Action<in ConfigurationVariantDetails>) {
        checkNotObserved()
        actions.add(ConfigurationVariantAction(Supplier { outgoingConfiguration }, spec, false))
    }

    override fun addVariantsFromConfiguration(outgoingConfiguration: Provider<ConsumableConfiguration>, action: Action<in ConfigurationVariantDetails>) {
        checkNotObserved()
        actions.add(ConfigurationVariantAction(Supplier { outgoingConfiguration.get() }, action, false))
    }

    override fun withVariantsFromConfiguration(outgoingConfiguration: Configuration, action: Action<in ConfigurationVariantDetails>) {
        checkNotObserved()
        actions.add(ConfigurationVariantAction(Supplier { outgoingConfiguration }, action, true))
    }

    override fun withVariantsFromConfiguration(outgoingConfiguration: Provider<ConsumableConfiguration>, action: Action<in ConfigurationVariantDetails>) {
        checkNotObserved()
        actions.add(ConfigurationVariantAction(Supplier { outgoingConfiguration.get() }, action, true))
    }

    override fun getUsages(): MutableSet<out UsageContext> {
        if (cachedVariants == null) {
            cachedVariants = computeVariants()
        }
        return cachedVariants!!
    }

    private fun computeVariants(): ImmutableSet<UsageContext> {
        val variants: MutableMap<Configuration, ConfigurationVariantMapping> = LinkedHashMap<Configuration, ConfigurationVariantMapping>(4)
        for (action in actions) {
            val configuration = action.getConfiguration()
            if (!action.isMutate) {
                variants.put(
                    configuration, ConfigurationVariantMapping(
                        configuration as ConfigurationInternal,
                        action.spec, objectFactory
                    )
                )
            } else {
                if (!variants.containsKey(configuration)) {
                    throw InvalidUserDataException(
                        "Variant for configuration '" + configuration.getName() + "' does not exist in component '" + componentName + "'. " +
                                "For a given configuration, 'addVariantsFromConfiguration' must be called before 'withVariantsFromConfiguration'."
                    )
                }
                variants.get(configuration)!!.addAction(action.spec)
            }
        }

        val builder = ImmutableSet.Builder<UsageContext>()
        for (variant in variants.values) {
            variant.collectVariants(Consumer { element: UsageContext? -> builder.add(element!!) })
        }
        return builder.build()
    }

    /**
     * Ensure this component cannot be modified after observation.
     *
     * @see [issue](https://github.com/gradle/gradle/issues/20581)
     */
    protected fun checkNotObserved() {
        if (cachedVariants != null) {
            throw MetadataModificationException("Gradle Module Metadata can't be modified after an eagerly populated publication.")
        }
    }

    class MetadataModificationException(message: String) : GradleException(message), ResolutionProvider {
        override fun getResolutions(): MutableList<String> {
            return mutableListOf<String>(upgradeMinorGuide(8, "gmm_modification_after_publication_populated").getConsultDocumentationMessage())
        }
    }

    private class ConfigurationVariantAction(private val configuration: Supplier<Configuration>, val spec: Action<in ConfigurationVariantDetails>, val isMutate: Boolean) {
        fun getConfiguration(): Configuration {
            return configuration.get()
        }
    }
}
