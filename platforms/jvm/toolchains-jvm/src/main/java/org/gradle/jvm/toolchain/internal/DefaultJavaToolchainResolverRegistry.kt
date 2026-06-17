/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.jvm.toolchain.internal

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.services.BuildServiceSpec
import org.gradle.internal.authentication.AuthenticationSchemeRegistry
import org.gradle.internal.reflect.Instantiator
import org.gradle.jvm.toolchain.JavaToolchainRepository
import org.gradle.jvm.toolchain.JavaToolchainResolver
import javax.inject.Inject

abstract class DefaultJavaToolchainResolverRegistry @Inject constructor(
    gradle: Gradle,
    instantiator: Instantiator,
    objectFactory: ObjectFactory,
    providerFactory: ProviderFactory,
    authenticationSchemeRegistry: AuthenticationSchemeRegistry
) : JavaToolchainResolverRegistryInternal {
    private val sharedServices: BuildServiceRegistry

    private val repositoryHandler: DefaultJavaToolchainRepositoryHandler

    private val realizedRepositories: MutableList<RealizedJavaToolchainRepository> = ArrayList<RealizedJavaToolchainRepository>()

    private val registrations: MutableMap<Class<out JavaToolchainResolver>, Provider<out JavaToolchainResolver>> = HashMap<Class<out JavaToolchainResolver>, Provider<out JavaToolchainResolver>>()

    init {
        this.sharedServices = gradle.getSharedServices()
        this.repositoryHandler = objectFactory.newInstance<DefaultJavaToolchainRepositoryHandler>(
            DefaultJavaToolchainRepositoryHandler::class.java,
            instantiator,
            objectFactory,
            providerFactory,
            authenticationSchemeRegistry
        )
    }

    override fun getRepositories(): JavaToolchainRepositoryHandlerInternal {
        return repositoryHandler
    }

    override fun <T : JavaToolchainResolver?> register(implementationType: Class<T?>) {
        if (registrations.containsKey(implementationType)) {
            throw GradleException("Duplicate registration for '" + implementationType.getName() + "'.")
        }

        val provider = sharedServices.registerIfAbsent<T?, BuildServiceParameters.None>(implementationType.getName(), implementationType, EMPTY_CONFIGURE_ACTION)
        registrations.put(implementationType, provider)
    }

    override fun requestedRepositories(): MutableList<RealizedJavaToolchainRepository> {
        if (realizedRepositories.size != repositoryHandler.size()) {
            realizeRepositories()
        }
        return realizedRepositories
    }

    override fun preventFromFurtherMutation() {
        repositoryHandler.preventFromFurtherMutation()
        // This makes sure all configured elements have been transformed in their internal representation for later use
        realizeRepositories()
    }

    private fun realizeRepositories() {
        realizedRepositories.clear()

        val resolvers: MutableSet<Class<*>> = HashSet<Class<*>>()
        for (repository in repositoryHandler.getAsList()) {
            if (!resolvers.add(repository.resolverClass.get())) {
                throw GradleException("Duplicate configuration for repository implementation '" + repository.resolverClass.get().getName() + "'.")
            }
            realizedRepositories.add(realize(repository))
        }
    }

    private fun realize(repository: JavaToolchainRepository): RealizedJavaToolchainRepository {
        val repositoryClass: Class<out JavaToolchainResolver> = getResolverClass(repository)
        val provider = findProvider(repositoryClass)
        return RealizedJavaToolchainRepository(provider, repository as JavaToolchainRepositoryInternal)
    }

    private fun findProvider(repositoryClass: Class<out JavaToolchainResolver>): Provider<out JavaToolchainResolver> {
        val provider: Provider<out JavaToolchainResolver> = registrations.get(repositoryClass)!!
        if (provider == null) {
            throw GradleException("Class " + repositoryClass.getName() + " hasn't been registered as a Java toolchain repository")
        }
        return provider
    }

    companion object {
        private val EMPTY_CONFIGURE_ACTION = Action { buildServiceSpec: BuildServiceSpec<BuildServiceParameters.None> -> }

        private fun getResolverClass(repository: JavaToolchainRepository): Class<out JavaToolchainResolver> {
            val resolverClassProperty = repository.resolverClass
            resolverClassProperty.finalizeValueOnRead()
            if (!resolverClassProperty.isPresent()) {
                throw GradleException("Java toolchain repository `" + repository.name + "` must have the `resolverClass` property set")
            }
            return resolverClassProperty.get()
        }
    }
}
