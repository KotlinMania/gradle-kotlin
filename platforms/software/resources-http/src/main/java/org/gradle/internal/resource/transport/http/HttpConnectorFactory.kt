/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.internal.resource.transport.http

import com.google.common.collect.ImmutableSet
import org.gradle.authentication.Authentication
import org.gradle.authentication.http.BasicAuthentication
import org.gradle.authentication.http.DigestAuthentication
import org.gradle.authentication.http.HttpHeaderAuthentication
import org.gradle.internal.authentication.AllSchemesAuthentication
import org.gradle.internal.resource.connector.ResourceConnectorFactory
import org.gradle.internal.resource.connector.ResourceConnectorSpecification
import org.gradle.internal.resource.transfer.DefaultExternalResourceConnector
import org.gradle.internal.resource.transfer.ExternalResourceConnector

class HttpConnectorFactory(private val sslContextFactory: SslContextFactory?, private val httpClientFactory: HttpClientFactory) : ResourceConnectorFactory {
    override fun getSupportedProtocols(): MutableSet<String?> {
        return SUPPORTED_PROTOCOLS
    }

    override fun getSupportedAuthentication(): MutableSet<Class<out Authentication?>?> {
        return SUPPORTED_AUTHENTICATION
    }

    override fun createResourceConnector(connectionDetails: ResourceConnectorSpecification): ExternalResourceConnector {
        val client = httpClientFactory.createClient(
            DefaultHttpSettings.Companion.builder()
                .withAuthenticationSettings(connectionDetails.getAuthentications())
                .withSslContextFactory(sslContextFactory)
                .withRedirectVerifier(connectionDetails.getRedirectVerifier())
                .build()
        )
        val accessor = HttpResourceAccessor(client)
        val lister = HttpResourceLister(accessor)
        val uploader = HttpResourceUploader(client)
        return DefaultExternalResourceConnector(accessor, lister, uploader)
    }

    companion object {
        private val SUPPORTED_PROTOCOLS: MutableSet<String?> = ImmutableSet.of<String?>("http", "https")
        private val SUPPORTED_AUTHENTICATION: MutableSet<Class<out Authentication?>?> = ImmutableSet.of<Class<out Authentication?>?>(
            BasicAuthentication::class.java,
            DigestAuthentication::class.java,
            HttpHeaderAuthentication::class.java,
            AllSchemesAuthentication::class.java
        )
    }
}
