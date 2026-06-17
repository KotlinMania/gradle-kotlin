/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.catalog

import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.plugin.use.PluginDependency
import java.util.Optional

class VersionCatalogView(
    private val config: DefaultVersionCatalog,
    private val providerFactory: ProviderFactory,
    objects: ObjectFactory,
    attributesFactory: AttributesFactory,
    capabilityNotationParser: CapabilityNotationParser
) : VersionCatalog {
    private val dependencyFactory: ExternalModuleDependencyFactory
    private val bundleFactory: AbstractExternalDependencyFactory.BundleFactory

    init {
        this.dependencyFactory = DefaultExternalDependencyFactory(config, providerFactory, objects, attributesFactory, capabilityNotationParser)
        this.bundleFactory = AbstractExternalDependencyFactory.BundleFactory(objects, providerFactory, config, attributesFactory, capabilityNotationParser)
    }

    override fun findLibrary(alias: String): Optional<Provider<MinimalExternalModuleDependency>> {
        val normalizedAlias = AliasNormalizer.normalize(alias)
        if (config.hasDependency(normalizedAlias!!)) {
            return Optional.of<Provider<MinimalExternalModuleDependency>>(dependencyFactory.create(normalizedAlias))
        }
        return Optional.empty<Provider<MinimalExternalModuleDependency>>()
    }

    override fun findBundle(alias: String): Optional<Provider<ExternalModuleDependencyBundle>> {
        val normalizedBundle = AliasNormalizer.normalize(alias)
        if (config.hasBundle(normalizedBundle!!)) {
            return Optional.of<Provider<ExternalModuleDependencyBundle>>(bundleFactory.createBundle(normalizedBundle))
        }
        return Optional.empty<Provider<ExternalModuleDependencyBundle>>()
    }

    override fun findVersion(alias: String): Optional<VersionConstraint> {
        val normalizedName = AliasNormalizer.normalize(alias)
        if (config.hasVersion(normalizedName!!)) {
            return Optional.of<VersionConstraint>(AbstractExternalDependencyFactory.VersionFactory(providerFactory, config).findVersionConstraint(normalizedName))
        }
        return Optional.empty<VersionConstraint>()
    }

    override fun findPlugin(alias: String): Optional<Provider<PluginDependency>> {
        val normalizedAlias = AliasNormalizer.normalize(alias)
        if (config.hasPlugin(normalizedAlias!!)) {
            return Optional.of<Provider<PluginDependency>>(AbstractExternalDependencyFactory.PluginFactory(providerFactory, config).createPlugin(normalizedAlias))
        }
        return Optional.empty<Provider<PluginDependency>>()
    }

    override fun getName(): String {
        return config.getName()
    }

    override fun getLibraryAliases(): MutableList<String> {
        return config.getLibraryAliases()
    }

    override fun getBundleAliases(): MutableList<String> {
        return config.getBundleAliases()
    }

    override fun getVersionAliases(): MutableList<String> {
        return config.getVersionAliases()
    }

    override fun getPluginAliases(): MutableList<String> {
        return config.getPluginAliases()
    }
}
