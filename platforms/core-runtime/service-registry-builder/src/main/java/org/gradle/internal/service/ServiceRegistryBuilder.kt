/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.service

import org.gradle.internal.service.ServiceRegistryBuilder.Companion.builder
import org.gradle.internal.service.ServiceRegistryFactory.create
import org.gradle.internal.service.scopes.Scope

/**
 * A builder for a [ServiceRegistry].
 *
 * <h2>Service lookup order</h2>
 *
 * How the service registry is built affects the lookup results in that registry.
 *
 *
 * **Own services** of a registry are services contributed by the [service providers][.provider].
 *
 *
 * **All services** of a registry are its own services and *all services* of its [parents][.parent].
 *
 *
 * The lookup order is the following:
 *
 *  1.  Own services of the current registry
 *  1.  All services of the first parent
 *  1.  All services of the second parent
 *  1.  ...
 *
 *
 * The lookup result in the *own services* does not depend on the order in which service providers were added.
 *
 *
 * If any service type is registered more than once within *own services*,
 * it will result in an ambiguity error *at the lookup time*.
 *
 * @see builder
 */
class ServiceRegistryBuilder private constructor() {
    private val parents: MutableList<ServiceRegistry?> = ArrayList<ServiceRegistry?>()
    private val providers: MutableList<ServiceRegistrationProvider> = ArrayList<ServiceRegistrationProvider>()
    private var displayName: String? = null
    private var scope: Class<out Scope>? = null
    private var strict = false

    /**
     * Sets the display name to be used by the service registry.
     *
     *
     * The display name is used for debugging and internal purposes.
     */
    fun displayName(displayName: String?): ServiceRegistryBuilder {
        this.displayName = displayName
        return this
    }

    /**
     * Adds a parent for the service registry.
     *
     *
     * There can be more than one parent and the order of parents **affects** the [lookup results][ServiceRegistryBuilder].
     */
    fun parent(parent: ServiceRegistry?): ServiceRegistryBuilder {
        this.parents.add(parent)
        return this
    }

    /**
     * Adds a service provider for the service registry.
     *
     *
     * There can be more than one provider and the order of service providers
     * does not affect [lookup results][ServiceRegistryBuilder].
     *
     *
     * Providers are examined for service declarations and service registration logic at the time of building the registry.
     * This implies that the `configure` methods will be executed before the registry is built.
     *
     * @see ServiceRegistrationProvider
     */
    fun provider(provider: ServiceRegistrationProvider?): ServiceRegistryBuilder {
        this.providers.add(provider!!)
        return this
    }

    /**
     * Adds a service provider for the service registry in the form of a registration action.
     *
     *
     * There can be more than one registration action and the order in which they register services
     * does not affect [lookup results][ServiceRegistryBuilder].
     *
     *
     * The registration action is executed at the time of building the registry.
     *
     * @see .provider
     * @see ServiceRegistrationAction
     */
    fun provider(register: ServiceRegistrationAction): ServiceRegistryBuilder {
        return provider(object : ServiceRegistrationProvider {
            @Suppress("unused")
            fun configure(registration: ServiceRegistration?) {
                register.registerServices(registration)
            }
        })
    }

    /**
     * Providing a scope makes the resulting [ServiceRegistry]
     * validate all registered services for being annotated with the given scope.
     *
     *
     * However, this still allows to register services without the
     * [@ServiceScope][org.gradle.internal.service.scopes.ServiceScope] annotation.
     *
     *
     * Only one scope can be specified. The last configured scope takes effect.
     *
     * @see .scopeStrictly
     */
    fun scope(scope: Class<out Scope?>?): ServiceRegistryBuilder {
        this.scope = scope
        this.strict = false
        return this
    }

    /**
     * Providing a scope makes the resulting [ServiceRegistry]
     * validate all registered services for being annotated with the given scope.
     *
     *
     * All registered services require the [@ServiceScope][org.gradle.internal.service.scopes.ServiceScope]
     * annotation to be present and contain the given scope.
     *
     *
     * Only one scope can be specified. The last configured scope takes effect.
     *
     * @see .scope
     */
    fun scopeStrictly(scope: Class<out Scope?>?): ServiceRegistryBuilder {
        this.scope = scope
        this.strict = true
        return this
    }

    /**
     * Creates a service registry with the provided configuration.
     *
     *
     * The registry **should be [closed][CloseableServiceRegistry.close]** when it is no longer required
     * to cleanly dispose of the resources potentially held by created services.
     *
     * @see CloseableServiceRegistry
     */
    fun build(): CloseableServiceRegistry {
        val parentRegistries = this.parents.filterNotNull().toTypedArray()
        val configuredScope = scope
        return create(configuredScope, strict, displayName, parentRegistries, providers)
    }

    companion object {
        /**
         * Creates a new builder.
         */
        @JvmStatic
        fun builder(): ServiceRegistryBuilder {
            return ServiceRegistryBuilder()
        }
    }
}
