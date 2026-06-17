/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.credentials.AwsCredentials
import org.gradle.api.credentials.Credentials
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.authentication.Authentication
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.authentication.AllSchemesAuthentication
import org.gradle.internal.authentication.AuthenticationInternal
import org.gradle.internal.credentials.DefaultAwsCredentials
import org.gradle.internal.credentials.DefaultHttpHeaderCredentials
import org.gradle.internal.credentials.DefaultPasswordCredentials
import org.gradle.internal.reflect.Instantiator

class AuthenticationSupporter(private val instantiator: Instantiator, objectFactory: ObjectFactory, val authentication: AuthenticationContainer, private val providerFactory: ProviderFactory) {
    val configuredCredentials: Property<Credentials>
    private var usesCredentials = false

    init {
        this.configuredCredentials = objectFactory.property<Credentials>(Credentials::class.java)
    }

    fun getCredentials(): PasswordCredentials {
        if (!usesCredentials()) {
            return setCredentials<PasswordCredentials>(PasswordCredentials::class.java)!!
        } else if (configuredCredentials.get() is PasswordCredentials) {
            return uncheckedCast<PasswordCredentials?>(configuredCredentials.get())!!
        } else {
            throw IllegalStateException("Can not use getCredentials() method when not using PasswordCredentials; please use getCredentials(Class)")
        }
    }

    fun <T : Credentials?> getCredentials(credentialsType: Class<T?>): T? {
        if (!usesCredentials()) {
            return setCredentials<T?>(credentialsType)
        } else if (credentialsType.isInstance(configuredCredentials.get())) {
            return uncheckedCast<T?>(configuredCredentials.get())
        } else {
            throw IllegalArgumentException(
                String.format(
                    "Given credentials type '%s' does not match actual type '%s'", credentialsType.getName(), Companion.getCredentialsPublicType(
                        configuredCredentials.get().javaClass
                    ).getName()
                )
            )
        }
    }

    fun credentials(action: Action<in PasswordCredentials>) {
        check(!(usesCredentials() && configuredCredentials.get() !is PasswordCredentials)) { "Can not use credentials(Action) method when not using PasswordCredentials; please use credentials(Class, Action)" }
        credentials<PasswordCredentials>(PasswordCredentials::class.java, action)
    }

    @Throws(IllegalStateException::class)
    fun <T : Credentials?> credentials(credentialsType: Class<T?>, action: Action<in T>) {
        action.execute(getCredentials<T?>(credentialsType))
    }

    fun credentials(credentialsType: Class<out Credentials>, identity: Provider<String>) {
        this.usesCredentials = true
        this.configuredCredentials.set(providerFactory.credentials(credentialsType, identity))
    }

    fun setConfiguredCredentials(credentials: Credentials) {
        this.usesCredentials = true
        this.configuredCredentials.set(credentials)
    }

    private fun <T : Credentials?> setCredentials(clazz: Class<T?>): T? {
        this.usesCredentials = true
        val t = newCredentials<T?>(clazz)
        configuredCredentials.set(t)
        return t
    }

    private fun <T : Credentials?> newCredentials(clazz: Class<T?>): T? {
        return instantiator.newInstance<T?>(getCredentialsImplType<T?>(clazz))
    }

    fun authentication(action: Action<in AuthenticationContainer>) {
        action.execute(this.authentication)
    }

    val configuredAuthentication: MutableCollection<Authentication>
        get() {
            populateAuthenticationCredentials()
            if (usesCredentials() && authentication.size == 0) {
                return mutableSetOf<Authentication>(AllSchemesAuthentication(configuredCredentials.get()))
            } else {
                return this.authentication
            }
        }

    fun usesCredentials(): Boolean {
        return usesCredentials
    }

    private fun populateAuthenticationCredentials() {
        // TODO: This will have to be changed when we support setting credentials directly on the authentication
        for (authentication in this.authentication) {
            (authentication as AuthenticationInternal).credentials = configuredCredentials.getOrNull()
        }
    }

    companion object {
        // Mappings between public and impl types
        // If the list of mappings grows we should move it to a data structure
        private fun <T : Credentials?> getCredentialsImplType(publicType: Class<T?>): Class<out T> {
            if (publicType == PasswordCredentials::class.java) {
                return uncheckedCast<Class<out T>?>(DefaultPasswordCredentials::class.java)!!
            } else if (publicType == AwsCredentials::class.java) {
                return uncheckedCast<Class<out T>?>(DefaultAwsCredentials::class.java)!!
            } else if (publicType == HttpHeaderCredentials::class.java) {
                return uncheckedCast<Class<out T>?>(DefaultHttpHeaderCredentials::class.java)!!
            } else {
                throw IllegalArgumentException(
                    String.format(
                        "Unknown credentials type: '%s' (supported types: %s, %s and %s).",
                        publicType.getName(),
                        PasswordCredentials::class.java.getName(),
                        AwsCredentials::class.java.getName(),
                        HttpHeaderCredentials::class.java.getName()
                    )
                )
            }
        }

        private fun <T : Credentials?> getCredentialsPublicType(implType: Class<T?>): Class<in T?> {
            if (PasswordCredentials::class.java.isAssignableFrom(implType)) {
                return uncheckedCast<Class<in T?>?>(PasswordCredentials::class.java)!!
            } else if (AwsCredentials::class.java.isAssignableFrom(implType)) {
                return uncheckedCast<Class<in T?>?>(AwsCredentials::class.java)!!
            } else if (HttpHeaderCredentials::class.java.isAssignableFrom(implType)) {
                return uncheckedCast<Class<in T?>?>(HttpHeaderCredentials::class.java)!!
            } else {
                throw IllegalArgumentException(
                    String.format(
                        "Unknown credentials implementation type: '%s' (supported types: %s, %s and %s).",
                        implType.getName(),
                        DefaultPasswordCredentials::class.java.getName(),
                        DefaultAwsCredentials::class.java.getName(),
                        DefaultHttpHeaderCredentials::class.java.getName()
                    )
                )
            }
        }
    }
}
