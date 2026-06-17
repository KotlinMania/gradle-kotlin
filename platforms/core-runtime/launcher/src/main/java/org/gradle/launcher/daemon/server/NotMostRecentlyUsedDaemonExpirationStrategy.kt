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

import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.registry.DaemonInfo
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy
import java.util.Date

class NotMostRecentlyUsedDaemonExpirationStrategy internal constructor(private val daemon: Daemon) : DaemonExpirationStrategy {
    override fun checkExpiration(): DaemonExpirationResult {
        if (!isMostRecentlyUsed(daemon.getDaemonRegistry().getIdle(), daemon.getDaemonContext())) {
            return DaemonExpirationResult(DaemonExpirationStatus.GRACEFUL_EXPIRE, EXPIRATION_REASON)
        }
        return DaemonExpirationResult.NOT_TRIGGERED
    }

    private fun isMostRecentlyUsed(daemonInfos: MutableCollection<DaemonInfo>, thisDaemonContext: DaemonContext): Boolean {
        var mruUid: String? = null
        var mruTimestamp = Date(Long.MIN_VALUE)
        for (daemonInfo in daemonInfos) {
            val daemonAccessTime = daemonInfo.getLastBusy()
            if (daemonAccessTime.after(mruTimestamp)) {
                mruUid = daemonInfo.getUid()
                mruTimestamp = daemonAccessTime
            }
        }
        return thisDaemonContext.getUid() == mruUid
    }

    companion object {
        const val EXPIRATION_REASON: String = "not recently used"
    }
}
