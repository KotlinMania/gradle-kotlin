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
package org.gradle.launcher.daemon.server.scaninfo

import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.Action
import org.gradle.internal.InternalBuildAdapter
import org.gradle.internal.event.ListenerManager
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationListener
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats
import java.util.concurrent.atomic.AtomicReference

class DefaultDaemonScanInfo(
    private val stats: DaemonRunningStats,
    private val idleTimeout: Long,
    private val singleRun: Boolean,
    private val daemonRegistry: DaemonRegistry,
    private val listenerManager: ListenerManager
) : DaemonScanInfo {
    override fun getNumberOfBuilds(): Int {
        return stats.getBuildCount()
    }

    override fun getStartedAt(): Long {
        return stats.startTime
    }

    override fun getIdleTimeout(): Long {
        return idleTimeout
    }

    override fun getNumberOfRunningDaemons(): Int {
        return daemonRegistry.getAll().size
    }

    override fun isSingleUse(): Boolean {
        return singleRun
    }

    override fun notifyOnUnhealthy(listener: Action<in String?>) {
        /*
            The semantics of this method are that the given action should be notified if the
            Daemon is going to be terminated at the end of this build.
            It is not a generic outlet for "expiry events".

            Ideally, the value given would describe the problem and not be phrased in terms of why we are shutting down,
            but this is a practical compromise born out of piggy backing on the expiration listener mechanism to implement it.
         */
        val daemonExpirationListenerReference = AtomicReference<DaemonExpirationListener?>()
        val daemonExpirationListener: DaemonExpirationListener = object : DaemonExpirationListener {
            override fun onExpirationEvent(result: DaemonExpirationResult) {
                if (result.status == DaemonExpirationStatus.GRACEFUL_EXPIRE) {
                    try {
                        listener.execute(result.reason)
                    } finally {
                        // there's a possibility that this listener is called concurrently with
                        // the build finished listener. If the message happens to be a graceful expire
                        // one, then there's a large risk that we create a deadlock, because we're trying to
                        // remove the same listener from 2 different notifications. To avoid this, we just
                        // set the reference to null, which says that we're taking care of removing the listener
                        if (daemonExpirationListenerReference.getAndSet(null) != null) {
                            listenerManager.removeListener(this)
                        }
                    }
                }
            }
        }
        daemonExpirationListenerReference.set(daemonExpirationListener)
        listenerManager.addListener(daemonExpirationListener)

        val buildListener: BuildAdapter = object : InternalBuildAdapter() {
            override fun buildFinished(result: BuildResult) {
                val daemonExpirationListener = daemonExpirationListenerReference.getAndSet(null)
                if (daemonExpirationListener != null) {
                    listenerManager.removeListener(daemonExpirationListener)
                }
                listenerManager.removeListener(this)
            }
        }
        listenerManager.addListener(buildListener)
    }
}
