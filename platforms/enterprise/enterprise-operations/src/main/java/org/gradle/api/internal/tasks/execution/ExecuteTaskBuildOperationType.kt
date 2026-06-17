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
package org.gradle.api.internal.tasks.execution

import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.scan.NotUsedByScanPlugin

/**
 * The overall execution of a task, including:
 *
 * - User lifecycle callbacks (TaskExecutionListener.beforeExecute and TaskExecutionListener.afterExecute)
 * - Gradle execution mechanics (e.g. up-to-date and build cache "checks", property validation, build cache output store, etc.)
 *
 * That is, this operation does not represent just the execution of task actions.
 *
 * This operation can fail _and_ yield a result.
 * If the operation gets as far as invoking the task executer
 * (i.e. beforeTask callbacks did not fail), then a result is expected.
 * If the task execution fails, or if afterTask callbacks fail, an operation failure is expected _in addition_.
 */
class ExecuteTaskBuildOperationType private constructor() : BuildOperationType<ExecuteTaskBuildOperationType.Details, ExecuteTaskBuildOperationType.Result> {
    interface Details {
        /**
         * The path of the build this task belongs to.
         */
        val buildPath: String?

        /**
         * Get the path of this task within the build.
         */
        val taskPath: String?

        /**
         * See `org.gradle.api.internal.project.taskfactory.TaskIdentity#uniqueId`.
         */
        val taskId: Long

        val taskClass: Class<*>?
    }

    interface Result {
        /**
         * The message describing why the task was skipped.
         *
         * Expected to be `org.gradle.api.tasks.TaskState#getSkipMessage()`.
         */
        val skipMessage: String?

        /**
         * The detailed reason why the task was skipped, if provided by the project configuration.
         *
         * Expected to be `org.gradle.api.tasks.TaskState#getSkipReasonMessage()`.
         *
         * @since 7.6
         */
        val skipReasonMessage: String?

        /**
         * Whether the task had any actions.
         * See `org.gradle.api.internal.tasks.TaskStateInternal#isActionable()`.
         */
        val isActionable: Boolean

        /**
         * If task was UP_TO_DATE or FROM_CACHE, this will convey the ID of the build that produced the outputs being reused.
         * Value will be null for any other outcome.
         *
         * This value may also be null for an UP_TO_DATE outcome where the task executed, but then decided it was UP_TO_DATE.
         * That is, it was not UP_TO_DATE due to Gradle's core input/output incremental build mechanism.
         * This is not necessarily ideal behaviour, but it is the current.
         */
        val originBuildInvocationId: String?

        /**
         * The build cache key of the task in the origin build invocation.
         *
         *
         * Null if [.getOriginBuildInvocationId] is null.
         *
         * @since 8.7
         */
        val originBuildCacheKeyBytes: ByteArray?

        /**
         * If task was UP_TO_DATE or FROM_CACHE, this will convey the execution time of the task in the build that produced the outputs being reused.
         * Value will be null for any other outcome.
         *
         * This value may also be null for an UP_TO_DATE outcome where the task executed, but then decided it was UP_TO_DATE.
         * That is, it was not UP_TO_DATE due to Gradle's core input/output incremental build mechanism.
         * This is not necessarily ideal behaviour, but it is the current.
         *
         * @since 4.5
         */
        val originExecutionTime: Long?

        /**
         * The human friendly description of why this task was not cacheable.
         * Null if the task was cacheable.
         * Not null if [.getCachingDisabledReasonCategory] is not null.
         */
        val cachingDisabledReasonMessage: String?

        /**
         * The categorisation of the why the task was not cacheable.
         * Null if the task was cacheable.
         * Not null if [.getCachingDisabledReasonMessage]l is not null.
         * Values are expected to correlate to [org.gradle.operations.execution.CachingDisabledReasonCategory].
         */
        val cachingDisabledReasonCategory: String?

        /**
         * Opaque messages describing why the task was not up to date.
         * In the order emitted by Gradle.
         * Null if execution did not get so far as to test "up-to-date-ness".
         * Empty if tested, but task was considered up to date.
         */
        val upToDateMessages: MutableList<String?>?

        @get:NotUsedByScanPlugin("used to report incrementality to TAPI progress listeners")
        val isIncremental: Boolean
    }
}
