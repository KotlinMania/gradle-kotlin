/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.tooling.internal.provider

import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.build.event.BuildEventListenerFactory
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.buildtree.BuildActionRunner
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.session.BuildSessionActionExecutor
import org.gradle.internal.session.BuildSessionContext
import org.gradle.tooling.internal.provider.action.SubscribableBuildAction

/**
 * Attaches build operation listeners to forward relevant operations back to the client.
 */
class SubscribableBuildActionExecutor(
    private val listenerManager: ListenerManager,
    private val buildOperationListenerManager: BuildOperationListenerManager,
    private val factory: BuildEventListenerFactory,
    private val eventConsumer: BuildEventConsumer,
    private val delegate: BuildSessionActionExecutor
) : BuildSessionActionExecutor {
    private val listeners: MutableList<Any> = ArrayList<Any>()

    override fun execute(action: BuildAction, buildSession: BuildSessionContext): BuildActionRunner.Result {
        if (action is SubscribableBuildAction) {
            val subscribableBuildAction = action
            registerListenersForClientSubscriptions(subscribableBuildAction.getClientSubscriptions(), eventConsumer)
        }
        try {
            return delegate.execute(action, buildSession)
        } finally {
            for (listener in listeners) {
                listenerManager.removeListener(listener)
                if (listener is BuildOperationListener) {
                    buildOperationListenerManager.removeListener(listener)
                }
            }
            listeners.clear()
        }
    }

    private fun registerListenersForClientSubscriptions(clientSubscriptions: BuildEventSubscriptions, eventConsumer: BuildEventConsumer) {
        for (listener in factory.createListeners(clientSubscriptions, eventConsumer)) {
            registerListener(listener)
        }
    }

    private fun registerListener(listener: Any) {
        listeners.add(listener)
        listenerManager.addListener(listener)
        if (listener is BuildOperationListener) {
            buildOperationListenerManager.addListener(listener)
        }
    }
}
