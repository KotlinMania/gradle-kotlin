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
package org.gradle.tooling.internal.protocol

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 */
interface InternalBuildProgressListener {
    /**
     * Invoked when a progress event happens in the build being run, and one or more listeners for the given event type have been registered.
     *
     * The event types implemented in Gradle 2.4 are:
     *
     *
     *  * [org.gradle.tooling.internal.protocol.events.InternalTestProgressEvent]
     *
     *
     * The event types implemented in Gradle 2.5 are:
     *
     *
     *  * [org.gradle.tooling.internal.protocol.events.InternalProgressEvent] - used for all operation types.
     *  * [org.gradle.tooling.internal.protocol.events.InternalTestProgressEvent] - test events also implement these types for backwards compatibility
     *
     *
     * @param event The issued progress event
     */
    fun onEvent(event: Any?)

    /**
     * Returns the type of operations that the listener wants to subscribe to.
     *
     * @return the type of operations to be notified about
     */
    val subscribedOperations: MutableList<String?>?

    companion object {
        /**
         * The constant for the test execution operations.
         */
        const val TEST_EXECUTION: String = "TEST_EXECUTION"

        /**
         * The constant for the task execution operations.
         */
        const val TASK_EXECUTION: String = "TASK_EXECUTION"

        /**
         * The constant for the build execution operations.
         */
        const val BUILD_EXECUTION: String = "BUILD_EXECUTION"

        /**
         * The constant for the work item execution operations.
         */
        const val WORK_ITEM_EXECUTION: String = "WORK_ITEM_EXECUTION"

        /**
         * The constant for the project configuration operations.
         */
        const val PROJECT_CONFIGURATION_EXECUTION: String = "PROJECT_CONFIGURATION_EXECUTION"

        /**
         * The constant for the transform operations.
         */
        const val TRANSFORM_EXECUTION: String = "TRANSFORM_EXECUTION"

        /**
         * The constant for the test output of the task execution operations.
         */
        const val TEST_OUTPUT: String = "TEST_OUTPUT"

        /**
         * The constant for test metadata of the task execution operations.
         */
        const val TEST_METADATA: String = "TEST_METADATA"

        /**
         * The constant for file download operations.
         */
        const val FILE_DOWNLOAD: String = "FILE_DOWNLOAD"

        /**
         * The constant for build phase operations.
         */
        const val BUILD_PHASE: String = "BUILD_PHASE"

        /**
         * The constant for problems events.
         */
        const val PROBLEMS: String = "PROBLEMS"

        /**
         * The constant for build finished events that may contain problems.
         */
        const val ROOT: String = "ROOT"
    }
}
