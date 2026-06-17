/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.internal.instrumentation.agent

import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * A build service to query the status of the Gradle's Java agents. Prefer using this service to accessing the [AgentControl] directly.
 */
@ServiceScope(Scope.Global::class)
interface AgentStatus {
    /**
     * Checks if the agent-based bytecode instrumentation is enabled for the current JVM process.
     *
     * @return `true` if the agent instrumentation should be used
     */
    @JvmField
    val isAgentInstrumentationEnabled: Boolean

    companion object {
        /**
         * Returns an AgentStatus instance that enables instrumentation if the agent is available.
         */
        fun allowed(): AgentStatus {
            return DefaultAgentStatus()
        }

        /**
         * Returns an AgentStatus instance that disables instrumentation regardless of the agent's availability.
         */
        @JvmStatic
        fun disabled(): AgentStatus {
            return DisabledAgentStatus()
        }

        /**
         * Returns [.allowed] or [.disabled] AgentStatus according to the value of the flag.
         *
         * @param agentInstrumentationAllowed if `true` then the returned status allows instrumentation
         */
        @JvmStatic
        fun of(agentInstrumentationAllowed: Boolean): AgentStatus {
            return if (agentInstrumentationAllowed) allowed() else disabled()
        }
    }
}
