/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.platform.base.internal.toolchain

import org.gradle.internal.logging.text.DiagnosticsVisitor
import org.gradle.internal.logging.text.TreeFormatter

class ToolChainAvailability : ToolSearchResult {
    private var reason: ToolSearchResult? = null

    override fun isAvailable(): Boolean {
        return reason == null
    }

    val unavailableMessage: String
        get() {
            val formatter = TreeFormatter()
            this.explain(formatter)
            return formatter.toString()
        }

    override fun explain(visitor: DiagnosticsVisitor) {
        reason!!.explain(visitor)
    }

    fun unavailable(unavailableMessage: String): ToolChainAvailability {
        if (reason == null) {
            reason = FixedMessageToolSearchResult(unavailableMessage)
        }
        return this
    }

    fun unsupported(unsupportedMessage: String): ToolChainAvailability {
        if (reason == null) {
            reason = FixedMessageToolSearchResult(unsupportedMessage)
        }

        return this
    }

    fun mustBeAvailable(tool: ToolSearchResult): ToolChainAvailability {
        if (!tool.isAvailable() && reason == null) {
            reason = tool
        }
        return this
    }

    private class FixedMessageToolSearchResult(private val message: String) : ToolSearchResult {
        override fun isAvailable(): Boolean {
            return false
        }

        override fun explain(visitor: DiagnosticsVisitor) {
            visitor.node(message)
        }
    }
}
