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
package org.gradle.launcher.daemon.server.expiry

import com.google.common.base.Joiner

/**
 * Expires the daemon if any of the children would expire the daemon.
 */
class AnyDaemonExpirationStrategy(expirationStrategies: MutableList<DaemonExpirationStrategy>) : DaemonExpirationStrategy {
    private val expirationStrategies: Iterable<DaemonExpirationStrategy>

    init {
        this.expirationStrategies = expirationStrategies
    }

    override fun checkExpiration(): DaemonExpirationResult {
        var expirationResult: DaemonExpirationResult
        var expirationStatus = DaemonExpirationStatus.DO_NOT_EXPIRE
        val reasons: MutableList<String?> = ArrayList<String?>()

        for (expirationStrategy in expirationStrategies) {
            expirationResult = expirationStrategy.checkExpiration()
            if (expirationResult.getStatus() != DaemonExpirationStatus.DO_NOT_EXPIRE) {
                reasons.add(expirationResult.getReason())
                expirationStatus = DaemonExpirationStatus.highestPriorityOf(expirationResult.getStatus(), expirationStatus)
            }
        }

        if (expirationStatus == DaemonExpirationStatus.DO_NOT_EXPIRE) {
            return DaemonExpirationResult.Companion.NOT_TRIGGERED
        } else {
            return DaemonExpirationResult(expirationStatus, Joiner.on(" and ").skipNulls().join(reasons))
        }
    }
}
