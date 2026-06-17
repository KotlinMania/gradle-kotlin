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
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.credentials.Credentials
import org.gradle.api.internal.artifacts.repositories.AuthenticationSupporter
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.authentication.Authentication
import org.gradle.jvm.toolchain.JavaToolchainResolver
import java.util.concurrent.Callable
import javax.inject.Inject

abstract class DefaultJavaToolchainRepository @Inject constructor(
    private val name: String,
    private val authenticationContainer: AuthenticationContainer,
    private val authenticationSupporter: AuthenticationSupporter,
    private val providerFactory: ProviderFactory
) : JavaToolchainRepositoryInternal {
    override fun getName(): String {
        return name
    }

    abstract override fun getResolverClass(): Property<Class<out JavaToolchainResolver>>?

    override fun getConfiguredAuthentication(): MutableCollection<Authentication> {
        return authenticationSupporter.getConfiguredAuthentication()
    }

    override fun getCredentials(): PasswordCredentials {
        return authenticationSupporter.getCredentials()
    }

    override fun <T : Credentials?> getCredentials(credentialsType: Class<T?>): T? {
        return authenticationSupporter.getCredentials<T?>(credentialsType)
    }

    override fun credentials(action: Action<in PasswordCredentials>) {
        authenticationSupporter.credentials(action)
    }

    override fun <T : Credentials?> credentials(credentialsType: Class<T?>, action: Action<in T?>) {
        authenticationSupporter.credentials<T?>(credentialsType, action)
    }

    override fun credentials(credentialsType: Class<out Credentials>) {
        authenticationSupporter.credentials(credentialsType, providerFactory.provider<String>(Callable { name }))
    }

    override fun authentication(action: Action<in AuthenticationContainer>) {
        authenticationSupporter.authentication(action)
    }

    override fun getAuthentication(): AuthenticationContainer {
        return authenticationContainer
    }
}
