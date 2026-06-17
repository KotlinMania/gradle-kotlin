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
import com.google.common.base.Functions
import com.google.common.base.Preconditions
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy
import java.util.concurrent.TimeUnit

class DaemonIdleTimeoutExpirationStrategy(private val daemon: Daemon, timeoutClosure: Function<*, Long>) : DaemonExpirationStrategy {
    private val idleTimeout: Function<*, Long>

    constructor(daemon: Daemon, idleTimeout: Int, timeUnit: TimeUnit) : this(daemon, Functions.constant<Long>(timeUnit.toMillis(idleTimeout.toLong())))

    init {
        this.idleTimeout = Preconditions.checkNotNull(timeoutClosure)
    }

    override fun checkExpiration(): DaemonExpirationResult {
        val idleMillis = daemon.getStateCoordinator().getIdleMillis()
        val idleTimeoutExceeded = idleMillis > idleTimeout.apply(null)
        if (idleTimeoutExceeded) {
            return DaemonExpirationResult(DaemonExpirationStatus.QUIET_EXPIRE, EXPIRATION_REASON + " for " + (idleMillis / 60000) + " minutes")
        }
        return DaemonExpirationResult.NOT_TRIGGERED
    }

    companion object {
        const val EXPIRATION_REASON: String = "after being idle"
    }
}
