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

import java.util.Arrays
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.tooling.Failure
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler

class BlockingResultHandler<T>(private val resultType: Class<T?>) : ResultHandler<T?> {
    private val queue: BlockingQueue<Any?> = ArrayBlockingQueue<Any?>(1)

    val result: T?
        get() {
            val result: Any?
            try {
                result = queue.take()
            } catch (e: InterruptedException) {
                throw throwAsUncheckedException(e)
            }

            if (result is Throwable) {
                throw throwAsUncheckedException(attachCallerThreadStackTrace(result))
            }
            if (result === NULL) {
                return null
            }
            return resultType.cast(result)
        }

    override fun onComplete(result: T?) {
        queue.add(if (result == null) NULL else result)
    }

    override fun onFailure(failure: GradleConnectionException?) {
        queue.add(failure)
    }

    companion object {
        private val NULL = Any()

        fun attachCallerThreadStackTrace(failure: Throwable): Throwable {
            val adjusted: MutableList<StackTraceElement?> = ArrayList<StackTraceElement?>()
            adjusted.addAll(Arrays.asList<StackTraceElement?>(*failure.getStackTrace()))
            val currentThreadStack = Arrays.asList<StackTraceElement?>(*Thread.currentThread().getStackTrace())
            if (!currentThreadStack.isEmpty()) {
                adjusted.addAll(currentThreadStack.subList(2, currentThreadStack.size))
            }
            failure.setStackTrace(adjusted.toTypedArray<StackTraceElement?>())
            return failure
        }
    }
}
