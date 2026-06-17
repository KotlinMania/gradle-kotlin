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
package org.gradle.launcher.daemon.server

import com.google.common.collect.ImmutableList
import org.gradle.cache.internal.locklistener.FileLockContentionHandler
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.launcher.daemon.configuration.DaemonServerConfiguration
import org.gradle.launcher.daemon.server.expiry.AllDaemonExpirationStrategy
import org.gradle.launcher.daemon.server.expiry.AnyDaemonExpirationStrategy
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy
import org.gradle.launcher.daemon.server.health.HealthExpirationStrategy
import org.gradle.launcher.daemon.server.health.LowMemoryDaemonExpirationStrategy
import java.util.concurrent.TimeUnit

@ServiceScope(Scope.Global::class)
class MasterExpirationStrategy(
    daemon: Daemon,
    params: DaemonServerConfiguration,
    healthExpirationStrategy: HealthExpirationStrategy,
    fileLockContentionHandler: FileLockContentionHandler,
    listenerManager: ListenerManager
) : DaemonExpirationStrategy {
    private val strategy: DaemonExpirationStrategy

    init {
        val strategies = ImmutableList.builder<DaemonExpirationStrategy>()

        // Expire under high JVM memory or GC pressure
        strategies.add(healthExpirationStrategy)

        // If we can no longer communicate with the outside world, the daemon should expire
        strategies.add(FileLockContentionExpirationStrategy(fileLockContentionHandler))

        // Expire compatible, idle, not recently used Daemons after a short time
        strategies.add(
            AllDaemonExpirationStrategy(
                ImmutableList.of<DaemonExpirationStrategy>(
                    CompatibleDaemonExpirationStrategy(daemon),
                    DaemonIdleTimeoutExpirationStrategy(daemon, DUPLICATE_DAEMON_GRACE_PERIOD_MS, TimeUnit.MILLISECONDS),
                    NotMostRecentlyUsedDaemonExpirationStrategy(daemon)
                )
            )
        )

        // Expire after normal idle timeout
        strategies.add(DaemonIdleTimeoutExpirationStrategy(daemon, params.idleTimeout, TimeUnit.MILLISECONDS))

        // Expire recently unused Daemons when memory pressure is high
        addLowMemoryDaemonExpirationStrategyWhenSupported(daemon, strategies, listenerManager)

        // Expire when Daemon Registry becomes unreachable for some reason
        strategies.add(DaemonRegistryUnavailableExpirationStrategy(daemon))

        this.strategy = AnyDaemonExpirationStrategy(strategies.build())
    }

    private fun addLowMemoryDaemonExpirationStrategyWhenSupported(daemon: Daemon, strategies: ImmutableList.Builder<DaemonExpirationStrategy>, listenerManager: ListenerManager) {
        val lowMemoryDaemonExpirationStrategy = LowMemoryDaemonExpirationStrategy(0.05)
        listenerManager.addListener(lowMemoryDaemonExpirationStrategy)
        strategies.add(
            AllDaemonExpirationStrategy(
                ImmutableList.of<DaemonExpirationStrategy>(
                    DaemonIdleTimeoutExpirationStrategy(daemon, DUPLICATE_DAEMON_GRACE_PERIOD_MS, TimeUnit.MILLISECONDS),
                    NotMostRecentlyUsedDaemonExpirationStrategy(daemon),
                    lowMemoryDaemonExpirationStrategy
                )
            )
        )
    }

    override fun checkExpiration(): DaemonExpirationResult {
        return strategy.checkExpiration()!!
    }

    companion object {
        private const val DUPLICATE_DAEMON_GRACE_PERIOD_MS = 10000
    }
}
