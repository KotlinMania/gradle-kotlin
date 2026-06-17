/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.artifacts.LegacyConfiguration
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.internal.AbstractNamedDomainObjectContainer
import org.gradle.api.internal.AbstractValidatingNamedDomainObjectContainer
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.ConfigurationResolver
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.problems.ProblemId.Companion.create
import org.gradle.api.problems.internal.GradleCoreProblemGroup.configurationUsage
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.api.provider.Provider
import org.gradle.internal.Actions
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.reflect.Instantiator
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Function
import java.util.regex.Pattern
import javax.inject.Inject

// TODO: We should eventually consider making the DefaultConfigurationContainer extend DefaultPolymorphicDomainObjectContainer
class DefaultConfigurationContainer @Inject constructor(
    instantiator: Instantiator,
    callbackDecorator: CollectionCallbackActionDecorator,
    private val owner: DomainObjectContext,
    private val defaultConfigurationFactory: DefaultConfigurationFactory,
    private val resolutionStrategyFactory: ResolutionStrategyFactory,
    private val problemsService: ProblemsInternal,
    resolverFactory: ConfigurationResolver.Factory,
    schema: AttributesSchemaInternal
) : AbstractValidatingNamedDomainObjectContainer<Configuration>(Configuration::class.java, instantiator, Named.Namer.INSTANCE, callbackDecorator), ConfigurationContainerInternal {
    private val resolver: ConfigurationResolver

    private val detachedConfigurationDefaultNameCounter = AtomicInteger(1)

    init {
        this.resolver = resolverFactory.create(this, owner, schema)!!
    }

    @Suppress("deprecation")
    override fun doCreate(name: String): Configuration {
        // TODO: Deprecate legacy configurations for consumption
        validateNameIsAllowed(name)
        return defaultConfigurationFactory.create(name, false, resolver, resolutionStrategyFactory, ConfigurationRoles.ALL)
    }

    override fun createDomainObjectProvider(name: String, configurationAction: Action<in Configuration>?): NamedDomainObjectProvider<Configuration> {
        // Called by `register` for registering legacy configurations.
        // We override to set the public type to `LegacyConfiguration`,
        // allowing us to filter for unlocked configurations using `withType`

        assertElementNotPresent(name)
        val provider: NamedDomainObjectProvider<Configuration> = uncheckedCast<org.gradle.api.NamedDomainObjectProvider<org.gradle.api.artifacts.Configuration>?>(
            getInstantiator().newInstance<NamedDomainObjectCreatingProvider<*>>(
                AbstractNamedDomainObjectContainer.NamedDomainObjectCreatingProvider::class.java,
                this@DefaultConfigurationContainer,
                name,
                LegacyConfiguration::class.java,
                configurationAction
            )
        )
        doAddLater(provider)

        return provider
    }

    override fun visitConsumable(visitor: Consumer<ConfigurationInternal>) {
        // Copy and visit all configurations which are known to be consumable
        // We need to copy the configurations in case visiting the configuration causes more configurations to be realized.
        val availableConsumableConfigurations: MutableCollection<ConsumableConfiguration> = ArrayList<ConsumableConfiguration>(withType<ConsumableConfiguration>(ConsumableConfiguration::class.java))
        availableConsumableConfigurations.forEach(Consumer { configuration: ConsumableConfiguration? -> visitor.accept(configuration as ConfigurationInternal) }
        )

        // Then, copy and visit any configuration with unknown role, checking if it is consumable
        // We need to copy the configurations in case visiting the configuration causes more configurations to be realized.
        val availableLegacyConfigurations: MutableCollection<LegacyConfiguration> =
            ArrayList<LegacyConfiguration>(withType<LegacyConfiguration>(LegacyConfiguration::class.java).matching(org.gradle.api.specs.Spec { obj: LegacyConfiguration? -> obj!!.isCanBeConsumed() }))
        availableLegacyConfigurations.forEach(Consumer { configuration: LegacyConfiguration? ->
            visitor.accept(configuration as ConfigurationInternal)
        })
    }

    override fun findByName(name: String): ConfigurationInternal? {
        return super.findByName(name) as ConfigurationInternal?
    }

    override fun getByName(name: String): ConfigurationInternal {
        return super.getByName(name) as ConfigurationInternal
    }

    public override fun getTypeDisplayName(): String {
        return "configuration"
    }

    override fun createNotFoundException(name: String): UnknownDomainObjectException {
        return UnknownConfigurationException(String.format("Configuration with name '%s' not found.", name))
    }

    private fun failOnAttemptToAdd(behavior: String): RuntimeException? {
        val ex = GradleException(behavior)
        val id = create("method-not-allowed", "Method call not allowed", configurationUsage())
        throw problemsService.internalReporter!!.throwing(ex, id, { spec ->
            spec!!.contextualLabel(ex.message)
        })
    }

    override fun add(o: Configuration?): Boolean {
        throw failOnAttemptToAdd("Adding a configuration directly to the configuration container is not allowed.  Use a factory method instead to create a new configuration in the container.")
    }

    override fun addAll(c: MutableCollection<out Configuration>): Boolean {
        throw failOnAttemptToAdd("Adding a collection of configurations directly to the configuration container is not allowed.  Use a factory method instead to create a new configuration in the container.")
    }

    override fun addLater(provider: Provider<out Configuration>) {
        throw failOnAttemptToAdd("Adding a configuration provider directly to the configuration container is not allowed.  Use a factory method instead to create a new configuration in the container.")
    }

    override fun addAllLater(provider: Provider<out Iterable<Configuration>>) {
        throw failOnAttemptToAdd("Adding a provider of configurations directly to the configuration container is not allowed.  Use a factory method instead to create a new configuration in the container.")
    }

    override fun detachedConfiguration(vararg dependencies: Dependency): ConfigurationInternal {
        val name = nextDetachedConfigurationName()

        @Suppress("deprecation") val role = ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE
        val detachedConfiguration = defaultConfigurationFactory.create(
            name,
            true,
            resolver,
            resolutionStrategyFactory,
            role
        )
        copyAllTo(detachedConfiguration, dependencies)
        return detachedConfiguration
    }

    private fun nextDetachedConfigurationName(): String {
        return DETACHED_CONFIGURATION_DEFAULT_NAME + detachedConfigurationDefaultNameCounter.getAndIncrement()
    }

    private fun copyAllTo(detachedConfiguration: DefaultConfiguration, dependencies: Array<Dependency>) {
        val detachedDependencies: DomainObjectSet<Dependency> = detachedConfiguration.getDependencies()
        for (dependency in dependencies) {
            detachedDependencies.add(dependency.copy())
        }
    }

    override fun resolvable(name: String): NamedDomainObjectProvider<ResolvableConfiguration> {
        assertCanMutate("resolvable(String)")
        return registerConfiguration<ResolvableConfiguration>(
            name,
            ResolvableConfiguration::class.java,
            Function { name: String? -> this.doCreateResolvable(name!!) },
            Actions.doNothing<ResolvableConfiguration>()
        )
    }

    override fun resolvable(name: String, action: Action<in ResolvableConfiguration>): NamedDomainObjectProvider<ResolvableConfiguration> {
        assertCanMutate("resolvable(String, Action)")
        return registerConfiguration<ResolvableConfiguration>(name, ResolvableConfiguration::class.java, Function { name: String? -> this.doCreateResolvable(name!!) }, action)
    }

    override fun resolvableLocked(name: String): ResolvableConfiguration {
        assertCanMutate("resolvableLocked(String)")
        return createLockedConfiguration<DefaultResolvableConfiguration>(
            name,
            java.util.function.Function { name: kotlin.String? -> this.doCreateResolvable(name) },
            org.gradle.internal.Actions.doNothing<org.gradle.api.artifacts.Configuration>()
        )!!
    }

    override fun resolvableLocked(name: String, action: Action<in Configuration>): ResolvableConfiguration {
        assertCanMutate("resolvableLocked(String, Action)")
        return createLockedConfiguration<DefaultResolvableConfiguration>(
            name,
            java.util.function.Function { name: kotlin.String? -> this.doCreateResolvable(name) },
            action
        )!!
    }

    override fun consumable(name: String): NamedDomainObjectProvider<ConsumableConfiguration> {
        assertCanMutate("consumable(String)")
        return registerConfiguration<ConsumableConfiguration>(
            name,
            ConsumableConfiguration::class.java,
            Function { name: String? -> this.doCreateConsumable(name!!) },
            Actions.doNothing<ConsumableConfiguration>()
        )
    }

    override fun consumable(name: String, action: Action<in ConsumableConfiguration>): NamedDomainObjectProvider<ConsumableConfiguration> {
        assertCanMutate("consumable(String, Action)")
        return registerConfiguration<ConsumableConfiguration>(name, ConsumableConfiguration::class.java, Function { name: String? -> this.doCreateConsumable(name!!) }, action)
    }

    override fun dependencyScope(name: String): NamedDomainObjectProvider<DependencyScopeConfiguration> {
        assertCanMutate("dependencyScope(String)")
        return registerConfiguration<DependencyScopeConfiguration>(
            name,
            DependencyScopeConfiguration::class.java,
            Function { name: String? -> this.doCreateDependencyScope(name!!) },
            Actions.doNothing<DependencyScopeConfiguration>()
        )
    }

    override fun dependencyScope(name: String, action: Action<in DependencyScopeConfiguration>): NamedDomainObjectProvider<DependencyScopeConfiguration> {
        assertCanMutate("dependencyScope(String, Action)")
        return registerConfiguration<DependencyScopeConfiguration>(name, DependencyScopeConfiguration::class.java, Function { name: String? -> this.doCreateDependencyScope(name!!) }, action)
    }

    override fun dependencyScopeLocked(name: String): DefaultDependencyScopeConfiguration {
        assertCanMutate("dependencyScopeLocked(String)")
        return createLockedConfiguration<DefaultDependencyScopeConfiguration>(
            name,
            java.util.function.Function { name: kotlin.String? -> this.doCreateDependencyScope(name) },
            org.gradle.internal.Actions.doNothing<org.gradle.api.artifacts.Configuration>()
        )!!
    }

    override fun dependencyScopeLocked(name: String, action: Action<in Configuration>): DefaultDependencyScopeConfiguration {
        assertCanMutate("dependencyScopeLocked(String, Action)")
        return createLockedConfiguration<DefaultDependencyScopeConfiguration>(
            name,
            java.util.function.Function { name: kotlin.String? -> this.doCreateDependencyScope(name) },
            action
        )!!
    }

    @Deprecated("")
    override fun resolvableDependencyScopeLocked(name: String): Configuration {
        assertCanMutate("resolvableDependencyScopeLocked(String)")
        return createLockedLegacyConfiguration(name, ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE, Actions.doNothing<Configuration>())
    }

    @Deprecated("")
    override fun resolvableDependencyScopeLocked(name: String, action: Action<in Configuration>): Configuration {
        assertCanMutate("resolvableDependencyScopeLocked(String, Action)")
        return createLockedLegacyConfiguration(name, ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE, action)
    }

    override fun migratingLocked(name: String, role: ConfigurationRole): Configuration {
        assertCanMutate("migratingLocked(String, ConfigurationRole)")
        return migratingLocked(name, role, Actions.doNothing<Configuration>())
    }

    override fun migratingLocked(name: String, role: ConfigurationRole, action: Action<in Configuration>): Configuration {
        assertCanMutate("migratingLocked(String, ConfigurationRole, Action)")

        if (ConfigurationRolesForMigration.ALL.contains(role)) {
            return createLockedLegacyConfiguration(name, role, action)
        } else {
            throw InvalidUserDataException("Unknown migration role: " + role)
        }
    }

    override fun maybeCreateDependencyScopeLocked(name: String, verifyPrexisting: Boolean): Configuration {
        val conf = findByName(name)
        if (null != conf) {
            if (verifyPrexisting) {
                throw failOnReservedName(name)
            } else {
                // We should also prevent usage mutation here, but we can't because this would break
                // existing undeprecated behavior.
                // Introduce locking here in Gradle 9.x.
                return getByName(name)
            }
        } else {
            return dependencyScopeLocked(name)
        }
    }

    private fun failOnReservedName(confName: String): RuntimeException? {
        val ex = GradleException("The configuration " + confName + " was created explicitly. This configuration name is reserved for creation by Gradle.")
        val id = create("unexpected configuration usage", "Unexpected configuration usage", configurationUsage())
        throw problemsService.internalReporter!!.throwing(ex, id, { spec ->
            spec!!.contextualLabel(ex.message)
        })
    }

    private fun doCreateConsumable(name: String): DefaultConsumableConfiguration {
        return defaultConfigurationFactory.createConsumable(name, resolver, resolutionStrategyFactory)
    }

    private fun doCreateResolvable(name: String): DefaultResolvableConfiguration {
        return defaultConfigurationFactory.createResolvable(name, resolver, resolutionStrategyFactory)
    }

    private fun doCreateDependencyScope(name: String): DefaultDependencyScopeConfiguration {
        return defaultConfigurationFactory.createDependencyScope(name, resolver, resolutionStrategyFactory)
    }

    private fun createLockedLegacyConfiguration(name: String, role: ConfigurationRole, configureAction: Action<in Configuration>): ConfigurationInternal {
        return createLockedConfiguration<DefaultLegacyConfiguration>(
            name,
            java.util.function.Function { n: kotlin.String? -> defaultConfigurationFactory.create(n, false, resolver, resolutionStrategyFactory, role) },
            configureAction
        )!!
    }

    private fun <T : ConfigurationInternal?> createLockedConfiguration(name: String, factory: Function<String, T?>, configureAction: Action<in Configuration>): T? {
        assertElementNotPresent(name)
        validateNameIsAllowed(name)
        val configuration = factory.apply(name)
        super.add(configuration!!)
        configureAction.execute(configuration)
        configuration.preventUsageMutation()
        return configuration
    }

    private fun <T : Configuration?> registerConfiguration(name: String, publicType: Class<T?>, factory: Function<String, T?>, configureAction: Action<in T>): NamedDomainObjectProvider<T?> {
        assertElementNotPresent(name)
        validateNameIsAllowed(name)

        val configuration: NamedDomainObjectProvider<T?> = org.gradle.internal.Cast.uncheckedCast<NamedDomainObjectProvider<T?>?>(
            getInstantiator().newInstance<NamedDomainObjectCreatingProvider<*>>(
                org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer.NamedDomainObjectCreatingProvider::class.java,
                this,
                name,
                publicType,
                configureAction,
                factory
            )
        )!!
        doAddLater(configuration)
        return configuration
    }

    private fun validateNameIsAllowed(name: String) {
        if (RESERVED_NAMES_FOR_DETACHED_CONFS.matcher(name).matches()) {
            val ex =
                GradleException(String.format("Creating a configuration with a name that starts with 'detachedConfiguration' is not allowed.  Use a different name for the configuration '%s'", name))
            val id = create("name-not-allowed", "Configuration name not allowed", configurationUsage())
            throw problemsService.internalReporter!!.throwing(ex, id, { spec ->
                spec!!.contextualLabel(ex.message)
            })
        }
    }

    // Cannot be private due to reflective instantiation
    inner class NamedDomainObjectCreatingProvider<I : Configuration?>(name: String, type: Class<I?>, configureAction: Action<in I?>?, private val factory: Function<String, I?>) :
        AbstractDomainObjectCreatingProvider<I?>(name, type, configureAction) {
        override fun createDomainObject(): I? {
            return factory.apply(getName())
        }
    }

    override fun getDisplayName(): String {
        return "configuration container for " + owner.getDisplayName()
    }

    companion object {
        const val DETACHED_CONFIGURATION_DEFAULT_NAME: String = "detachedConfiguration"
        private val RESERVED_NAMES_FOR_DETACHED_CONFS: Pattern = Pattern.compile(DETACHED_CONFIGURATION_DEFAULT_NAME + "\\d*")
    }
}
