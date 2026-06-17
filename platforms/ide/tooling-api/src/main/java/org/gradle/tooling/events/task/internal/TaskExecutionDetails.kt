/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.tooling.events.task.internal

import org.gradle.tooling.events.task.TaskExecutionResult
import org.gradle.tooling.model.internal.Exceptions

abstract class TaskExecutionDetails {
    abstract val isIncremental: Boolean

    abstract val executionReasons: MutableList<String?>?

    companion object {
        private val UNSUPPORTED: TaskExecutionDetails = object : TaskExecutionDetails() {
            public override fun isIncremental(): Boolean {
                throw Exceptions.unsupportedMethod(TaskExecutionResult::class.java.getSimpleName() + ".isIncremental()")
            }

            public override fun getExecutionReasons(): MutableList<String?>? {
                throw Exceptions.unsupportedMethod(TaskExecutionResult::class.java.getSimpleName() + ".getExecutionReasons()")
            }
        }

        fun of(incremental: Boolean, executionReasons: MutableList<String?>): TaskExecutionDetails {
            return object : TaskExecutionDetails() {
                public override fun isIncremental(): Boolean {
                    return incremental
                }

                public override fun getExecutionReasons(): MutableList<String?> {
                    return executionReasons
                }
            }
        }

        @JvmStatic
        fun unsupported(): TaskExecutionDetails {
            return UNSUPPORTED
        }
    }
}
