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

import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultMinimalDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.provider.ValueSourceSpec
import org.gradle.plugin.use.PluginDependency
import java.util.concurrent.Callable
import java.util.function.Supplier
import java.util.stream.Collectors
import javax.inject.Inject

abstract class AbstractExternalDependencyFactory @Inject protected constructor(
    protected val config: DefaultVersionCatalog,
    protected val providers: ProviderFactory,
    protected val objects: ObjectFactory,
    protected val attributesFactory: AttributesFactory,
    protected val capabilityNotationParser: CapabilityNotationParser
) : ExternalModuleDependencyFactory {
    @Suppress("unused")
    abstract class SubDependencyFactory protected constructor(protected val owner: AbstractExternalDependencyFactory) : ExternalModuleDependencyFactory {
        override fun create(alias: String): Provider<MinimalExternalModuleDependency> {
            return owner.create(alias)
        }
    }

    override fun create(alias: String): Provider<MinimalExternalModuleDependency> {
        return providers.of<DependencyModel, DependencyValueSource.Params>(
            DependencyValueSource::class.java,
            Action { spec: ValueSourceSpec<DependencyValueSource.Params>? -> spec!!.getParameters().getDependencyData().set(config.getDependencyData(alias)) }
        ).map<MinimalExternalModuleDependency>(object : Transformer<MinimalExternalModuleDependency, DependencyModel> {
            override fun transform(x: DependencyModel): MinimalExternalModuleDependency {
                return createMinimalDependency(x, attributesFactory, capabilityNotationParser, objects)
            }
        })
    }

    class VersionFactory(protected val providers: ProviderFactory, protected val config: DefaultVersionCatalog) {
        /**
         * Returns a single version string from a rich version
         * constraint, assuming the user knows what they are doing.
         *
         * @param name the name of the version alias
         * @return a single version string or an empty string
         */
        protected fun getVersion(name: String): Provider<String> {
            return providers.provider<String>(Callable { doGetVersion(name) })
        }

        private fun doGetVersion(name: String): String {
            val version = findVersionConstraint(name)
            val requiredVersion = version.getRequiredVersion()
            if (!requiredVersion.isEmpty()) {
                return requiredVersion
            }
            val strictVersion = version.getStrictVersion()
            if (!strictVersion.isEmpty()) {
                return strictVersion
            }
            return version.getPreferredVersion()
        }

        fun findVersionConstraint(name: String): ImmutableVersionConstraint {
            return config.getVersion(name).getVersion()
        }
    }

    class BundleFactory(
        protected val objects: ObjectFactory,
        protected val providers: ProviderFactory,
        protected val config: DefaultVersionCatalog,
        protected val attributesFactory: AttributesFactory,
        protected val capabilityNotationParser: CapabilityNotationParser
    ) {
        fun createBundle(name: String): Provider<ExternalModuleDependencyBundle> {
            val property = objects.property<ExternalModuleDependencyBundle>(ExternalModuleDependencyBundle::class.java)
            property.convention(
                providers.of<MutableList<DependencyModel>, DependencyBundleValueSource.Params>(
                    DependencyBundleValueSource::class.java,
                    Action { spec: ValueSourceSpec<DependencyBundleValueSource.Params>? ->
                        spec!!.parameters(Action { params: DependencyBundleValueSource.Params? ->
                            params!!.getConfig().set(config)
                            params.getBundleName().set(name)
                        })
                    }
                ).map<DefaultExternalModuleDependencyBundle>(Transformer { dataList: MutableList<DependencyModel?>? ->
                    dataList!!.stream()
                        .map<DefaultMinimalDependency> { x: DependencyModel? -> Companion.createMinimalDependency(x!!, attributesFactory, capabilityNotationParser, objects) }
                        .collect(Collectors.toCollection(Supplier { DefaultExternalModuleDependencyBundle() }))
                })
            )
            return property
        }
    }

    class PluginFactory(protected val providers: ProviderFactory, protected val config: DefaultVersionCatalog) {
        fun createPlugin(name: String): Provider<PluginDependency> {
            return providers.of<PluginDependency, PluginDependencyValueSource.Params>(
                PluginDependencyValueSource::class.java,
                Action { spec: ValueSourceSpec<PluginDependencyValueSource.Params>? ->
                    spec!!.parameters(Action { params: PluginDependencyValueSource.Params? ->
                        params!!.getConfig().set(config)
                        params.getPluginName().set(name)
                    })
                })
        }
    }

    companion object {
        private fun createMinimalDependency(
            data: DependencyModel,
            attributesFactory: AttributesFactory,
            capabilityNotationParser: CapabilityNotationParser,
            objectFactory: ObjectFactory
        ): DefaultMinimalDependency {
            val version = data.getVersion()
            val result = DefaultMinimalDependency(
                DefaultModuleIdentifier.newId(data.getGroup(), data.getName()), DefaultMutableVersionConstraint(version)
            )
            result.setAttributesFactory(attributesFactory)
            result.setCapabilityNotationParser(capabilityNotationParser)
            result.setObjectFactory(objectFactory)
            return result
        }
    }
}
