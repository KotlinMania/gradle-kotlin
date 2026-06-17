/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.tooling.internal.provider

import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildLayoutParameters
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.buildprocess.BuildProcessState
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.instrumentation.agent.AgentStatus.Companion.disabled
import org.gradle.internal.logging.services.LoggingServiceRegistry.Companion.newEmbeddableLogging
import org.gradle.internal.nativeintegration.services.NativeServices.Companion.getInstance
import org.gradle.internal.nativeintegration.services.NativeServices.Companion.initializeOnClient
import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode.Companion.fromSystemProperties
import org.gradle.internal.service.CloseableServiceRegistry
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder.Companion.builder
import org.gradle.internal.service.scopes.GlobalScopeServices
import org.gradle.internal.service.scopes.Scope
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1
import org.gradle.tooling.internal.protocol.BuildParameters
import org.gradle.tooling.internal.protocol.BuildResult
import org.gradle.tooling.internal.protocol.ConfigurableConnection
import org.gradle.tooling.internal.protocol.ConnectionMetaDataVersion1
import org.gradle.tooling.internal.protocol.ConnectionParameters
import org.gradle.tooling.internal.protocol.ConnectionVersion4
import org.gradle.tooling.internal.protocol.InternalBuildAction
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2
import org.gradle.tooling.internal.protocol.InternalCancellableConnection
import org.gradle.tooling.internal.protocol.InternalCancellationToken
import org.gradle.tooling.internal.protocol.InternalInvalidatableVirtualFileSystemConnection
import org.gradle.tooling.internal.protocol.InternalParameterAcceptingConnection
import org.gradle.tooling.internal.protocol.InternalPhasedAction
import org.gradle.tooling.internal.protocol.InternalPhasedActionConnection
import org.gradle.tooling.internal.protocol.InternalStopWhenIdleConnection
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import org.gradle.tooling.internal.protocol.ModelIdentifier
import org.gradle.tooling.internal.protocol.PhasedActionResultListener
import org.gradle.tooling.internal.protocol.ShutdownParameters
import org.gradle.tooling.internal.protocol.StoppableConnection
import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionConnection
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequest
import org.gradle.tooling.internal.provider.connection.ProviderBuildResult
import org.gradle.tooling.internal.provider.connection.ProviderConnectionParameters
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters
import org.gradle.tooling.internal.provider.test.ProviderInternalTestExecutionRequest
import org.gradle.util.GradleVersion
import org.gradle.util.internal.IncubationLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.Boolean
import java.util.Arrays
import kotlin.Any
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Throws
import kotlin.checkNotNull

/**
 * Implements the provider side of the tooling API.
 *
 * @see org.gradle.tooling.internal.consumer.loader.DefaultToolingImplementationLoader
 */
@Suppress("deprecation")
class DefaultConnection : ConnectionVersion4, ConfigurableConnection, InternalCancellableConnection, InternalParameterAcceptingConnection, StoppableConnection, InternalTestExecutionConnection,
    InternalPhasedActionConnection, InternalInvalidatableVirtualFileSystemConnection, InternalStopWhenIdleConnection {
    private val adapter = ProtocolToModelAdapter()

    private var loggingServices: ServiceRegistry? = null
    private var clientServices: CloseableServiceRegistry? = null
    private var embeddedDaemonState: BuildProcessState? = null

    // not provided by older client versions
    private var consumerVersion: GradleVersion? = null
    private var verboseLogging = false

    /**
     * This is used by consumers 1.0-milestone-3 and later
     */
    init {
        LOGGER.debug("Tooling API provider {} created.", GradleVersion.current().getVersion())
    }

    /**
     * This is used by consumers 1.2-rc-1 and later.
     */
    override fun configure(parameters: ConnectionParameters?) {
        val providerConnectionParameters = ProtocolToModelAdapter().adapt<ProviderConnectionParameters>(ProviderConnectionParameters::class.java, parameters)
        var gradleUserHomeDir = providerConnectionParameters.getGradleUserHomeDir(null)
        if (gradleUserHomeDir == null) {
            gradleUserHomeDir = BuildLayoutParameters().getGradleUserHomeDir()
        }
        initializeServices(gradleUserHomeDir)
        consumerVersion = GradleVersion.version(providerConnectionParameters.consumerVersion)
        verboseLogging = providerConnectionParameters.verboseLogging
    }

    private fun initializeServices(gradleUserHomeDir: File) {
        initializeOnClient(gradleUserHomeDir, fromSystemProperties())
        this.loggingServices = newEmbeddableLogging()
    }

    /**
     * This is used by consumers 1.0-milestone-3 and later
     */
    override fun getMetaData(): ConnectionMetaDataVersion1 {
        return DefaultConnectionMetaData()
    }

    /**
     * This is used by consumers 2.2-rc-1 and later
     */
    override fun shutdown(parameters: ShutdownParameters?) {
        if (embeddedDaemonState != null) {
            embeddedDaemonState!!.getServices().get<ShutdownCoordinator?>(ShutdownCoordinator::class.java)!!.stopAllDaemons()
            embeddedDaemonState!!.close()
            embeddedDaemonState = null
        }
        if (clientServices != null) {
            clientServices!!.get<ShutdownCoordinator?>(ShutdownCoordinator::class.java)!!.stopAllDaemons()
            clientServices!!.close()
            clientServices = null
        }
        loggingServices = null
    }

    /**
     * This is used by consumers 2.1-rc-1 and later
     */
    @Throws(BuildExceptionVersion1::class, InternalUnsupportedModelException::class, InternalUnsupportedBuildArgumentException::class, IllegalStateException::class)
    override fun getModel(modelIdentifier: ModelIdentifier, cancellationToken: InternalCancellationToken, operationParameters: BuildParameters?): BuildResult<*> {
        val providerParameters = validateAndConvert(operationParameters)
        val buildCancellationToken: BuildCancellationToken = InternalCancellationTokenAdapter(cancellationToken)
        val result: Any? = getConnection(providerParameters)!!.run(modelIdentifier.name, buildCancellationToken, providerParameters)
        return ProviderBuildResult<Any?>(result)
    }

    /**
     * This is used by consumers 2.1-rc-1 to 4.3
     */
    @Throws(BuildExceptionVersion1::class, InternalUnsupportedBuildArgumentException::class, IllegalStateException::class)
    override fun <T> run(action: InternalBuildAction<T?>, cancellationToken: InternalCancellationToken, operationParameters: BuildParameters?): BuildResult<T?> {
        val providerParameters = validateAndConvert(operationParameters)
        val buildCancellationToken: BuildCancellationToken = InternalCancellationTokenAdapter(cancellationToken)
        val results = getConnection(providerParameters)!!.run(action, buildCancellationToken, providerParameters)
        return ProviderBuildResult<T?>(uncheckedNonnullCast<T?>(results))
    }

    /**
     * This is used by consumers 4.4 and later
     */
    @Throws(BuildExceptionVersion1::class, InternalUnsupportedBuildArgumentException::class, IllegalStateException::class)
    override fun <T> run(action: InternalBuildActionVersion2<T?>, cancellationToken: InternalCancellationToken, operationParameters: BuildParameters?): BuildResult<T?> {
        val providerParameters = validateAndConvert(operationParameters)
        val buildCancellationToken: BuildCancellationToken = InternalCancellationTokenAdapter(cancellationToken)
        val results = getConnection(providerParameters)!!.run(action, buildCancellationToken, providerParameters)
        return ProviderBuildResult<T?>(uncheckedNonnullCast<T?>(results))
    }

    /**
     * This is used by consumers 4.8 and later
     */
    override fun run(phasedAction: InternalPhasedAction, listener: PhasedActionResultListener, cancellationToken: InternalCancellationToken, operationParameters: BuildParameters?): BuildResult<*> {
        val providerParameters = validateAndConvert(operationParameters)
        val buildCancellationToken: BuildCancellationToken = InternalCancellationTokenAdapter(cancellationToken)
        val results: Any? = getConnection(providerParameters)!!.runPhasedAction(phasedAction, listener, buildCancellationToken, providerParameters)
        return ProviderBuildResult<Any?>(results)
    }

    /**
     * This is used by consumers 2.6-rc-1 and later
     */
    @Throws(BuildExceptionVersion1::class, InternalUnsupportedBuildArgumentException::class, IllegalStateException::class)
    override fun runTests(testExecutionRequest: InternalTestExecutionRequest?, cancellationToken: InternalCancellationToken, operationParameters: BuildParameters?): BuildResult<*> {
        val providerParameters = validateAndConvert(operationParameters)
        val testExecutionRequestVersion2 = adapter.adapt<ProviderInternalTestExecutionRequest>(ProviderInternalTestExecutionRequest::class.java, testExecutionRequest)
        val buildCancellationToken: BuildCancellationToken = InternalCancellationTokenAdapter(cancellationToken)
        val results: Any? = getConnection(providerParameters)!!.runTests(testExecutionRequestVersion2, buildCancellationToken, providerParameters)
        return ProviderBuildResult<Any?>(results)
    }

    private fun validateAndConvert(buildParameters: BuildParameters?): ProviderOperationParameters {
        LOGGER.info("Tooling API is using target Gradle version: {}.", GradleVersion.current().getVersion())

        checkUnsupportedTapiVersion()
        val parameters: ProviderOperationParameters = adapter.builder<ProviderOperationParameters>(ProviderOperationParameters::class.java)
            .mixInTo(org.gradle.tooling.internal.provider.connection.ProviderOperationParameters::class.java, org.gradle.tooling.internal.provider.connection.BuildLogLevelMixIn::class.java)
            .build(buildParameters)!!

        DeprecationLogger.reset()
        IncubationLogger.reset()
        return parameters
    }

    private fun unsupportedConnectionException(): UnsupportedVersionException {
        return UnsupportedVersionException(
            String.format(
                "Support for clients using a tooling API version older than %s was removed in Gradle %d.0. %sYou should upgrade your tooling API client to version %s or later.",
                MIN_CLIENT_VERSION_STR,
                DefaultGradleConnector.MINIMAL_CLIENT_MAJOR_VERSION + GUARANTEED_TAPI_BACKWARDS_COMPATIBILITY,
                createCurrentVersionMessage(),
                MIN_CLIENT_VERSION_STR
            )
        )
    }

    private fun createCurrentVersionMessage(): String {
        if (consumerVersion == null) {
            return ""
        } else {
            // Consumer version is provided by client 1.2 and later
            return String.format("You are currently using tooling API version %s. ", consumerVersion!!.getVersion())
        }
    }

    private fun checkUnsupportedTapiVersion() {
        if (consumerVersion == null || consumerVersion!!.compareTo(DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION) < 0) {
            throw unsupportedConnectionException()
        }
    }

    override fun notifyDaemonsAboutChangedPaths(changedPaths: MutableList<String?>, operationParameters: BuildParameters?) {
        val providerParameters = validateAndConvert(operationParameters)
        getConnection(providerParameters)!!.notifyDaemonsAboutChangedPaths(changedPaths, providerParameters)
    }

    override fun stopWhenIdle(operationParameters: BuildParameters?) {
        val providerParameters = validateAndConvert(operationParameters)
        getConnection(providerParameters)!!.stopWhenIdle(providerParameters)
    }

    private fun getConnection(providerParameters: ProviderOperationParameters): ProviderConnection? {
        return getServices(providerParameters)!!.get<ProviderConnection?>(ProviderConnection::class.java)
    }

    private fun getServices(providerParameters: ProviderOperationParameters): ServiceRegistry? {
        if (Boolean.TRUE == providerParameters.isEmbedded) {
            if (embeddedDaemonState == null) {
                embeddedDaemonState = BuildProcessState(
                    true,
                    disabled(),
                    CurrentGradleInstallation.locate(),
                    mutableSetOf<ServiceRegistrationProvider?>(ConnectionScopeServices()),
                    Arrays.asList<ServiceRegistry?>(
                        loggingServices,
                        getInstance()
                    )
                )
                configureServices(embeddedDaemonState!!.getServices())
            }
            return embeddedDaemonState!!.getServices()
        } else {
            if (clientServices == null) {
                clientServices = builder()
                    .displayName("TAPI connection global services")
                    .scopeStrictly(Scope.Global::class.java)
                    .parent(loggingServices)
                    .parent(getInstance())
                    .provider(
                        GlobalScopeServices(
                            true,
                            disabled(),
                            CurrentGradleInstallation.locate()
                        )
                    )
                    .provider(ConnectionScopeServices())
                    .build()
                configureServices(clientServices!!)
            }
            return clientServices
        }
    }

    private fun configureServices(services: ServiceRegistry) {
        checkNotNull(consumerVersion)
        services.get<ProviderConnection?>(ProviderConnection::class.java)!!.configure(verboseLogging, consumerVersion!!)
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultConnection::class.java)

        private val MIN_CLIENT_VERSION_STR: String? = DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION.getVersion()
        const val GUARANTEED_TAPI_BACKWARDS_COMPATIBILITY: Int = 5
    }
}
