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
package org.gradle.tooling.events

import org.gradle.api.Incubating

/**
 * Enumerates the different types of operations for which progress events can be received.
 *
 * @see org.gradle.tooling.LongRunningOperation.addProgressListener
 */
enum class OperationType {
    /**
     * Flag for test operation progress events.
     *
     *
     *
     * The following events are currently issued for this operation type.
     *
     *  * [org.gradle.tooling.events.test.TestStartEvent]
     *  * [org.gradle.tooling.events.test.TestFinishEvent]
     *
     */
    TEST,

    /**
     * Flag for task operation progress events.
     *
     *
     *
     * The following events are currently issued for this operation type.
     *
     *  * [org.gradle.tooling.events.task.TaskStartEvent]
     *  * [org.gradle.tooling.events.task.TaskFinishEvent]
     *
     *
     */
    TASK,

    /**
     * Flag for operations with no specific type.
     *
     *
     *
     * The following events are currently issued for this operation type.
     *
     *  * [StartEvent]
     *  * [FinishEvent]
     *  * [StatusEvent]
     *
     *
     */
    GENERIC,

    /**
     * Flag for work item operation progress events.
     *
     *
     *
     * The following events are currently issued for this operation type.
     *
     *  * [org.gradle.tooling.events.work.WorkItemStartEvent]
     *  * [org.gradle.tooling.events.work.WorkItemFinishEvent]
     *
     *
     * @since 5.1
     */
    WORK_ITEM,

    /**
     * Flag for project configuration operation progress events.
     *
     *
     *
     * The following events are currently issued for this operation type.
     *
     *  * [org.gradle.tooling.events.configuration.ProjectConfigurationStartEvent]
     *  * [org.gradle.tooling.events.configuration.ProjectConfigurationFinishEvent]
     *
     *
     * @since 5.1
     */
    PROJECT_CONFIGURATION,

    /**
     * Flag for transform operation progress events.
     *
     *
     *
     * The following events are currently issued for this operation type.
     *
     *  * [org.gradle.tooling.events.transform.TransformStartEvent]
     *  * [org.gradle.tooling.events.transform.TransformFinishEvent]
     *
     *
     * @since 5.1
     */
    TRANSFORM,

    /**
     * Flag for test output operation progress events.
     *
     *
     * Clients must subscribe to [.TEST] events too if they want to receive test output events.
     *
     *
     * The following events are currently issued for this operation type.
     *
     *  * [org.gradle.tooling.events.test.TestOutputEvent]
     *
     *
     * @since 6.0
     */
    TEST_OUTPUT,

    /**
     * Flag for test metadata events.
     *
     *
     * Clients must subscribe to [.TEST] events too if they want to receive test metadata events.
     *
     *
     * The following events are currently issued for this operation type.
     *
     *  * [org.gradle.tooling.events.test.TestMetadataEvent]
     *
     *
     * @since 8.13
     */
    @Incubating
    TEST_METADATA,

    /**
     * Flag for file download progress events. This includes various types of files, for example files downloaded during dependency resolution,
     * Gradle distribution downloads, and Java toolchain downloads.
     *
     *
     *
     * The following events are currently issued for this operation type.
     *
     *  * [org.gradle.tooling.events.download.FileDownloadStartEvent]
     *  * [StatusEvent]
     *  * [org.gradle.tooling.events.download.FileDownloadFinishEvent]
     *
     *
     * @since 7.3
     */
    FILE_DOWNLOAD,


    /**
     * Flag for build phase events.
     *
     *
     *
     * The following events are currently issued for this operation type.
     *
     *  * [org.gradle.tooling.events.lifecycle.BuildPhaseStartEvent]
     *  * [org.gradle.tooling.events.lifecycle.BuildPhaseFinishEvent]
     *
     *
     * @since 7.6
     */
    @Incubating
    BUILD_PHASE,

    /**
     * Flag for problem events.
     *
     *
     *
     * The following events are currently issued for this operation type.
     *
     *  * [org.gradle.tooling.events.problems.ProblemEvent]
     *  * [org.gradle.tooling.events.problems.SingleProblemEvent]
     *  * [org.gradle.tooling.events.problems.ProblemSummariesEvent]
     *
     *
     * @since 8.4
     */
    @Incubating
    PROBLEMS,

    /**
     * Flag for the topmost progress event.
     *
     *
     * Using this operation type is useful for capturing the build failure details from the finish event.
     *
     * The following events are currently issued for this operation type.
     *
     *  * [StartEvent]
     *  * [FinishEvent]
     *
     * @since 8.12
     */
    @Incubating
    ROOT
}
