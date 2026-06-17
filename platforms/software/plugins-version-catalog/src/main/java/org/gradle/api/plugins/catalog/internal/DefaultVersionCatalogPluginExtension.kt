/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.catalog.internal

import com.google.common.collect.Interners
import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.catalog.DefaultVersionCatalog
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.util.concurrent.Callable
import java.util.function.Supplier
import javax.inject.Inject

class DefaultVersionCatalogPluginExtension @Inject constructor(objects: ObjectFactory, providers: ProviderFactory, drs: DependencyResolutionServices, dependenciesConfiguration: Configuration) :
    CatalogExtensionInternal {
    private val builder: DependenciesAwareVersionCatalogBuilder
    private val model: Provider<DefaultVersionCatalog>

    init {
        this.builder = objects.newInstance<DependenciesAwareVersionCatalogBuilder>(
            DependenciesAwareVersionCatalogBuilder::class.java,
            "versionCatalog",
            Interners.newStrongInterner<Any>(),
            Interners.newStrongInterner<Any>(),
            objects,
            Supplier { drs } as Supplier<DependencyResolutionServices?>,
            dependenciesConfiguration
        )
        this.model = providers.provider<DefaultVersionCatalog>(Callable { builder.build() })
    }

    override fun versionCatalog(spec: Action<in VersionCatalogBuilder>) {
        spec.execute(builder)
    }

    override fun configureExplicitAlias(alias: String, group: String, name: String) {
        builder.configureExplicitAlias(DefaultModuleIdentifier.newId(group, name), alias)
    }

    override fun getVersionCatalog(): Provider<DefaultVersionCatalog> {
        return model
    }
}
