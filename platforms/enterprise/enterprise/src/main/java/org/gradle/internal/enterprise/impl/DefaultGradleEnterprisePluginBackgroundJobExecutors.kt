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
package org.gradle.internal.enterprise.impl

import org.gradle.internal.concurrent.ExecutorPolicy
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.concurrent.ManagedExecutorImpl
import org.gradle.internal.enterprise.DevelocityPluginUnsafeConfigurationService
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier
import javax.inject.Inject

class DefaultGradleEnterprisePluginBackgroundJobExecutors @Inject constructor(private val unsafeConfigurationService: DevelocityPluginUnsafeConfigurationService) :
    GradleEnterprisePluginBackgroundJobExecutorsInternal {
    private val executorService: ManagedExecutor = createExecutor()

    override fun getUserJobExecutor(): Executor {
        return Executor { job: Runnable? -> this.executeUserJob(job!!) }
    }

    private fun executeUserJob(job: Runnable) {
        executorService.execute(Runnable {
            unsafeConfigurationService.withConfigurationInputTrackingDisabled<Any>(Supplier {
                job.run()
                null
            })
        })
    }

    override fun isInBackground(): Boolean {
        return Thread.currentThread() is BackgroundThread
    }

    override fun shutdown() {
        if (executorService.isShutdown()) {
            return
        }
        executorService.stop()
    }

    private class BackgroundThreadFactory : ThreadFactory {
        private val group = ThreadGroup(NAME)
        private val counter = AtomicLong()

        override fun newThread(r: Runnable): Thread {
            val thread: Thread = BackgroundThread(group, r, NAME + "-" + counter.getAndIncrement())
            thread.setDaemon(true)
            return thread
        }

        companion object {
            private const val NAME = "gradle-enterprise-background-job"
        }
    }

    private class BackgroundThread(group: ThreadGroup, r: Runnable, s: String) : Thread(group, r, s)
    companion object {
        private fun createExecutor(): ManagedExecutor {
            val poolExecutor = ThreadPoolExecutor(
                4, 4,
                30, TimeUnit.SECONDS,
                LinkedBlockingQueue<Runnable?>(),
                BackgroundThreadFactory()
            )
            poolExecutor.allowCoreThreadTimeOut(true)

            return ManagedExecutorImpl(poolExecutor, ExecutorPolicy.CatchAndRecordFailures())
        }
    }
}
