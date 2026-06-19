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
package org.gradle.internal.jvm.inspection

import com.google.common.collect.Sets
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.jspecify.annotations.NullMarked
import java.util.Objects

/**
 * A BuildSession-scoped service that reports JVM installation problems.
 *
 *
 *
 * This cannot be merged in to [DefaultJavaInstallationRegistry] as we must rediscover JVM installations on each build invocation,
 * as e.g. the `GradleBuild` task could be used to change the system properties and environment variables that affect the JVM installation.
 *
 */
@NullMarked
@ServiceScope(Scope.BuildSession::class)
class JvmInstallationProblemReporter {
    private class ProblemReport(// Include auto-detection as it affects visibility of the problem. We do want to report twice if a location was auto-detected and then explicitly configured.
        val autoDetected: Boolean, private val problem: String
    ) {
        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as ProblemReport
            return autoDetected == that.autoDetected && problem == that.problem
        }

        override fun hashCode(): Int {
            return Objects.hash(autoDetected, problem)
        }

        override fun toString(): String {
            return "ProblemReport{autoDetected=" + autoDetected + ", problem='" + problem + "'}"
        }
    }

    private val reportedProblems: MutableSet<ProblemReport> = Sets.newConcurrentHashSet<ProblemReport>()

    fun reportProblemIfNeeded(targetLogger: Logger, installationLocation: InstallationLocation, message: String) {
        val key = ProblemReport(installationLocation.isAutoDetected, message)
        if (!reportedProblems.add(key)) {
            return
        }
        // If a user has explicitly configured a java installation, we should always log problems with it visibly, because they have bad configuration they can change.
        // But if we are just locating it automatically, we should log problems less visibly, because the user may be unable to fix the problem.
        targetLogger.log(if (key.autoDetected) LogLevel.INFO else LogLevel.WARN, message)
    }
}
