/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer.connection

import org.gradle.internal.logging.events.ProgressCompleteEvent
import org.gradle.internal.logging.events.ProgressEvent
import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.logging.progress.ProgressListener
import org.gradle.tooling.internal.consumer.LoggingProvider
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1

/**
 * Provides some high-level progress information.
 */
class ProgressLoggingConsumerActionExecutor(private val actionExecutor: ConsumerActionExecutor, private val loggingProvider: LoggingProvider) : ConsumerActionExecutor {
    override fun stop() {
        actionExecutor.stop()
    }

    override fun getDisplayName(): String {
        return actionExecutor.getDisplayName()
    }

    @Throws(UnsupportedOperationException::class, IllegalStateException::class)
    override fun <T> run(action: ConsumerAction<T?>): T? {
        val parameters = action.getParameters()
        val listener = ProgressListenerAdapter(parameters.getProgressListener())
        val listenerManager = loggingProvider.listenerManager
        listenerManager.addListener(listener)
        try {
            val progressLogger = loggingProvider.progressLoggerFactory.newOperation(ProgressLoggingConsumerActionExecutor::class.java)
            progressLogger!!.setDescription("Build")
            progressLogger.started()
            try {
                return actionExecutor.run<T?>(action)
            } finally {
                progressLogger.completed()
            }
        } finally {
            listenerManager.removeListener(listener)
        }
    }

    override fun disconnect() {
        actionExecutor.disconnect()
    }

    private class ProgressListenerAdapter(private val progressListener: ProgressListenerVersion1) : ProgressListener {
        override fun started(event: ProgressStartEvent) {
            progressListener.onOperationStart(event.getDescription())
        }

        override fun progress(event: ProgressEvent) {
        }

        override fun completed(event: ProgressCompleteEvent) {
            progressListener.onOperationEnd()
        }
    }
}
