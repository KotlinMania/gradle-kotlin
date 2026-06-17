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

import com.google.common.base.Function
import com.google.common.collect.Lists
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.launcher.daemon.registry.DaemonDir
import org.gradle.launcher.daemon.registry.DaemonInfo
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy

class DaemonRegistryUnavailableExpirationStrategy(private val daemon: Daemon) : DaemonExpirationStrategy {
    private var lastModified: Long = -1

    override fun checkExpiration(): DaemonExpirationResult {
        try {
            val daemonContext = daemon.getDaemonContext()
            val daemonRegistryDir = daemonContext.getDaemonRegistryDir()
            val registry = DaemonDir(daemonRegistryDir).getRegistry()
            val newLastModified = registry.lastModified()

            if (lastModified != newLastModified) {
                lastModified = newLastModified
                if (!registry.canRead()) {
                    LOG.warn("Daemon registry {} became unreadable. Expiring daemon.", daemonRegistryDir)
                    return DaemonExpirationResult(DaemonExpirationStatus.GRACEFUL_EXPIRE, REGISTRY_BECAME_UNREADABLE)
                } else {
                    // Check that given daemon still exists in registry - a daemon registry could be removed and recreated between checks
                    val allDaemonPids = Lists.transform<DaemonInfo, Long>(daemon.getDaemonRegistry().getAll(), object : Function<DaemonInfo, Long> {
                        override fun apply(info: DaemonInfo): Long {
                            return info.getPid()
                        }
                    })
                    if (!allDaemonPids.contains(daemonContext.getPid())) {
                        return DaemonExpirationResult(DaemonExpirationStatus.GRACEFUL_EXPIRE, REGISTRY_ENTRY_UNEXPECTEDLY_LOST)
                    }
                }
            }
        } catch (se: SecurityException) {
            LOG.warn("Daemon registry became inaccessible. Expiring daemon. Error message is '{}'", se.message)
            return DaemonExpirationResult(DaemonExpirationStatus.GRACEFUL_EXPIRE, REGISTRY_BECAME_INACCESSIBLE)
        }
        return DaemonExpirationResult.NOT_TRIGGERED
    }

    companion object {
        private val LOG: Logger = Logging.getLogger(DaemonRegistryUnavailableExpirationStrategy::class.java)
        const val REGISTRY_BECAME_UNREADABLE: String = "after the daemon registry became unreadable"
        const val REGISTRY_ENTRY_UNEXPECTEDLY_LOST: String = "after the daemon was no longer found in the daemon registry"
        const val REGISTRY_BECAME_INACCESSIBLE: String = "after the daemon registry became inaccessible"
    }
}

