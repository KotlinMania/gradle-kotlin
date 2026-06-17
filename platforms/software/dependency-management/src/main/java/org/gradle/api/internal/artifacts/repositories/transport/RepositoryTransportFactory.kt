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
package org.gradle.api.internal.artifacts.repositories.transport

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.StartParameterResolutionOverride
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultExternalResourceCachePolicy
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.ExternalResourceCachePolicy
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.authentication.Authentication
import org.gradle.cache.internal.ProducerGuard
import org.gradle.internal.authentication.AuthenticationInternal
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.cached.CachedExternalResourceIndex
import org.gradle.internal.resource.connector.ResourceConnectorFactory
import org.gradle.internal.resource.connector.ResourceConnectorSpecification
import org.gradle.internal.resource.local.FileResourceRepository
import org.gradle.internal.resource.transport.ResourceConnectorRepositoryTransport
import org.gradle.internal.resource.transport.file.FileTransport
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.verifier.HttpRedirectVerifier
import org.gradle.util.internal.BuildCommencedTimeProvider

@ServiceScope(Scope.Build::class)
class RepositoryTransportFactory(
    resourceConnectorFactory: MutableCollection<ResourceConnectorFactory?>,
    private val temporaryFileProvider: TemporaryFileProvider,
    private val cachedExternalResourceIndex: CachedExternalResourceIndex<String?>,
    private val timeProvider: BuildCommencedTimeProvider,
    private val artifactCacheLockingManager: ArtifactCacheLockingAccessCoordinator,
    private val buildOperationRunner: BuildOperationRunner,
    private val startParameterResolutionOverride: StartParameterResolutionOverride,
    private val producerGuard: ProducerGuard<ExternalResourceName?>,
    private val fileRepository: FileResourceRepository,
    private val checksumService: ChecksumService
) {
    private val registeredProtocols: MutableList<ResourceConnectorFactory> = ArrayList<ResourceConnectorFactory>()

    init {
        registeredProtocols.addAll(resourceConnectorFactory)
    }

    fun getRegisteredProtocols(): MutableSet<String?> {
        val validSchemes: MutableSet<String?> = LinkedHashSet<String?>()
        for (registeredProtocol in registeredProtocols) {
            validSchemes.addAll(registeredProtocol.getSupportedProtocols())
        }
        return validSchemes
    }

    fun createFileTransport(name: String?): RepositoryTransport {
        return FileTransport(name, fileRepository, cachedExternalResourceIndex, temporaryFileProvider, timeProvider, artifactCacheLockingManager, producerGuard, checksumService)
    }

    fun createTransport(scheme: String?, name: String?, authentications: MutableCollection<Authentication?>, redirectVerifier: HttpRedirectVerifier?): RepositoryTransport? {
        return createTransport(mutableSetOf<String?>(scheme), name, authentications, redirectVerifier)
    }

    fun createTransport(schemes: MutableSet<String?>, name: String?, authentications: MutableCollection<Authentication?>, redirectVerifier: HttpRedirectVerifier?): RepositoryTransport? {
        validateSchemes(schemes)

        val connectorFactory = findConnectorFactory(schemes)

        // Ensure resource transport protocol, authentication types and credentials are all compatible
        validateConnectorFactoryCredentials(schemes, connectorFactory, authentications)

        // File resources are handled slightly differently at present.
        // file:// repos are treated differently
        // 1) we don't cache their files
        // 2) we don't do progress logging for "downloading"
        if (schemes == mutableSetOf<String?>("file")) {
            return createFileTransport(name)
        }
        val connectionDetails: ResourceConnectorSpecification = DefaultResourceConnectorSpecification(authentications, redirectVerifier)

        var resourceConnector = connectorFactory.createResourceConnector(connectionDetails)
        resourceConnector = startParameterResolutionOverride.overrideExternalResourceConnector(resourceConnector)

        var cachePolicy: ExternalResourceCachePolicy = DefaultExternalResourceCachePolicy()
        cachePolicy = startParameterResolutionOverride.overrideExternalResourceCachePolicy(cachePolicy)

        return ResourceConnectorRepositoryTransport(
            name,
            temporaryFileProvider,
            cachedExternalResourceIndex,
            timeProvider,
            artifactCacheLockingManager,
            resourceConnector,
            buildOperationRunner,
            cachePolicy,
            producerGuard,
            fileRepository,
            checksumService
        )
    }

    private fun validateSchemes(schemes: MutableSet<String?>) {
        val validSchemes = getRegisteredProtocols()
        for (scheme in schemes) {
            if (!validSchemes.contains(scheme)) {
                throw InvalidUserDataException(String.format("Not a supported repository protocol '%s': valid protocols are %s", scheme, validSchemes))
            }
        }
    }

    private fun validateConnectorFactoryCredentials(schemes: MutableSet<String?>, factory: ResourceConnectorFactory, authentications: MutableCollection<Authentication?>) {
        val configuredAuthenticationTypes: MutableSet<Class<out Authentication?>?> = HashSet<Class<out Authentication?>?>()

        for (authentication in authentications) {
            val authenticationInternal = authentication as AuthenticationInternal
            var isAuthenticationSupported = false
            val credentials = authenticationInternal.credentials
            val needCredentials = authenticationInternal.requiresCredentials()

            for (authenticationType in factory.getSupportedAuthentication()) {
                if (authenticationType.isAssignableFrom(authentication.javaClass)) {
                    isAuthenticationSupported = true
                    break
                }
            }

            if (!isAuthenticationSupported) {
                throw InvalidUserDataException(
                    String.format(
                        "Authentication scheme %s is not supported by protocol '%s'",
                        authentication, schemes.iterator().next()
                    )
                )
            }

            if (credentials != null) {
                if (!authentication.supports(credentials)) {
                    throw InvalidUserDataException(
                        String.format(
                            "Credentials type of '%s' is not supported by authentication scheme %s",
                            credentials.javaClass.getSimpleName(), authentication
                        )
                    )
                }
            } else {
                if (needCredentials) {
                    throw InvalidUserDataException("You cannot configure authentication schemes for this repository type if no credentials are provided.")
                }
            }

            if (!configuredAuthenticationTypes.add(authenticationInternal.type)) {
                throw InvalidUserDataException(String.format("You cannot configure multiple authentication schemes of the same type.  The duplicate one is %s.", authentication))
            }
        }
    }

    private fun findConnectorFactory(schemes: MutableSet<String?>): ResourceConnectorFactory {
        for (protocolRegistration in registeredProtocols) {
            if (protocolRegistration.getSupportedProtocols().containsAll(schemes)) {
                return protocolRegistration
            }
        }
        throw InvalidUserDataException("You cannot mix different URL schemes for a single repository. Please declare separate repositories.")
    }

    private class DefaultResourceConnectorSpecification(private val authentications: MutableCollection<Authentication?>?, private val redirectVerifier: HttpRedirectVerifier?) :
        ResourceConnectorSpecification {
        override fun <T> getCredentials(type: Class<T?>): T? {
            if (authentications == null || authentications.size < 1) {
                return null
            }

            val credentials = (authentications.iterator().next() as AuthenticationInternal).credentials

            if (credentials == null) {
                return null
            }
            if (type.isAssignableFrom(credentials.javaClass)) {
                return type.cast(credentials)
            } else {
                throw IllegalArgumentException(String.format("Credentials must be an instance of '%s'.", type.getCanonicalName()))
            }
        }

        override fun getAuthentications(): MutableCollection<Authentication?>? {
            return authentications
        }

        override fun getRedirectVerifier(): HttpRedirectVerifier? {
            return redirectVerifier
        }
    }
}
