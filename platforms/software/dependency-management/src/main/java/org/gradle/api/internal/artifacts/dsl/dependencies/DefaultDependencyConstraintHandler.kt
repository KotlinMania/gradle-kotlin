/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl.dependencies

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Transformer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalModuleDependencyBundle
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.internal.artifacts.dependencies.DependencyConstraintInternal
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.metaobject.MethodAccess
import org.gradle.internal.metaobject.MethodMixIn
import org.gradle.util.internal.ConfigureUtil

class DefaultDependencyConstraintHandler(
    private val configurationContainer: ConfigurationContainer,
    private val dependencyConstraintFactory: DependencyConstraintFactoryInternal,
    private val platformSupport: PlatformSupport
) : DependencyConstraintHandler, MethodMixIn {
    private val dynamicMethods: DynamicAddDependencyMethods

    init {
        this.dynamicMethods = DynamicAddDependencyMethods(configurationContainer, DefaultDependencyConstraintHandler.DependencyConstraintAdder())
    }

    override fun add(configurationName: String, dependencyNotation: Any): DependencyConstraint {
        return doAdd(configurationContainer.getByName(configurationName), dependencyNotation, null)
    }

    override fun add(configurationName: String, dependencyNotation: Any, configureAction: Action<in DependencyConstraint>): DependencyConstraint {
        return doAdd(configurationContainer.getByName(configurationName), dependencyNotation, configureAction)
    }

    override fun <T> addProvider(configurationName: String, dependencyNotation: Provider<T?>) {
        doAddProvider(configurationContainer.getByName(configurationName), dependencyNotation, null)
    }

    override fun <T> addProvider(configurationName: String, dependencyNotation: Provider<T?>, configureAction: Action<in DependencyConstraint>) {
        doAddProvider(configurationContainer.getByName(configurationName), dependencyNotation, configureAction)
    }

    override fun <T> addProviderConvertible(configurationName: String, dependencyNotation: ProviderConvertible<T?>) {
        doAddProvider(configurationContainer.getByName(configurationName), dependencyNotation.asProvider(), null)
    }

    override fun <T> addProviderConvertible(configurationName: String, dependencyNotation: ProviderConvertible<T?>, configureAction: Action<in DependencyConstraint>) {
        doAddProvider(configurationContainer.getByName(configurationName), dependencyNotation.asProvider(), configureAction)
    }

    override fun create(dependencyNotation: Any): DependencyConstraint {
        return doCreate(dependencyNotation, null)
    }

    override fun create(dependencyNotation: Any, configureAction: Action<in DependencyConstraint>): DependencyConstraint {
        return doCreate(dependencyNotation, configureAction)
    }

    override fun enforcedPlatform(notation: Any): DependencyConstraint {
        val platformDependency = create(notation) as DependencyConstraintInternal
        platformDependency.setForce(true)
        platformSupport.addPlatformAttribute<DependencyConstraint>(platformDependency, Category.ENFORCED_PLATFORM)
        return platformDependency
    }

    override fun enforcedPlatform(notation: Any, configureAction: Action<in DependencyConstraint>): DependencyConstraint {
        val dep = enforcedPlatform(notation)
        configureAction.execute(dep)
        return dep
    }

    private fun doCreate(dependencyNotation: Any, configureAction: Action<in DependencyConstraint>?): DependencyConstraint {
        val dependencyConstraint = dependencyConstraintFactory.createDependencyConstraint(dependencyNotation)
        if (configureAction != null) {
            configureAction.execute(dependencyConstraint)
        }
        return dependencyConstraint
    }

    private fun doAdd(configuration: Configuration, dependencyNotation: Any, configureAction: Action<in DependencyConstraint>?): DependencyConstraint {
        if (dependencyNotation is ProviderConvertible<*>) {
            return doAddProvider(configuration, dependencyNotation.asProvider(), configureAction)
        }
        if (dependencyNotation is Provider<*>) {
            return doAddProvider(configuration, dependencyNotation, configureAction)
        }
        val dependency = doCreate(dependencyNotation, configureAction)
        configuration.getDependencyConstraints().add(dependency)
        return dependency
    }

    private fun doAddProvider(configuration: Configuration, dependencyNotation: Provider<*>, configureAction: Action<in DependencyConstraint>?): DependencyConstraint {
        if (dependencyNotation is ProviderInternal<*>) {
            val provider = dependencyNotation
            if (provider.getType() != null && ExternalModuleDependencyBundle::class.java.isAssignableFrom(provider.getType())) {
                val bundle: ExternalModuleDependencyBundle = uncheckedCast<ExternalModuleDependencyBundle?>(provider.get())!!
                for (dependency in bundle) {
                    doAdd(configuration, dependency, configureAction)
                }
                return DUMMY_CONSTRAINT
            }
        }
        val lazyConstraint = dependencyNotation.map<DependencyConstraint>(mapDependencyConstraintProvider(configureAction))
        configuration.getDependencyConstraints().addLater(lazyConstraint)
        // Return a fake dependency constraint object to satisfy Kotlin DSL backwards compatibility
        return DUMMY_CONSTRAINT
    }

    private fun <T> mapDependencyConstraintProvider(configurationAction: Action<in DependencyConstraint>?): Transformer<DependencyConstraint, T?> {
        return Transformer { lazyNotation: T? -> doCreate(lazyNotation!!, configurationAction) }
    }

    override fun getAdditionalMethods(): MethodAccess {
        return dynamicMethods
    }

    private inner class DependencyConstraintAdder : DynamicAddDependencyMethods.DependencyAdder<DependencyConstraint?> {
        override fun add(configuration: Configuration, dependencyNotation: Any, configureClosure: Closure<*>): DependencyConstraint {
            if (dependencyNotation is ProviderConvertible<*>) {
                return doAddProvider(configuration, dependencyNotation.asProvider(), ConfigureUtil.configureUsing<DependencyConstraint>(configureClosure))
            }
            if (dependencyNotation is Provider<*>) {
                return doAddProvider(configuration, dependencyNotation, ConfigureUtil.configureUsing<DependencyConstraint>(configureClosure))
            }
            val dependencyConstraint = ConfigureUtil.configure<DependencyConstraint>(configureClosure, dependencyConstraintFactory.createDependencyConstraint(dependencyNotation))
            configuration.getDependencyConstraints().add(dependencyConstraint)
            return dependencyConstraint
        }
    }

    companion object {
        private val DUMMY_CONSTRAINT: DependencyConstraint = object : DependencyConstraint {
            private fun shouldNotBeCalled(): InvalidUserCodeException {
                return InvalidUserCodeException("You shouldn't use a dependency constraint created via a Provider directly")
            }

            override fun version(configureAction: Action<in MutableVersionConstraint>) {
                throw shouldNotBeCalled()
            }

            override fun getReason(): String? {
                throw shouldNotBeCalled()
            }

            override fun because(reason: String?) {
                throw shouldNotBeCalled()
            }

            override fun getAttributes(): AttributeContainer? {
                throw shouldNotBeCalled()
            }

            override fun attributes(configureAction: Action<in AttributeContainer>): DependencyConstraint? {
                throw shouldNotBeCalled()
            }

            override fun getVersionConstraint(): VersionConstraint? {
                throw shouldNotBeCalled()
            }

            override fun getGroup(): String? {
                throw shouldNotBeCalled()
            }

            override fun getName(): String? {
                throw shouldNotBeCalled()
            }

            override fun getVersion(): String? {
                throw shouldNotBeCalled()
            }

            override fun matchesStrictly(identifier: ModuleVersionIdentifier): Boolean {
                throw shouldNotBeCalled()
            }

            override fun getModule(): ModuleIdentifier? {
                throw shouldNotBeCalled()
            }
        }
    }
}
