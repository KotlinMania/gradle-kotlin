/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.repositories.UrlArtifactRepository
import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.deprecation.Documentation
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.verifier.HttpRedirectVerifier
import org.gradle.internal.verifier.HttpRedirectVerifierFactory
import java.net.URI
import java.util.function.Consumer
import java.util.function.Supplier
import javax.inject.Inject

class DefaultUrlArtifactRepository internal constructor(
    private val fileResolver: FileResolver,
    private val repositoryType: String,
    private val displayNameSupplier: Supplier<String>
) {
    private var url: Any? = null
    private var resolvedUrl: URI? = null
    var isAllowInsecureProtocol: Boolean = false

    fun getUrl(): URI {
        if (url == null) {
            return null
        }

        // We must always resolve the URL in case the backing Object is live/mutable
        // However, we always try to return the same URI instance if the backing object hasn't changed
        val latestUrl = fileResolver.resolveUri(url!!)
        if (latestUrl != resolvedUrl) {
            resolvedUrl = latestUrl
        }

        return resolvedUrl!!
    }

    fun setUrl(url: URI) {
        this.url = url
    }

    fun setUrl(url: Any) {
        this.url = url
    }

    fun validateUrl(): URI {
        val rootUri = getUrl()
        if (rootUri == null) {
            throw InvalidUserDataException(
                String.format(
                    "You must specify a URL for a %s repository.",
                    repositoryType
                )
            )
        }
        return rootUri
    }

    @Throws(InvalidUserCodeException::class)
    private fun throwExceptionDueToInsecureProtocol() {
        throw InsecureProtocolException(
            "Using insecure protocols with repositories, without explicit opt-in, is unsupported.",
            String.format("Switch %s repository '%s' to redirect to a secure protocol (like HTTPS) or allow insecure protocols.", repositoryType, displayNameSupplier.get()),
            Documentation.dslReference(UrlArtifactRepository::class.java, "allowInsecureProtocol").getConsultDocumentationMessage()
        )
    }

    @Throws(InvalidUserCodeException::class)
    private fun throwExceptionDueToInsecureRedirect(redirectFrom: URI?, redirectLocation: URI) {
        val contextualAdvice: String
        if (redirectFrom != null) {
            contextualAdvice = String.format(
                " '%s' is redirecting to '%s'. ",
                redirectFrom,
                redirectLocation
            )
        } else {
            contextualAdvice = ""
        }
        throw InsecureProtocolException(
            "Redirecting from secure protocol to insecure protocol, without explicit opt-in, is unsupported." + contextualAdvice,
            String.format("Switch %s repository '%s' to redirect to a secure protocol (like HTTPS) or allow insecure protocols. ", repositoryType, displayNameSupplier.get()),
            Documentation.dslReference(UrlArtifactRepository::class.java, "allowInsecureProtocol").getConsultDocumentationMessage()
        )
    }

    fun createRedirectVerifier(): HttpRedirectVerifier {
        val uri: URI? = getUrl()
        return HttpRedirectVerifierFactory
            .create(
                uri,
                this.isAllowInsecureProtocol,
                Runnable { this.throwExceptionDueToInsecureProtocol() },
                Consumer { redirection: URI? -> throwExceptionDueToInsecureRedirect(uri, redirection!!) }
            )
    }

    @ServiceScope(Scope.Project::class)
    class Factory @Inject constructor(private val fileResolver: FileResolver) {
        fun create(repositoryType: String, displayNameSupplier: Supplier<String>): DefaultUrlArtifactRepository {
            return DefaultUrlArtifactRepository(fileResolver, repositoryType, displayNameSupplier)
        }
    }
}
