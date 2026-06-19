/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.logging.slf4j

import org.gradle.api.logging.LogLevel
import org.gradle.internal.operations.OperationIdentifier

internal class MessageRewritingBuildOperationAwareLogger(private val delegate: BuildOperationAwareLogger, val messageRewriter: ContextAwareTaskLogger.MessageRewriter?) : BuildOperationAwareLogger() {
    override fun log(logLevel: LogLevel?, throwable: Throwable?, message: String?, operationIdentifier: OperationIdentifier?) {
        if (messageRewriter == null) {
            delegate.log(logLevel, throwable, message, operationIdentifier)
            return
        }
        val rewrittenMessage = messageRewriter.rewrite(logLevel, message)
        if (rewrittenMessage == null) {
            return
        }
        delegate.log(logLevel, throwable, rewrittenMessage, operationIdentifier)
    }

    override fun isLevelAtMost(level: LogLevel?): Boolean {
        return delegate.isLevelAtMost(level)
    }

    override fun getName(): String? {
        return delegate.getName()
    }
}
