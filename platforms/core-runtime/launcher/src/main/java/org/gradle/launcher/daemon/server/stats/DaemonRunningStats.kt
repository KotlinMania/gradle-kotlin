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
package org.gradle.launcher.daemon.server.stats

import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.time.Time
import kotlin.math.max

@ServiceScope(Scope.Global::class)
class DaemonRunningStats {
    val startTime: Long = System.currentTimeMillis()
    private val daemonTimer = Time.startTimer()
    private val currentBuildTimer = Time.startTimer()

    var buildCount: Int = 0
        private set
    var allBuildsTime: Long = 0
        private set

    val prettyUpTime: String
        get() = daemonTimer.elapsed

    // TODO: these should be moved off to a separate type
    fun buildStarted() {
        ++buildCount
        currentBuildTimer.reset()
    }

    fun buildFinished() {
        val buildTime = max(currentBuildTimer.elapsedMillis, 1)
        allBuildsTime += buildTime
    }
}
