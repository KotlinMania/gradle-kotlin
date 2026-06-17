/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.operations.execution

import org.gradle.internal.operations.BuildOperationType

/**
 * A [BuildOperationType] for executing any kind of work inside the execution engine.
 *
 *
 * This operation encompasses most of the execution engine pipeline, starting immediately
 * after the work is identified and a workspace is assigned.
 *
 *
 * Note that the pipeline does not have to execute fully, for instance if the work is up-to-date.
 * The underlying work (task or transform action) can be skipped, and the [skip message][Result.getSkipMessage]
 * is provided in the build operation result.
 *
 * @since 8.3
 */
class ExecuteWorkBuildOperationType : BuildOperationType<ExecuteWorkBuildOperationType.Details?, ExecuteWorkBuildOperationType.Result?> {
    interface Details {
        /**
         * Type of work being executed.
         *
         *
         * Expected values are:
         *
         *  * `null` - work type is not classified
         *  * `TRANSFORM` - execution of an artifact transform
         *
         */
        val workType: String?

        /**
         * The identity of the executed unit of work.
         *
         *
         * The identity needs to be unique per build tree, so consumers of this
         * build operation attribute them to the corresponding domain object.
         * Note that this identity is different from the raw identity in the execution engine,
         * since the execution engine does not guarantee uniqueness within the build tree.
         */
        val identity: String?
    }

    interface Result {
        /**
         * A message describing why the work was skipped.
         *
         *
         * Expected values are:
         *
         *  * `null` - the work was not skipped
         *  * `NO-SOURCE` - executing the work was no necessary to produce the outputs
         *  * `UP-TO-DATE` - the outputs have not changed, because the work is already up-to-date
         *  * `FROM-CACHE` - the outputs have been loaded from the build cache
         *
         */
        val skipMessage: String?

        /**
         * If work was UP_TO_DATE or FROM_CACHE, this will convey the ID of the build that produced the outputs being reused.
         * Value will be null for any other outcome.
         *
         * This value may also be null for an UP_TO_DATE outcome where the work executed, but then decided it was UP_TO_DATE.
         * That is, it was not UP_TO_DATE due to Gradle's core input/output incremental build mechanism.
         * This is not necessarily ideal behaviour, but it is the current.
         */
        val originBuildInvocationId: String?

        /**
         * The build cache key of the work in the origin build invocation.
         *
         *
         * Null if [.getOriginBuildInvocationId] is null.
         *
         * @since 8.7
         */
        val originBuildCacheKeyBytes: ByteArray?

        /**
         * If the work was UP_TO_DATE or FROM_CACHE, this will convey the execution time of the work in the build that produced the outputs being reused.
         * Value will be null for any other outcome.
         *
         * This value may also be null for an UP_TO_DATE outcome where the work executed, but then decided it was UP_TO_DATE.
         * That is, it was not UP_TO_DATE due to Gradle's core input/output incremental build mechanism,
         * but the unit of work returns `false` for `org.gradle.internal.execution.UnitOfWork.WorkOutput#getDidWork()`.
         * This is not necessarily ideal behaviour, but it is the current.
         */
        val originExecutionTime: Long?

        /**
         * The human friendly description of why this work was not cacheable.
         * Null if the work was cacheable.
         * Not null if [.getCachingDisabledReasonCategory] is not null.
         */
        val cachingDisabledReasonMessage: String?

        /**
         * The categorisation of why the work was not cacheable.
         * Null if the work was cacheable.
         * Not null if [.getCachingDisabledReasonMessage]l is not null.
         * Values are expected to correlate to [CachingDisabledReasonCategory].
         */
        val cachingDisabledReasonCategory: String?

        /**
         * Opaque messages describing why the work was executed.
         *
         *
         * In the order emitted by Gradle.
         * Null if execution did not get so far as to test "up-to-date-ness".
         * Empty if tested, but work was considered up to date.
         */
        val executionReasons: MutableList<String>?
    }
}
