/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.tooling.events.internal.DefaultOperationSuccessResult
import org.gradle.tooling.events.task.TaskSuccessResult

/**
 * Implementation of the `TaskSuccessResult` interface.
 */
open class DefaultTaskSuccessResult(startTime: Long, endTime: Long, private val upToDate: Boolean, private val fromCache: Boolean, private val taskExecutionDetails: TaskExecutionDetails) :
    DefaultOperationSuccessResult(startTime, endTime), TaskSuccessResult {
    override val isUpToDate: Boolean
        get() = this.upToDate

    override val isFromCache: Boolean
        get() = fromCache

    override val isIncremental: Boolean
        get() = taskExecutionDetails.isIncremental

    override val executionReasons: MutableList<String?>?
        get() = taskExecutionDetails.executionReasons
}
