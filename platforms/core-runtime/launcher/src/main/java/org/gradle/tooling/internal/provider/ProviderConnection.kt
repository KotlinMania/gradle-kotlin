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
package org.gradle.tooling.internal.provider

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableMap
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.tasks.userinput.UserInputReader
import org.gradle.api.logging.LogLevel
import org.gradle.cli.CommandLineParser
import org.gradle.cli.ParsedCommandLine
import org.gradle.configuration.GradleLauncherMetaData
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.BuildEventConsumer
import org.gradle.initialization.BuildLayoutParameters
import org.gradle.initialization.NoOpBuildEventConsumer
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.daemon.client.execution.ClientBuildRequestContext
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.logging.LoggingManagerFactory
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.console.GlobalUserInputReceiver
import org.gradle.internal.logging.services.LoggingServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.snapshot.impl.IsolatableSerializerRegistry
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration
import org.gradle.launcher.cli.converter.BuildLayoutConverter
import org.gradle.launcher.cli.converter.BuildOptionBackedConverter
import org.gradle.launcher.cli.converter.InitialPropertiesConverter
import org.gradle.launcher.cli.converter.LayoutToPropertiesConverter
import org.gradle.launcher.cli.internal.HelpRenderer.render
import org.gradle.launcher.cli.internal.VersionInfoRenderer.renderWithLauncherJvm
import org.gradle.launcher.configuration.AllProperties
import org.gradle.launcher.configuration.BuildLayoutResult
import org.gradle.launcher.daemon.client.DaemonClient
import org.gradle.launcher.daemon.client.DaemonClientFactory
import org.gradle.launcher.daemon.client.NotifyDaemonAboutChangedPathsClient
import org.gradle.launcher.daemon.client.NotifyDaemonClientExecuter
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.launcher.daemon.context.DaemonRequestContext
import org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria
import org.gradle.launcher.daemon.toolchain.ToolchainBuildOptions.forToolChainConfiguration
import org.gradle.launcher.exec.BuildActionExecutor
import org.gradle.launcher.exec.BuildActionParameters
import org.gradle.launcher.exec.BuildActionResult
import org.gradle.launcher.exec.BuildExecutor
import org.gradle.process.internal.streams.SafeStreams
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.internal.build.DefaultBuildEnvironment
import org.gradle.tooling.internal.build.DefaultHelp
import org.gradle.tooling.internal.consumer.parameters.FailsafeBuildProgressListenerAdapter
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1
import org.gradle.tooling.internal.protocol.InternalBuildAction
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.protocol.InternalPhasedAction
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import org.gradle.tooling.internal.protocol.ModelIdentifier
import org.gradle.tooling.internal.protocol.PhasedActionResultListener
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionException
import org.gradle.tooling.internal.provider.action.BuildModelAction
import org.gradle.tooling.internal.provider.action.ClientProvidedBuildAction
import org.gradle.tooling.internal.provider.action.ClientProvidedPhasedAction
import org.gradle.tooling.internal.provider.action.TestExecutionRequestAction
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.test.ProviderInternalTestExecutionRequest
import org.gradle.tooling.model.UnsupportedMethodException
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.build.Help
import org.gradle.util.GradleVersion
import org.gradle.util.internal.GUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.lang.Boolean
import java.util.EnumSet
import java.util.Optional
import java.util.function.Consumer
import kotlin.Any
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.RuntimeException
import kotlin.String
import kotlin.Suppress
import kotlin.require
import kotlin.synchronized

@ServiceScope(Scope.Global::class)
class ProviderConnection(
    private val sharedServices: ServiceRegistry,
    private val buildLayoutFactory: BuildLayoutFactory,
    private val daemonClientFactory: DaemonClientFactory,
    private val payloadSerializer: PayloadSerializer,
    private val fileCollectionFactory: FileCollectionFactory,
    private val shutdownCoordinator: ShutdownCoordinator,
    private val notifyDaemonClientExecuter: NotifyDaemonClientExecuter,
    private val isolatableSerializerRegistry: IsolatableSerializerRegistry
) {
    private var consumerVersion: GradleVersion? = null

    fun configure(verboseLogging: Boolean, consumerVersion: GradleVersion) {
        this.consumerVersion = consumerVersion
        val providerLogLevel = if (verboseLogging) LogLevel.DEBUG else LogLevel.INFO
        LOGGER.debug("Configuring logging to level: {}", providerLogLevel)
        val loggingManager = sharedServices.get<LoggingManagerFactory>(LoggingManagerFactory::class.java).createLoggingManager()
        loggingManager.setLevelInternal(providerLogLevel)
        loggingManager.start()
    }

    fun run(modelName: String, cancellationToken: BuildCancellationToken, providerParameters: ProviderOperationParameters): Any {
        val tasks: MutableList<String>? = providerParameters.tasks
        require(!(modelName == ModelIdentifier.NULL_MODEL && tasks == null)) { "No model type or tasks specified." }
        val params = initParams(providerParameters)
        if (BuildEnvironment::class.java.getName() == modelName) {
            //we don't really need to launch the daemon to acquire information needed for BuildEnvironment
            require(tasks == null) { "Cannot run tasks and fetch the build environment model." }
            val javaHome: File = reportableJavaHomeForBuild(params)
            return DefaultBuildEnvironment(
                DefaultBuildIdentifier(providerParameters.projectDir),
                params.buildLayout.getGradleUserHomeDir(),
                GradleVersion.current().getVersion(),
                javaHome,
                params.daemonParams.getEffectiveJvmArgs(),
                renderWithLauncherJvm(Jvm.forHome(javaHome).toString())
            )
        }

        if (Help::class.java.getName() == modelName) {
            require(tasks == null) { "Cannot run tasks and fetch the Help model." }
            val help = render(null, true)
            return DefaultHelp(help)
        }

        val listenerConfig: ProgressListenerConfiguration = ProgressListenerConfiguration.Companion.from(providerParameters, consumerVersion!!, payloadSerializer, isolatableSerializerRegistry)
        val action: BuildAction = BuildModelAction(params.startParameter, modelName, tasks != null, listenerConfig.clientSubscriptions)
        return run(action, cancellationToken, listenerConfig, listenerConfig.buildEventConsumer, providerParameters, params)
    }

    @Suppress("deprecation")
    fun run(clientAction: InternalBuildAction<*>, cancellationToken: BuildCancellationToken, providerParameters: ProviderOperationParameters): Any {
        return runClientAction(clientAction, cancellationToken, providerParameters)
    }

    fun run(clientAction: InternalBuildActionVersion2<*>, cancellationToken: BuildCancellationToken, providerParameters: ProviderOperationParameters): Any {
        return runClientAction(clientAction, cancellationToken, providerParameters)
    }

    fun runClientAction(clientAction: Any, cancellationToken: BuildCancellationToken, providerParameters: ProviderOperationParameters): Any {
        val tasks: MutableList<String>? = providerParameters.tasks
        val serializedAction = payloadSerializer.serialize(clientAction)
        val params = initParams(providerParameters)
        val startParameter = ProviderStartParameterConverter().toStartParameter(providerParameters, params.buildLayout, params.properties, params.tapiEnvironmentVariables)
        val listenerConfig: ProgressListenerConfiguration = ProgressListenerConfiguration.Companion.from(providerParameters, consumerVersion!!, payloadSerializer, isolatableSerializerRegistry)
        val action: BuildAction = ClientProvidedBuildAction(startParameter, serializedAction, tasks != null, listenerConfig.clientSubscriptions)
        return run(action, cancellationToken, listenerConfig, listenerConfig.buildEventConsumer, providerParameters, params)
    }

    fun runPhasedAction(
        clientPhasedAction: InternalPhasedAction,
        resultListener: PhasedActionResultListener,
        cancellationToken: BuildCancellationToken,
        providerParameters: ProviderOperationParameters
    ): Any {
        val tasks: MutableList<String>? = providerParameters.tasks
        val serializedAction = payloadSerializer.serialize(clientPhasedAction)
        val params = initParams(providerParameters)
        val startParameter = ProviderStartParameterConverter().toStartParameter(providerParameters, params.buildLayout, params.properties, params.tapiEnvironmentVariables)
        val failsafePhasedActionResultListener = FailsafePhasedActionResultListener(resultListener)
        val listenerConfig: ProgressListenerConfiguration = ProgressListenerConfiguration.Companion.from(providerParameters, consumerVersion!!, payloadSerializer, isolatableSerializerRegistry)
        val action: BuildAction = ClientProvidedPhasedAction(startParameter, serializedAction, tasks != null, listenerConfig.clientSubscriptions)
        try {
            return run(
                action, cancellationToken, listenerConfig, PhasedActionEventConsumer(failsafePhasedActionResultListener, payloadSerializer, listenerConfig.buildEventConsumer),
                providerParameters, params
            )
        } finally {
            failsafePhasedActionResultListener.rethrowErrors()
        }
    }

    fun runTests(testExecutionRequest: ProviderInternalTestExecutionRequest, cancellationToken: BuildCancellationToken, providerParameters: ProviderOperationParameters): Any {
        val params = initParams(providerParameters)
        val startParameter = ProviderStartParameterConverter().toStartParameter(providerParameters, params.buildLayout, params.properties, params.tapiEnvironmentVariables)
        val listenerConfig: ProgressListenerConfiguration = ProgressListenerConfiguration.Companion.from(providerParameters, consumerVersion!!, payloadSerializer, isolatableSerializerRegistry)
        val action = TestExecutionRequestAction.create(listenerConfig.clientSubscriptions, startParameter, testExecutionRequest)
        return run(action, cancellationToken, listenerConfig, listenerConfig.buildEventConsumer, providerParameters, params)
    }

    fun notifyDaemonsAboutChangedPaths(changedPaths: MutableList<String>, providerParameters: ProviderOperationParameters) {
        val requestSpecificLoggingServices = LoggingServiceRegistry.newNestedLogging()
        val params = initParams(providerParameters)
        notifyDaemonClientExecuter.execute(
            requestSpecificLoggingServices,
            params.daemonParams.getBaseDir(),
            Consumer { client: NotifyDaemonAboutChangedPathsClient? -> client!!.notifyDaemonsAboutChangedPaths(changedPaths) })
    }

    fun stopWhenIdle(providerParameters: ProviderOperationParameters) {
        val requestSpecificLoggingServices = LoggingServiceRegistry.newNestedLogging()
        val params = initParams(providerParameters)
        shutdownCoordinator.stopStartedDaemons(requestSpecificLoggingServices, params.daemonParams.getBaseDir())
    }

    private fun run(
        action: BuildAction, cancellationToken: BuildCancellationToken,
        progressListenerConfiguration: ProgressListenerConfiguration,
        buildEventConsumer: BuildEventConsumer,
        providerParameters: ProviderOperationParameters,
        parameters: Parameters
    ): Any {
        try {
            val executor = createExecutor(providerParameters, parameters)
            val interactiveConsole = providerParameters.standardInput != null
            val context = ClientBuildRequestContext(GradleLauncherMetaData(), providerParameters.startTime, interactiveConsole, cancellationToken, buildEventConsumer)
            val result = executor.execute(action, ConnectionOperationParameters(parameters.daemonParams, parameters.tapiSystemProperties, providerParameters), context)
            throwFailure(result)
            return payloadSerializer.deserialize(result.getResult())!!
        } finally {
            progressListenerConfiguration.failsafeWrapper.rethrowErrors()
        }
    }

    private fun throwFailure(result: BuildActionResult) {
        if (result.getException() != null) {
            throw map(result, result.getException()!!)
        }
        if (result.getFailure() != null) {
            throw map(result, (payloadSerializer.deserialize(result.getFailure()) as java.lang.RuntimeException?)!!)
        }
    }

    private fun map(result: BuildActionResult, exception: RuntimeException): RuntimeException {
        // Wrap build failure in 'cancelled' cross version exception
        if (result.wasCancelled()) {
            throw InternalBuildCancelledException(exception)
        }

        // Forward special cases directly to consumer
        if (exception is InternalTestExecutionException || exception is InternalBuildActionFailureException || exception is InternalUnsupportedModelException) {
            return exception
        }

        // Wrap in generic 'build failed' cross version exception
        throw BuildExceptionVersion1(exception)
    }

    private fun createExecutor(operationParameters: ProviderOperationParameters, params: Parameters): BuildActionExecutor<ConnectionOperationParameters, ClientBuildRequestContext> {
        val loggingManager: LoggingManagerInternal?
        val executor: BuildActionExecutor<BuildActionParameters, ClientBuildRequestContext>?
        var standardInput: InputStream? = operationParameters.standardInput
        if (standardInput == null) {
            standardInput = SafeStreams.emptyInput()
        }
        val stoppable = CompositeStoppable()
        if (Boolean.TRUE == operationParameters.isEmbedded) {
            loggingManager = sharedServices.get<LoggingManagerFactory>(LoggingManagerFactory::class.java).createLoggingManager()
            loggingManager.captureSystemSources()
            executor = RunInProcess(
                SystemPropertySetterExecuter(
                    ForwardStdInToThisProcess(
                        sharedServices.get<GlobalUserInputReceiver>(GlobalUserInputReceiver::class.java),
                        sharedServices.get<UserInputReader>(UserInputReader::class.java),
                        standardInput,
                        sharedServices.get<BuildExecutor>(BuildExecutor::class.java)
                    )
                )
            )
        } else {
            val requestSpecificLogging = LoggingServiceRegistry.newNestedLogging()
            loggingManager = requestSpecificLogging.get<LoggingManagerFactory>(LoggingManagerFactory::class.java).createLoggingManager()
            val clientServices = daemonClientFactory.createBuildClientServices(
                requestSpecificLogging,
                params.daemonParams,
                params.requestContext,
                params.buildLayout.toLayoutConfiguration(),
                standardInput,
                Optional.ofNullable<InternalBuildProgressListener>(operationParameters.buildProgressListener)
            )
            stoppable.add(clientServices)
            stoppable.add(requestSpecificLogging)
            executor = clientServices.get<DaemonClient>(DaemonClient::class.java)
        }
        return LoggingBridgingBuildActionExecuter(DaemonBuildActionExecuter(executor), loggingManager, stoppable)
    }

    private fun initParams(operationParameters: ProviderOperationParameters): Parameters {
        val commandLineParser = CommandLineParser()
        commandLineParser.allowUnknownOptions()
        commandLineParser.allowMixedSubcommandsAndOptions()

        val initialPropertiesConverter = InitialPropertiesConverter()
        val buildLayoutConverter = BuildLayoutConverter()
        initialPropertiesConverter.configure(commandLineParser)
        buildLayoutConverter.configure(commandLineParser)

        val parsedCommandLine: ParsedCommandLine? = commandLineParser.parse(if (operationParameters.arguments == null) mutableListOf<T>() else operationParameters.arguments)

        val initialProperties = initialPropertiesConverter.convert(parsedCommandLine)
        val buildLayoutResult = buildLayoutConverter.convert(initialProperties, parsedCommandLine, operationParameters.projectDir, Consumer { layout: BuildLayoutParameters? ->
            if (operationParameters.gradleUserHomeDir != null) {
                layout!!.setGradleUserHomeDir(operationParameters.gradleUserHomeDir)
            }
            layout!!.setProjectDir(operationParameters.projectDir)
        })

        var properties = LayoutToPropertiesConverter(buildLayoutFactory).convert(initialProperties, buildLayoutResult)

        // Either use the environment variables from the operation parameters, or inherit the current process environment
        var environmentVariables: MutableMap<String, String>? = operationParameters.getEnvironmentVariables(null)
        if (environmentVariables == null) {
            environmentVariables = HashMap<String, String>(System.getenv())
        }

        val daemonParams = DaemonParameters(buildLayoutResult.getGradleUserHomeDir(), fileCollectionFactory, mutableMapOf<String, String>(), environmentVariables)
        DaemonBuildOptions().propertiesConverter().convert(properties.getProperties(), daemonParams)
        if (operationParameters.daemonBaseDir != null) {
            daemonParams.setBaseDir(operationParameters.daemonBaseDir)
        }

        // Since 8.13, we split the daemon JVM args into base and additional JVM args (#31462)
        // When calling the following new method before 8.13 provider, it will fall back to the old behavior.
        try {
            val baseJvmArguments: MutableList<String>? = operationParameters.baseJvmArguments
            // Here, we consider `null` and `[]` as no-op.
            // See LongRunningOperation.setJvmArguments(java.lang.String...)
            if (baseJvmArguments != null && !baseJvmArguments.isEmpty()) {
                daemonParams.setJvmArgs(baseJvmArguments)
            }
            val additionalJvmArguments: MutableList<String>? = operationParameters.additionalJvmArguments
            if (additionalJvmArguments != null) {
                daemonParams.addJvmArgs(additionalJvmArguments)
            }
        } catch (ex: UnsupportedMethodException) {
            // If we get an exception, we are dealing with an older provider.
            val legacyJvmArguments: MutableList<String>? = operationParameters.jvmArguments
            if (legacyJvmArguments != null) {
                daemonParams.setJvmArgs(legacyJvmArguments)
            }
        }

        daemonParams.setRequestedJvmCriteriaFromMap(properties.getDaemonJvmProperties())

        // Include the system properties that are defined in the daemon JVM args
        properties = properties.merge(daemonParams.getSystemProperties())

        val javaHome: File? = operationParameters.javaHome
        if (javaHome != null) {
            daemonParams.setRequestedJvmCriteria(DaemonJvmCriteria.JavaHome(DaemonJvmCriteria.JavaHome.Source.TOOLING_API_CLIENT, javaHome))
        }

        val requestContext = daemonParams.toRequestContext()

        if (operationParameters.daemonMaxIdleTimeValue != null && operationParameters.daemonMaxIdleTimeUnits != null) {
            val idleTimeout = operationParameters.daemonMaxIdleTimeUnits.toMillis(operationParameters.daemonMaxIdleTimeValue) as Int
            daemonParams.setIdleTimeout(idleTimeout)
        }

        val effectiveSystemProperties: MutableMap<String, String> = HashMap<String, String>()
        val operationParametersSystemProperties: MutableMap<String, String>? = operationParameters.getSystemProperties(null)
        if (operationParametersSystemProperties != null) {
            effectiveSystemProperties.putAll(operationParametersSystemProperties)
            effectiveSystemProperties.putAll(daemonParams.getMutableAndImmutableSystemProperties())
        } else {
            GUtil.addToMap(effectiveSystemProperties, System.getProperties())
            effectiveSystemProperties.putAll(daemonParams.getMutableAndImmutableSystemProperties())
        }
        val startParameter = ProviderStartParameterConverter().toStartParameter(operationParameters, buildLayoutResult, properties, environmentVariables)
        if (requestContext.getJvmCriteria() is DaemonJvmCriteria.Spec) {
            startParameter.setDaemonJvmCriteriaConfigured(true)
        }

        val gradlePropertiesAsSeenByToolchains: MutableMap<String, String> = HashMap<String, String>()
        gradlePropertiesAsSeenByToolchains.putAll(properties.getProperties())
        gradlePropertiesAsSeenByToolchains.putAll(startParameter.getProjectProperties())
        BuildOptionBackedConverter<ToolchainConfiguration?>(forToolChainConfiguration()).convert(
            parsedCommandLine,
            gradlePropertiesAsSeenByToolchains,
            environmentVariables,
            daemonParams.getToolchainConfiguration()
        )

        return Parameters(daemonParams, buildLayoutResult, properties, effectiveSystemProperties, environmentVariables, startParameter, requestContext)
    }

    private class Parameters(
        val daemonParams: DaemonParameters,
        val buildLayout: BuildLayoutResult,
        val properties: AllProperties,
        val tapiSystemProperties: MutableMap<String, String>,
        val tapiEnvironmentVariables: MutableMap<String, String>,
        val startParameter: StartParameterInternal,
        val requestContext: DaemonRequestContext
    )

    private class BuildProgressListenerInvokingBuildEventConsumer(private val buildProgressListener: InternalBuildProgressListener) : BuildEventConsumer {
        override fun dispatch(event: Any) {
            if (event is InternalProgressEvent) {
                this.buildProgressListener.onEvent(event)
            }
        }
    }

    @VisibleForTesting
    internal class ProgressListenerConfiguration(
        @get:VisibleForTesting val clientSubscriptions: BuildEventSubscriptions,
        private val buildEventConsumer: BuildEventConsumer,
        private val failsafeWrapper: FailsafeBuildProgressListenerAdapter
    ) {
        private class SynchronizedConsumer(private val delegate: BuildEventConsumer) : BuildEventConsumer {
            override fun dispatch(message: Any) {
                synchronized(this) {
                    delegate.dispatch(message)
                }
            }
        }

        companion object {
            private val OPERATION_TYPE_MAPPING: MutableMap<String, OperationType> = ImmutableMap.builderWithExpectedSize<String, OperationType>(OperationType.entries.size)
                .put(InternalBuildProgressListener.TEST_EXECUTION, OperationType.TEST)
                .put(InternalBuildProgressListener.TASK_EXECUTION, OperationType.TASK)
                .put(InternalBuildProgressListener.WORK_ITEM_EXECUTION, OperationType.WORK_ITEM)
                .put(InternalBuildProgressListener.PROJECT_CONFIGURATION_EXECUTION, OperationType.PROJECT_CONFIGURATION)
                .put(InternalBuildProgressListener.TRANSFORM_EXECUTION, OperationType.TRANSFORM)
                .put(InternalBuildProgressListener.BUILD_EXECUTION, OperationType.GENERIC)
                .put(InternalBuildProgressListener.TEST_OUTPUT, OperationType.TEST_OUTPUT)
                .put(InternalBuildProgressListener.TEST_METADATA, OperationType.TEST_METADATA)
                .put(InternalBuildProgressListener.FILE_DOWNLOAD, OperationType.FILE_DOWNLOAD)
                .put(InternalBuildProgressListener.BUILD_PHASE, OperationType.BUILD_PHASE)
                .put(InternalBuildProgressListener.PROBLEMS, OperationType.PROBLEMS)
                .put(InternalBuildProgressListener.ROOT, OperationType.ROOT)
                .build()

            @VisibleForTesting
            fun from(
                providerParameters: ProviderOperationParameters,
                consumerVersion: GradleVersion,
                payloadSerializer: PayloadSerializer,
                isolatableSerializerRegistry: IsolatableSerializerRegistry
            ): ProgressListenerConfiguration {
                val buildProgressListener: InternalBuildProgressListener = providerParameters.buildProgressListener
                val operationTypes: MutableSet<OperationType> = toOperationTypes(buildProgressListener, consumerVersion)
                val clientSubscriptions = BuildEventSubscriptions(operationTypes)
                val progressListenerAdapter = FailsafeBuildProgressListenerAdapter(buildProgressListener)
                var buildEventConsumer = if (clientSubscriptions.isAnyOperationTypeRequested()) BuildProgressListenerInvokingBuildEventConsumer(progressListenerAdapter) else NoOpBuildEventConsumer()
                buildEventConsumer = ProblemAdditionalDataRemapper(payloadSerializer, buildEventConsumer, isolatableSerializerRegistry)
                buildEventConsumer = StreamedValueConsumer(providerParameters, payloadSerializer, buildEventConsumer)
                if (Boolean.TRUE == providerParameters.isEmbedded) {
                    // Contract requires build events are delivered by a single thread. This is taken care of by the daemon client when not in embedded mode
                    // Need to apply some synchronization when in embedded mode
                    buildEventConsumer = SynchronizedConsumer(buildEventConsumer)
                }
                return ProgressListenerConfiguration(clientSubscriptions, buildEventConsumer, progressListenerAdapter)
            }

            private fun toOperationTypes(buildProgressListener: InternalBuildProgressListener, consumerVersion: GradleVersion): MutableSet<OperationType> {
                if (buildProgressListener != null) {
                    val operationTypes: MutableSet<OperationType> = EnumSet.noneOf<OperationType>(OperationType::class.java)
                    for (operation in buildProgressListener.subscribedOperations) {
                        if (OPERATION_TYPE_MAPPING.containsKey(operation)) {
                            operationTypes.add(OPERATION_TYPE_MAPPING.get(operation)!!)
                        }
                    }
                    if (consumerVersion.compareTo(GradleVersion.version("5.1")) < 0) {
                        // Some types were split out of 'generic' type in 5.1, so include these when an older consumer requests 'generic'
                        if (operationTypes.contains(OperationType.GENERIC)) {
                            operationTypes.add(OperationType.PROJECT_CONFIGURATION)
                            operationTypes.add(OperationType.TRANSFORM)
                            operationTypes.add(OperationType.WORK_ITEM)
                        }
                    }
                    if (consumerVersion.compareTo(GradleVersion.version("7.3")) < 0) {
                        // Some types were split out of 'generic' type in 7.3, so include these when an older consumer requests 'generic'
                        if (operationTypes.contains(OperationType.GENERIC)) {
                            operationTypes.add(OperationType.FILE_DOWNLOAD)
                        }
                    }
                    if (consumerVersion.compareTo(GradleVersion.version("8.12")) < 0) {
                        // The root type was split out of 'generic' type in 8.12, so include it when an older consumer requests 'generic'
                        if (operationTypes.contains(OperationType.GENERIC)) {
                            operationTypes.add(OperationType.ROOT)
                        }
                    }
                    return operationTypes
                }
                return mutableSetOf<OperationType>()
            }
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(ProviderConnection::class.java)
        private fun reportableJavaHomeForBuild(params: Parameters): File {
            val daemonParameters = params.daemonParams
            val criteria = daemonParameters.getRequestedJvmCriteria()
            if (criteria is DaemonJvmCriteria.Spec) {
                // Gradle daemon properties have been defined
                // TODO: We don't know what this will be without searching.
                // We'll say it's the current JVM because we don't know any better for now.
                return Jvm.current().getJavaHome()
            } else if (criteria is DaemonJvmCriteria.JavaHome) {
                return criteria.getJavaHome()
            } else if (criteria is DaemonJvmCriteria.LauncherJvm) {
                return Jvm.current().getJavaHome()
            } else {
                throw IllegalStateException("Unknown DaemonJvmCriteria type: " + criteria.javaClass.getName())
            }
        }
    }
}
