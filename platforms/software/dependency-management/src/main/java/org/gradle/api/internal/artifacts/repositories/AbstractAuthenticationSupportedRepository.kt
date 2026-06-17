/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories

import org.apache.commons.lang3.builder.EqualsBuilder
import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.credentials.Credentials
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.repositories.descriptor.RepositoryDescriptor
import org.gradle.api.internal.provider.MissingValueException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.authentication.Authentication
import org.gradle.internal.Cast
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal
import org.gradle.internal.authentication.AuthenticationInternal
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.internal.CollectionUtils.collect
import java.net.URI
import java.util.concurrent.Callable
import java.util.function.Function

abstract class AbstractAuthenticationSupportedRepository<T : RepositoryDescriptor?> internal constructor(
    instantiator: Instantiator,
    authenticationContainer: AuthenticationContainer,
    objectFactory: ObjectFactory,
    private val providerFactory: ProviderFactory,
    versionParser: VersionParser
) : AbstractResolutionAwareArtifactRepository<T?>(objectFactory, versionParser), AuthenticationSupportedInternal {
    private val delegate: AuthenticationSupporter

    init {
        this.delegate = AuthenticationSupporter(instantiator, objectFactory, authenticationContainer, providerFactory)
    }

    override fun getCredentials(): PasswordCredentials {
        invalidateDescriptor()
        return delegate.getCredentials()
    }

    override fun <C : Credentials?> getCredentials(credentialsType: Class<C?>): C? {
        invalidateDescriptor()
        return delegate.getCredentials<C?>(credentialsType)
    }

    override fun getConfiguredCredentials(): Property<Credentials> {
        return delegate.getConfiguredCredentials()
    }

    override fun setConfiguredCredentials(credentials: Credentials) {
        invalidateDescriptor()
        delegate.setConfiguredCredentials(credentials)
    }

    override fun credentials(action: Action<in PasswordCredentials>) {
        invalidateDescriptor()
        delegate.credentials(action)
    }

    @Throws(IllegalStateException::class)
    override fun <C : Credentials?> credentials(credentialsType: Class<C?>, action: Action<in C?>) {
        invalidateDescriptor()
        delegate.credentials<C?>(credentialsType, action)
    }

    override fun credentials(credentialsType: Class<out Credentials>) {
        invalidateDescriptor()
        delegate.credentials(credentialsType, providerFactory.provider<String>(Callable { this.getName() }))
    }

    override fun authentication(action: Action<in AuthenticationContainer>) {
        invalidateDescriptor()
        delegate.authentication(action)
    }

    override fun getAuthentication(): AuthenticationContainer {
        invalidateDescriptor()
        return delegate.getAuthentication()
    }

    override fun getConfiguredAuthentication(): MutableCollection<Authentication> {
        val configuredAuthentication = delegate.getConfiguredAuthentication()

        for (authentication in configuredAuthentication) {
            val authenticationInternal = authentication as AuthenticationInternal
            for (repositoryUrl in this.repositoryUrls) {
                // only care about HTTP hosts right now
                if (repositoryUrl.getScheme().startsWith("http")) {
                    authenticationInternal.addHost(repositoryUrl.getHost(), repositoryUrl.getPort())
                }
            }
        }
        return configuredAuthentication
    }

    protected open val repositoryUrls: MutableCollection<URI>
        get() = mutableListOf<URI>()

    val authenticationSchemes: MutableList<String>
        get() = collect<String?, Authentication?>(
            getConfiguredAuthentication(),
            Function { authentication: Authentication? ->
                Cast.cast<AuthenticationInternal?, Authentication?>(
                    org.gradle.internal.authentication.AuthenticationInternal::class.java,
                    authentication
                )!!.type!!.getSimpleName()
            })

    fun usesCredentials(): Boolean {
        return delegate.usesCredentials()
    }

    override fun isUsingCredentialsProvider(): Provider<Boolean> {
        return getConfiguredCredentials().map<Boolean>(Transformer { configured: Credentials? -> isUsingCredentialsProvider(getName(), configured!!) }
        )
    }

    private fun isUsingCredentialsProvider(identity: String, toCheck: Credentials): Boolean {
        val referenceCredentials: Credentials?
        try {
            val credentialsProvider: Provider<out Credentials>?
            try {
                credentialsProvider = providerFactory.credentials(toCheck.javaClass, identity)
            } catch (e: IllegalArgumentException) {
                // some possibilities are invalid repository names and invalid credential types
                // either way, this is not the place to validate that
                return false
            }
            referenceCredentials = credentialsProvider.get()
        } catch (e: MissingValueException) {
            return false
        }
        return EqualsBuilder.reflectionEquals(toCheck, referenceCredentials)
    }
}
