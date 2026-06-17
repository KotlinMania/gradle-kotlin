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
package org.gradle.tooling.internal.consumer

import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.logging.progress.DefaultProgressLoggerFactory
import org.gradle.internal.logging.progress.ProgressListener
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.operations.BuildOperationIdFactory
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.time.Clock

/**
 * Provides logging services per thread.
 */
class SynchronizedLogging(private val clock: Clock, private val buildOperationIdFactory: BuildOperationIdFactory) : LoggingProvider {
    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private val services = ThreadLocal<ThreadLoggingServices?>()

    override fun getListenerManager(): ListenerManager? {
        return services().listenerManager
    }

    override fun getProgressLoggerFactory(): ProgressLoggerFactory? {
        return services().progressLoggerFactory
    }

    private fun services(): ThreadLoggingServices {
        var threadServices = services.get()
        if (threadServices == null) {
            val manager = DefaultListenerManager(Scope.Global::class.java)
            val progressLoggerFactory = DefaultProgressLoggerFactory(manager.getBroadcaster<ProgressListener?>(ProgressListener::class.java)!!, clock, buildOperationIdFactory)
            threadServices = ThreadLoggingServices(manager, progressLoggerFactory)
            services.set(threadServices)
        }
        return threadServices
    }

    private class ThreadLoggingServices(val listenerManager: ListenerManager?, val progressLoggerFactory: ProgressLoggerFactory?)
}
