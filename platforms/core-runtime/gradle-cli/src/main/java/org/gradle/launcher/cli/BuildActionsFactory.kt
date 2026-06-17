/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.launcher.cli

import com.google.common.annotations.VisibleForTesting
import org.gradle.StartParameter
import org.gradle.api.Action
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.tasks.userinput.UserInputReader
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.cli.CommandLineParser
import org.gradle.cli.ParsedCommandLine
import org.gradle.configuration.GradleLauncherMetaData
import org.gradle.initialization.layout.BuildLayoutConfiguration
import org.gradle.internal.Actions
import org.gradle.internal.SystemProperties
import org.gradle.internal.buildprocess.BuildProcessState
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.daemon.client.execution.ClientBuildRequestContext
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.instrumentation.agent.AgentInitializer
import org.gradle.internal.instrumentation.agent.AgentStatus
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.logging.console.GlobalUserInputReceiver
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.launcher.bootstrap.ExecutionListener
import org.gradle.launcher.daemon.bootstrap.ForegroundDaemonAction
import org.gradle.launcher.daemon.client.DaemonClient
import org.gradle.launcher.daemon.client.DaemonClientFactory
import org.gradle.launcher.daemon.client.DaemonClientGlobalServices
import org.gradle.launcher.daemon.client.DaemonStopClient
import org.gradle.launcher.daemon.client.ReportDaemonStatusClient
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.launcher.daemon.configuration.ForegroundDaemonConfiguration
import org.gradle.launcher.daemon.context.DaemonCompatibilitySpec
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.context.DaemonRequestContext
import org.gradle.launcher.daemon.context.DefaultDaemonContext
import org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria
import org.gradle.launcher.exec.BuildActionExecutor
import org.gradle.launcher.exec.BuildActionParameters
import org.gradle.launcher.exec.BuildExecutor
import org.gradle.launcher.exec.DefaultBuildActionParameters
import org.gradle.process.internal.CurrentProcess
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener
import org.gradle.tooling.internal.provider.ForwardStdInToThisProcess
import org.gradle.tooling.internal.provider.RunInProcess
import java.lang.management.ManagementFactory
import java.util.Arrays
import java.util.Optional
import java.util.Properties
import java.util.UUID

internal open class BuildActionsFactory(loggingServices: ServiceRegistry, basicServices: ServiceRegistry, currentGradleInstallation: CurrentGradleInstallation) : CommandLineActionCreator {
    private val loggingServices: ServiceRegistry
    private val fileCollectionFactory: FileCollectionFactory
    private val basicServices: ServiceRegistry?
    private val currentGradleInstallation: CurrentGradleInstallation

    init {
        this.basicServices = basicServices
        this.loggingServices = loggingServices
        this.fileCollectionFactory = basicServices.get<FileCollectionFactory>(FileCollectionFactory::class.java)
        this.currentGradleInstallation = currentGradleInstallation
    }

    override fun configureCommandLineParser(parser: CommandLineParser?) {
    }

    override fun createAction(parser: CommandLineParser?, commandLine: ParsedCommandLine?, parameters: Parameters): Action<in ExecutionListener?>? {
        val startParameter = parameters.getStartParameter()
        val daemonParameters = parameters.getDaemonParameters()
        val buildLayoutConfiguration = parameters.getBuildLayout().toLayoutConfiguration()

        if (daemonParameters.isStop()) {
            return Actions.toAction<ExecutionListener?>(stopAllDaemons(daemonParameters))
        }
        if (daemonParameters.isStatus()) {
            return Actions.toAction<ExecutionListener?>(showDaemonStatus(daemonParameters))
        }
        if (daemonParameters.isForeground()) {
            val conf = ForegroundDaemonConfiguration(
                UUID.randomUUID().toString(), daemonParameters.getBaseDir(), daemonParameters.getIdleTimeout(), daemonParameters.getPeriodicCheckInterval(), fileCollectionFactory,
                daemonParameters.shouldApplyInstrumentationAgent(), daemonParameters.getNativeServicesMode()
            )
            return Actions.toAction<ExecutionListener?>(ForegroundDaemonAction(loggingServices, conf))
        }

        val requestContext = daemonParameters.toRequestContext()
        if (daemonParameters.getRequestedJvmCriteria() is DaemonJvmCriteria.Spec) {
            startParameter.setDaemonJvmCriteriaConfigured(true)
        }

        if (daemonParameters.isEnabled()) {
            return Actions.toAction<ExecutionListener?>(runBuildWithDaemon(startParameter, daemonParameters, requestContext, buildLayoutConfiguration))
        }
        if (canUseCurrentProcess(daemonParameters, requestContext)) {
            return Actions.toAction<ExecutionListener?>(runBuildInProcess(startParameter, daemonParameters))
        }

        return Actions.toAction<ExecutionListener?>(runBuildInSingleUseDaemon(startParameter, daemonParameters, requestContext, buildLayoutConfiguration))
    }

    private fun stopAllDaemons(daemonParameters: DaemonParameters): Runnable {
        val clientSharedServices = createGlobalClientServices()
        val clientServices = clientSharedServices.get<DaemonClientFactory?>(DaemonClientFactory::class.java)!!.createMessageDaemonServices(loggingServices, daemonParameters.getBaseDir())
        val stopClient = clientServices.get<DaemonStopClient>(DaemonStopClient::class.java)
        return StopDaemonAction(stopClient)
    }

    private fun showDaemonStatus(daemonParameters: DaemonParameters): Runnable {
        val clientSharedServices = createGlobalClientServices()
        val clientServices = clientSharedServices.get<DaemonClientFactory?>(DaemonClientFactory::class.java)!!.createMessageDaemonServices(loggingServices, daemonParameters.getBaseDir())
        val statusClient = clientServices.get<ReportDaemonStatusClient>(ReportDaemonStatusClient::class.java)
        return ReportDaemonStatusAction(statusClient)
    }

    private fun runBuildWithDaemon(
        startParameter: StartParameterInternal,
        daemonParameters: DaemonParameters,
        requestContext: DaemonRequestContext?,
        buildLayoutConfiguration: BuildLayoutConfiguration?
    ): Runnable {
        // Create a client that will match based on the daemon startup parameters.
        val clientSharedServices = createGlobalClientServices()
        val clientServices = clientSharedServices.get<DaemonClientFactory?>(DaemonClientFactory::class.java)!!
            .createBuildClientServices(loggingServices, daemonParameters, requestContext, buildLayoutConfiguration, System.`in`, Optional.empty<InternalBuildProgressListener?>())
        val client = clientServices.get<DaemonClient>(DaemonClient::class.java)
        return runBuildAndCloseServices(startParameter, daemonParameters, client, clientSharedServices, clientServices)
    }

    protected open fun canUseCurrentProcess(daemonParameters: DaemonParameters?, requestContext: DaemonRequestContext): Boolean {
        // Pretend like the current process is actually a daemon, and see if it satisfies the compatibility spec
        val currentProcess = CurrentProcess(fileCollectionFactory)
        val contextForCurrentProcess: DaemonContext = buildDaemonContextForCurrentProcess(requestContext, currentProcess)

        val comparison = DaemonCompatibilitySpec(requestContext)
        if (currentProcess.isLowMemoryProcess()) {
            LOGGER.info(CAN_USE_CURRENT_PROCESS_MESSAGE, "The maximum heap size is insufficient.\n")
            return false
        }
        val whyUnsatisfied = comparison.whyUnsatisfied(contextForCurrentProcess)
        if (whyUnsatisfied != null) {
            LOGGER.info(CAN_USE_CURRENT_PROCESS_MESSAGE, whyUnsatisfied)
        }
        return whyUnsatisfied == null
    }

    private fun runBuildInProcess(startParameter: StartParameterInternal, daemonParameters: DaemonParameters): Runnable {
        // Set the system properties and use this process
        val properties = Properties()
        properties.putAll(daemonParameters.getEffectiveSystemProperties())
        System.setProperties(properties)

        val buildProcessState = BuildProcessState(
            startParameter.isContinuous(),
            AgentStatus.of(daemonParameters.shouldApplyInstrumentationAgent()),
            currentGradleInstallation,
            mutableSetOf<ServiceRegistrationProvider?>(),
            Arrays.asList<ServiceRegistry?>(
                loggingServices,
                NativeServices.getInstance()
            )
        )

        val globalServices = buildProcessState.getServices()
        globalServices.get<AgentInitializer?>(AgentInitializer::class.java)!!.maybeConfigureInstrumentationAgent()

        val executor: BuildActionExecutor<BuildActionParameters?, ClientBuildRequestContext?> = RunInProcess(
            ForwardStdInToThisProcess(
                globalServices.get<GlobalUserInputReceiver?>(GlobalUserInputReceiver::class.java)!!,
                globalServices.get<UserInputReader?>(UserInputReader::class.java)!!,
                System.`in`,
                globalServices.get<BuildExecutor?>(BuildExecutor::class.java)!!
            )
        )

        // Force the user home services to be stopped first, the dependencies between the user home services and the global services are not preserved currently
        return runBuildAndCloseServices(startParameter, daemonParameters, executor, buildProcessState.getServices(), buildProcessState)
    }

    private fun runBuildInSingleUseDaemon(
        startParameter: StartParameterInternal,
        daemonParameters: DaemonParameters,
        requestContext: DaemonRequestContext?,
        buildLayoutConfiguration: BuildLayoutConfiguration?
    ): Runnable {
        //(SF) this is a workaround until this story is completed. I'm hardcoding setting the idle timeout to be max X mins.
        //this way we avoid potential runaway daemons that steal resources on linux and break builds on windows.
        //We might leave that in if we decide it's a good idea for an extra safety net.
        val maxTimeout = 2 * 60 * 1000
        if (daemonParameters.getIdleTimeout() > maxTimeout) {
            daemonParameters.setIdleTimeout(maxTimeout)
        }

        //end of workaround.

        // Create a client that will not match any existing daemons, so it will always start a new one
        val clientSharedServices = createGlobalClientServices()
        val clientServices = clientSharedServices.get<DaemonClientFactory?>(DaemonClientFactory::class.java)!!
            .createSingleUseDaemonClientServices(clientSharedServices, daemonParameters, requestContext, buildLayoutConfiguration, System.`in`)
        val client = clientServices.get<DaemonClient>(DaemonClient::class.java)
        return runBuildAndCloseServices(startParameter, daemonParameters, client, clientSharedServices, clientServices)
    }

    private fun createGlobalClientServices(): ServiceRegistry {
        val builder = ServiceRegistryBuilder.builder()
            .displayName("Daemon client global services")
            .parent(NativeServices.getInstance())
        builder.parent(basicServices!!)
        return builder.provider(DaemonClientGlobalServices()).build()
    }

    private fun runBuildAndCloseServices(
        startParameter: StartParameterInternal,
        daemonParameters: DaemonParameters,
        executor: BuildActionExecutor<BuildActionParameters?, ClientBuildRequestContext?>?,
        sharedServices: ServiceRegistry,
        vararg stopBeforeSharedServices: Any?
    ): Runnable {
        val parameters = createBuildActionParameters(startParameter, daemonParameters)
        val stoppable: Stoppable = CompositeStoppable().add(*stopBeforeSharedServices).add(sharedServices)
        return RunBuildAction(executor, startParameter, clientMetaData(), this.buildStartTime, parameters, sharedServices, stoppable)
    }

    private fun createBuildActionParameters(startParameter: StartParameter, daemonParameters: DaemonParameters): BuildActionParameters {
        return DefaultBuildActionParameters(
            daemonParameters.getEffectiveSystemProperties(),
            daemonParameters.getEnvironmentVariables(),
            SystemProperties.getInstance().getCurrentDir(),
            startParameter.getLogLevel(),
            daemonParameters.isEnabled(),
            ClassPath.EMPTY
        )
    }

    private val buildStartTime: Long
        get() = ManagementFactory.getRuntimeMXBean().getStartTime()

    private fun clientMetaData(): GradleLauncherMetaData {
        return GradleLauncherMetaData()
    }

    companion object {
        private val LOGGER: Logger = Logging.getLogger(BuildActionsFactory::class.java)
        private const val CAN_USE_CURRENT_PROCESS_MESSAGE = "The current JVM process isn't compatible with build requirement. {}"

        @VisibleForTesting
        fun buildDaemonContextForCurrentProcess(requestContext: DaemonRequestContext, currentProcess: CurrentProcess): DaemonContext {
            return DefaultDaemonContext(
                UUID.randomUUID().toString(),
                currentProcess.jvm.getJavaHome(),
                JavaLanguageVersion.current(),
                Jvm.current().getVendor(),
                null, 0L, 0,
                currentProcess.getJvmOptions().getAllImmutableJvmArgs(),
                AgentStatus.allowed().isAgentInstrumentationEnabled,  // These aren't being properly checked.
                // We assume the current process is compatible when considering these properties.
                requestContext.getNativeServicesMode(),
                requestContext.getPriority()
            )
        }
    }
}
