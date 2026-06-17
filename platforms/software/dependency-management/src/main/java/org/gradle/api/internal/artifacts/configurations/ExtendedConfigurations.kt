/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.DomainObjectCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.function.Function

/**
 * This class manages a set of ExtendedConfiguration instances, which can be backed by either realized configurations or provided configurations.
 * It handles correctly calling the validation action whenever a configuration is realized.  In other words, it ensures that the validation action
 * is always called whenever the configuration is realized and prevents access to the underlying provider which could potentially allow realizing
 * the configuration without validation.
 */
internal class ExtendedConfigurations(private val validationAction: Consumer<Configuration>, private val providerFactory: ProviderFactory) {
    private val configurations: MutableSet<ExtendedConfiguration> = LinkedHashSet<ExtendedConfiguration>()

    /**
     * Adds a realized configuration.
     */
    fun add(configuration: Configuration) {
        // For realized configurations, we can call the validation action immediately.
        validationAction.accept(configuration)
        configurations.add(RealizedExtendedConfiguration(configuration, providerFactory))
    }

    /**
     * Adds a provided configuration.  The validation action associated with this object will be called when the configuration is realized.
     */
    fun add(configurationProvider: Provider<out Configuration>) {
        configurations.add(ProvidedExtendedConfiguration(configurationProvider, validationAction, providerFactory))
    }

    val isEmpty: Boolean
        /**
         * Returns true if there are no extended configurations.
         */
        get() = configurations.isEmpty()

    /**
     * Visits all extended configurations.  Visiting an extended configuration does not realize it.
     */
    fun visitConfigurations(visitor: ExtendedConfiguration.Visitor) {
        configurations.forEach(Consumer { configuration: ExtendedConfiguration? -> visitor.visit(configuration!!) })
    }

    private class ProvidedExtendedConfiguration(
        private val provider: Provider<out Configuration>,
        private val validationAction: Consumer<Configuration>,
        private val providerFactory: ProviderFactory
    ) : ExtendedConfiguration {
        override fun get(): Configuration {
            // It's important that we call get() first, and then call the validation action, to ensure that the extended configuration graph is realized before validating.
            // We can't wrap validation in a provider since the validation action could realize other configurations, causing reentrant calls to get() in the
            // case where there is a cycle between configurations.  In other words, this order allows us to realize all extended configurations first, and then
            // discover and report cycles during the validation action.
            val configuration: Configuration = provider.get()
            validationAction.accept(configuration)
            return configuration
        }

        override fun <T> mapToCollection(configurationToCollection: Function<Configuration, DomainObjectCollection<T?>>): Provider<DomainObjectCollection<out T>> {
            return providerFactory.provider<DomainObjectCollection<out T>>(Callable { configurationToCollection.apply(get()) })
        }
    }

    private class RealizedExtendedConfiguration(private val configuration: Configuration, private val providerFactory: ProviderFactory) : ExtendedConfiguration {
        override fun get(): Configuration {
            return configuration
        }

        override fun <T> mapToCollection(configurationToCollection: Function<Configuration, DomainObjectCollection<T?>>): Provider<DomainObjectCollection<out T>> {
            return providerFactory.provider<DomainObjectCollection<out T>>(Callable { configurationToCollection.apply(configuration) })
        }
    }
}
