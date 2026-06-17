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
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.Namer
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.credentials.Credentials
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.DefaultNamedDomainObjectList
import org.gradle.api.internal.artifacts.repositories.AuthenticationSupporter
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.authentication.Authentication
import org.gradle.internal.authentication.AuthenticationSchemeRegistry
import org.gradle.internal.authentication.DefaultAuthenticationContainer
import org.gradle.internal.reflect.Instantiator
import org.gradle.jvm.toolchain.JavaToolchainRepository
import org.gradle.jvm.toolchain.JavaToolchainResolver
import java.util.Collections
import java.util.function.Supplier
import java.util.stream.Collectors
import javax.inject.Inject

class DefaultJavaToolchainRepositoryHandler @Inject constructor(
    private val instantiator: Instantiator,
    private val objectFactory: ObjectFactory,
    private val providerFactory: ProviderFactory,
    private val authenticationSchemeRegistry: AuthenticationSchemeRegistry
) : JavaToolchainRepositoryHandlerInternal {
    private val repositories: DefaultNamedDomainObjectList<JavaToolchainRepository>

    private var mutable = true

    init {
        this.repositories = object : DefaultNamedDomainObjectList<JavaToolchainRepository>(
            JavaToolchainRepository::class.java,
            instantiator, RepositoryNamer(), CollectionCallbackActionDecorator.NOOP
        ) {
            public override fun getTypeDisplayName(): String {
                return "repository"
            }
        }
    }

    private class RepositoryNamer : Namer<JavaToolchainRepository> {
        override fun determineName(repository: JavaToolchainRepository): String {
            return repository.name
        }
    }

    override fun repository(name: String, configureAction: Action<in JavaToolchainRepository>) {
        assertMutable()

        val authenticationContainer = DefaultAuthenticationContainer(instantiator, CollectionCallbackActionDecorator.NOOP)
        for (e in authenticationSchemeRegistry.getRegisteredSchemes<Authentication>().entries) {
            authenticationContainer.registerBinding<Authentication>(e.key, e.value)
        }
        val authenticationSupporter = AuthenticationSupporter(instantiator, objectFactory, authenticationContainer, providerFactory)

        val repository = objectFactory.newInstance<DefaultJavaToolchainRepository>(DefaultJavaToolchainRepository::class.java, name, authenticationContainer, authenticationSupporter, providerFactory)
        configureAction.execute(repository)

        val isNew = repositories.add(repository)
        if (!isNew) {
            throw GradleException("Duplicate configuration for repository '" + name + "'.")
        }
    }

    override fun getAsList(): MutableList<JavaToolchainRepository> {
        val copy: java.util.ArrayList<JavaToolchainRepository> = repositories.stream()
            .map<JavaToolchainRepositoryInternal> { it: JavaToolchainRepository? -> it as JavaToolchainRepositoryInternal }
            .map<ImmutableJavaToolchainRepository> { delegate: JavaToolchainRepositoryInternal? -> ImmutableJavaToolchainRepository(delegate!!) }
            .collect(Collectors.toCollection(Supplier { ArrayList() }))
        return Collections.unmodifiableList<JavaToolchainRepository>(copy)
    }

    override fun size(): Int {
        return repositories.size
    }

    override fun remove(name: String): Boolean {
        assertMutable()

        val repository = repositories.findByName(name)
        if (repository == null) {
            return false
        }

        return repositories.remove(repository)
    }

    override fun preventFromFurtherMutation() {
        this.mutable = false
    }

    private fun assertMutable() {
        if (!mutable) {
            throw InvalidUserCodeException("Mutation of toolchain repositories declared in settings is only allowed during settings evaluation")
        }
    }

    private class ImmutableJavaToolchainRepository(private val delegate: JavaToolchainRepositoryInternal) : JavaToolchainRepositoryInternal {
        override fun getConfiguredAuthentication(): MutableCollection<Authentication> {
            return delegate.configuredAuthentication
        }

        override fun getCredentials(): PasswordCredentials {
            return delegate.getCredentials()
        }

        override fun <T : Credentials?> getCredentials(credentialsType: Class<T?>): T? {
            return delegate.getCredentials<T?>(credentialsType)
        }

        override fun credentials(action: Action<in PasswordCredentials>) {
            throw UnsupportedOperationException("Can't modify repositories through a read-only view")
        }

        override fun <T : Credentials?> credentials(credentialsType: Class<T?>, action: Action<in T?>) {
            throw UnsupportedOperationException("Can't modify repositories through a read-only view")
        }

        override fun credentials(credentialsType: Class<out Credentials>) {
            throw UnsupportedOperationException("Can't modify repositories through a read-only view")
        }

        override fun authentication(action: Action<in AuthenticationContainer>) {
            throw UnsupportedOperationException("Can't modify repositories through a read-only view")
        }

        override fun getAuthentication(): AuthenticationContainer {
            return delegate.getAuthentication()
        }

        override fun getName(): String {
            return delegate.name
        }

        override fun getResolverClass(): Property<Class<out JavaToolchainResolver>> {
            return delegate.resolverClass
        }
    }
}
