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
package org.gradle.launcher.daemon.server

import com.google.common.collect.ImmutableList
import org.gradle.internal.buildprocess.BuildProcessState
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.instrumentation.agent.AgentStatus.Companion.of
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration
import org.gradle.launcher.daemon.registry.DaemonRegistryServices
import java.io.Closeable
import java.util.Arrays
import java.util.concurrent.atomic.AtomicReference

/**
 * Encapsulates the state of the daemon process.
 */
class DaemonProcessState(configuration: DaemonServerConfiguration, loggingServices: ServiceRegistry, loggingManager: LoggingManagerInternal) : Closeable {
    private val buildProcessState: BuildProcessState
    private val stopState = AtomicReference<DaemonStopState>()

    init {
        // Merge the daemon services into the build process services
        // It would be better to separate these into different scopes, but many things still assume that daemon services are available in the global scope,
        // so keep them merged as a migration step
        buildProcessState = BuildProcessState(
            !configuration.isSingleUse,
            of(configuration.isInstrumentationAgentAllowed),
            CurrentGradleInstallation.locate(),
            ImmutableList.of<ServiceRegistrationProvider>(
                DaemonServices(configuration, loggingManager),
                DaemonRegistryServices(configuration.baseDir)
            ),
            Arrays.asList<ServiceRegistry>(
                loggingServices,
                NativeServices.getInstance()
            )
        )
    }

    val services: ServiceRegistry
        get() = buildProcessState.getServices()

    fun stopped(stopState: DaemonStopState) {
        this.stopState.set(stopState)
    }

    override fun close() {
        if (stopState.get() == DaemonStopState.Forced) {
            // The daemon could not be stopped cleanly, so the services could still be doing work.
            // Don't attempt to stop the services, just stop this process
            return
        }

        // Daemon has finished work, so stop the services
        buildProcessState.close()
    }
}
