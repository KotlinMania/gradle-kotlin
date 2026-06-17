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
package org.gradle.launcher.daemon.server

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.tasks.userinput.UserInputReader
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.cache.internal.locklistener.FileLockContentionHandler
import org.gradle.internal.FileUtils
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.instrumentation.agent.AgentStatus
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.internal.remote.internal.inet.InetAddressFactory
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.context.DefaultDaemonContext
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics
import org.gradle.launcher.daemon.protocol.DaemonMessageSerializer
import org.gradle.launcher.daemon.registry.DaemonDir
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.server.api.DaemonCommandAction
import org.gradle.launcher.daemon.server.api.HandleInvalidateVirtualFileSystem
import org.gradle.launcher.daemon.server.api.HandleReportStatus
import org.gradle.launcher.daemon.server.api.HandleStop
import org.gradle.launcher.daemon.server.exec.ApplyClientEnvironmentVariables
import org.gradle.launcher.daemon.server.exec.CleanUpVirtualFileSystemAfterBuild
import org.gradle.launcher.daemon.server.exec.DaemonCommandExecuter
import org.gradle.launcher.daemon.server.exec.EstablishBuildEnvironment
import org.gradle.launcher.daemon.server.exec.ExecuteBuild
import org.gradle.launcher.daemon.server.exec.ForwardClientInput
import org.gradle.launcher.daemon.server.exec.HandleCancel
import org.gradle.launcher.daemon.server.exec.LogAndCheckHealth
import org.gradle.launcher.daemon.server.exec.LogToClient
import org.gradle.launcher.daemon.server.exec.RequestStopIfSingleUsedDaemon
import org.gradle.launcher.daemon.server.exec.ResetDeprecationLogger
import org.gradle.launcher.daemon.server.exec.ReturnResult
import org.gradle.launcher.daemon.server.exec.StartBuildOrRespondWithBusy
import org.gradle.launcher.daemon.server.exec.WatchForDisconnection
import org.gradle.launcher.daemon.server.health.DaemonHealthCheck
import org.gradle.launcher.daemon.server.health.DaemonHealthStats
import org.gradle.launcher.daemon.server.health.HealthExpirationStrategy
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectorMonitoringStrategy
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectorMonitoringStrategy.Companion.determineGcStrategy
import org.gradle.launcher.daemon.server.scaninfo.DaemonScanInfo
import org.gradle.launcher.daemon.server.scaninfo.DefaultDaemonScanInfo
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats
import org.gradle.launcher.exec.BuildExecutor
import org.gradle.tooling.internal.provider.action.BuildActionSerializer
import java.io.File

/**
 * Takes care of instantiating and wiring together the services required by the daemon server.
 */
class DaemonServices(private val configuration: DaemonServerConfiguration, private val loggingManager: LoggingManagerInternal) : ServiceRegistrationProvider {
    @Provides
    protected fun createDaemonContext(agentStatus: AgentStatus, processEnvironment: ProcessEnvironment): DaemonContext {
        LOGGER.debug("Creating daemon context with opts: {}", configuration.jvmOptions)
        return DefaultDaemonContext(
            configuration.uid,
            FileUtils.canonicalize(Jvm.current().getJavaHome()),
            JavaLanguageVersion.current(),
            Jvm.current().getVendor(),
            configuration.baseDir,
            processEnvironment.maybeGetPid(),
            configuration.idleTimeout,
            configuration.jvmOptions,
            agentStatus.isAgentInstrumentationEnabled,
            configuration.nativeServicesMode,
            configuration.priority
        )
    }

    @Provides
    protected fun createDaemonLogFile(daemonContext: DaemonContext, daemonDir: DaemonDir): DaemonLogFile {
        val pid = daemonContext.getPid()
        return DaemonLogFile(File(daemonDir.getVersionedDir(), DaemonLogFile.getDaemonLogFileName(pid)))
    }

    @Provides
    protected fun createDaemonHealthCheck(listenerManager: ListenerManager, healthExpirationStrategy: HealthExpirationStrategy): DaemonHealthCheck {
        return DaemonHealthCheck(healthExpirationStrategy, listenerManager)
    }

    @Provides
    protected fun createDaemonRunningStats(): DaemonRunningStats {
        return DaemonRunningStats()
    }

    @Provides
    protected fun createDaemonScanInfo(runningStats: DaemonRunningStats, listenerManager: ListenerManager, daemonRegistry: DaemonRegistry): DaemonScanInfo {
        return DefaultDaemonScanInfo(runningStats, configuration.idleTimeout, configuration.isSingleUse, daemonRegistry, listenerManager)
    }

    @Provides
    protected fun createMasterExpirationStrategy(
        daemon: Daemon,
        healthExpirationStrategy: HealthExpirationStrategy,
        fileLockContentionHandler: FileLockContentionHandler,
        listenerManager: ListenerManager
    ): MasterExpirationStrategy {
        return MasterExpirationStrategy(daemon, configuration, healthExpirationStrategy, fileLockContentionHandler, listenerManager)
    }

    @Provides
    protected fun createHealthExpirationStrategy(stats: DaemonHealthStats, strategy: GarbageCollectorMonitoringStrategy): HealthExpirationStrategy {
        return HealthExpirationStrategy(stats, strategy)
    }

    @Provides
    protected fun createDaemonHealthStats(runningStats: DaemonRunningStats, strategy: GarbageCollectorMonitoringStrategy, executorFactory: ExecutorFactory): DaemonHealthStats {
        return DaemonHealthStats(runningStats, strategy, executorFactory)
    }

    @Provides
    protected fun createGarbageCollectorMonitoringStrategy(): GarbageCollectorMonitoringStrategy {
        return determineGcStrategy()!!
    }

    @Provides
    protected fun createDaemonCommandActions(
        buildActionExecuter: BuildExecutor,
        daemonContext: DaemonContext,
        healthCheck: DaemonHealthCheck,
        healthStats: DaemonHealthStats,
        runningStats: DaemonRunningStats,
        executorFactory: ExecutorFactory,
        processEnvironment: ProcessEnvironment,
        inputReader: UserInputReader,
        eventDispatch: OutputEventListener,
        daemonLogFile: DaemonLogFile,
        userHomeServiceRegistry: GradleUserHomeScopeServiceRegistry,
        listenerManager: ListenerManager
    ): ImmutableList<DaemonCommandAction> {
        val daemonDiagnostics = DaemonDiagnostics(daemonLogFile.getFile(), daemonContext.getPid())
        return ImmutableList.of<DaemonCommandAction>(
            HandleStop(listenerManager),
            HandleInvalidateVirtualFileSystem(userHomeServiceRegistry),
            HandleCancel(),
            HandleReportStatus(),
            CleanUpVirtualFileSystemAfterBuild(executorFactory, userHomeServiceRegistry),
            ReturnResult(),
            StartBuildOrRespondWithBusy(daemonDiagnostics),  // from this point down, the daemon is 'busy'
            EstablishBuildEnvironment(processEnvironment),
            LogToClient(loggingManager, daemonDiagnostics),  // from this point down, logging is sent back to the client
            ApplyClientEnvironmentVariables(processEnvironment),
            LogAndCheckHealth(healthStats, healthCheck, runningStats),
            ForwardClientInput(inputReader, eventDispatch),
            RequestStopIfSingleUsedDaemon(),
            ResetDeprecationLogger(),
            WatchForDisconnection(),
            ExecuteBuild(buildActionExecuter, runningStats)
        )
    }

    @Provides
    fun createBuildActionSerializer(): Serializer<BuildAction> {
        return BuildActionSerializer.create()
    }

    @Provides
    protected fun createDaemon(
        actions: ImmutableList<DaemonCommandAction>,
        buildActionSerializer: Serializer<BuildAction>,
        executorFactory: ExecutorFactory,
        inetAddressFactory: InetAddressFactory,
        daemonRegistry: DaemonRegistry,
        daemonContext: DaemonContext,
        listenerManager: ListenerManager
    ): Daemon {
        return Daemon(
            DaemonTcpServerConnector(
                executorFactory,
                inetAddressFactory,
                DaemonMessageSerializer.create(buildActionSerializer)
            ),
            daemonRegistry,
            daemonContext,
            DaemonCommandExecuter(configuration, actions),
            executorFactory,
            listenerManager
        )
    }

    companion object {
        private val LOGGER: Logger = Logging.getLogger(DaemonServices::class.java)
    }
}
