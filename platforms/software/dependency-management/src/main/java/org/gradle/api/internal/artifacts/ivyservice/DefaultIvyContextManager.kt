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
package org.gradle.api.internal.artifacts.ivyservice

import org.apache.ivy.Ivy
import org.apache.ivy.core.IvyContext
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.util.Message
import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.internal.SystemProperties
import org.gradle.internal.Transformers
import java.util.LinkedList
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class DefaultIvyContextManager : IvyContextManager {
    private val lock: Lock = ReentrantLock()
    private var messageAdapterAttached = false
    private val cached = LinkedList<Ivy>()
    private val depth = ThreadLocal<Int?>()

    override fun withIvy(action: Action<in Ivy?>?) {
        withIvy<Any?>(Transformers.toTransformer<Any?, Ivy?>(action))
    }

    override fun <T> withIvy(action: Transformer<out T?, in Ivy?>): T? {
        val currentDepth = depth.get()

        if (currentDepth != null) {
            depth.set(currentDepth + 1)
            try {
                return action.transform(IvyContext.getContext().getIvy())
            } finally {
                depth.set(currentDepth)
            }
        }

        IvyContext.pushNewContext()
        try {
            depth.set(1)
            try {
                val ivy = this.ivy
                try {
                    IvyContext.getContext().setIvy(ivy)
                    return action.transform(ivy)
                } finally {
                    releaseIvy(ivy)
                }
            } finally {
                depth.remove()
            }
        } finally {
            IvyContext.popContext()
        }
    }

    private val ivy: Ivy
        get() {
            lock.lock()
            try {
                if (!cached.isEmpty()) {
                    return cached.removeFirst()
                }
                if (!messageAdapterAttached) {
                    Message.setDefaultLogger(IvyLoggingAdaper())
                    messageAdapterAttached = true
                }
            } finally {
                lock.unlock()
            }
            return createNewIvyInstance()
        }

    /*
    * Synchronizes on the system properties, because IvySettings iterates
    * over them without taking a defensive copy. This can fail if another
    * process sets a system property at that moment.
    */
    private fun createNewIvyInstance(): Ivy {
        return SystemProperties.getInstance().withSystemProperties<Ivy>(org.gradle.internal.Factory { Ivy.newInstance(IvySettings()) })
    }

    private fun releaseIvy(ivy: Ivy) {
        // cleanup
        ivy.getSettings().getResolvers().clear()
        ivy.getSettings().setDefaultResolver(null)

        lock.lock()
        try {
            if (cached.size < MAX_CACHED_IVY_INSTANCES) {
                cached.add(ivy)
            }
            // else, throw it away
        } finally {
            lock.unlock()
        }
    }

    companion object {
        private const val MAX_CACHED_IVY_INSTANCES = 4
    }
}
