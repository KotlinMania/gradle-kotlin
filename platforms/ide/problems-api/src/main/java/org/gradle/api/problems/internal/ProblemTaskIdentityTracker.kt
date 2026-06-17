/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.problems.internal

/**
 * This class tracks the currently executed task. The implementation is a naive workaround for capturing the currently executed task based on the assumption that a current thread can execute at most
 * one task at a time. The correct approach would be to capture the executed tasks via build operations (see the usages of BuildOperationAncestryTracker). We invested quite some time into it only to
 * find that introducing another ancestry tracker usage results in a significant performance regression.
 *
 * @see [https://github.com/gradle/gradle/issues/31430](https://github.com/gradle/gradle/issues/31430)
 */
object ProblemTaskIdentityTracker {
    private val TASK_IDENTITY = ThreadLocal<TaskIdentity>()

    @JvmStatic
    var taskIdentity: TaskIdentity?
        /**
         * Returns the identity of the currently executed task.
         *
         * @return the identity of the currently executed task or null if no task is currently executed on the current thread.
         */
        get() = TASK_IDENTITY.get()
        set(taskIdentity) {
            TASK_IDENTITY.set(taskIdentity)
        }

    @JvmStatic
    fun clear() {
        TASK_IDENTITY.remove()
    }
}
