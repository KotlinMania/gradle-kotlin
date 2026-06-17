/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.launcher.daemon.bootstrap

import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.instrumentation.agent.AgentInitializer
import org.gradle.internal.logging.LoggingManagerFactory
import org.gradle.internal.service.ServiceRegistry
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.server.Daemon
import org.gradle.launcher.daemon.server.DaemonProcessState
import org.gradle.launcher.daemon.server.MasterExpirationStrategy
import org.gradle.launcher.daemon.server.api.DaemonState
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy

class ForegroundDaemonAction(private val loggingRegistry: ServiceRegistry, private val configuration: DaemonServerConfiguration) : Runnable {
    override fun run() {
        val loggingManager = loggingRegistry.get<LoggingManagerFactory?>(LoggingManagerFactory::class.java)!!.createLoggingManager()
        loggingManager.start()

        val daemonProcessState = DaemonProcessState(configuration, loggingRegistry, loggingManager)
        val daemonServices = daemonProcessState.getServices()
        val daemon = daemonServices.get<Daemon>(Daemon::class.java)
        val daemonRegistry = daemonServices.get<DaemonRegistry>(DaemonRegistry::class.java)
        val expirationStrategy: DaemonExpirationStrategy = daemonServices.get<MasterExpirationStrategy>(MasterExpirationStrategy::class.java)
        daemonServices.get<AgentInitializer?>(AgentInitializer::class.java)!!.maybeConfigureInstrumentationAgent()

        daemon.start()

        try {
            daemonRegistry.markState(daemon.getAddress(), DaemonState.Idle)
            daemon.stopOnExpiration(expirationStrategy, configuration.getPeriodicCheckIntervalMs())
        } finally {
            CompositeStoppable.stoppable(daemon, daemonProcessState).stop()
        }
    }
}
