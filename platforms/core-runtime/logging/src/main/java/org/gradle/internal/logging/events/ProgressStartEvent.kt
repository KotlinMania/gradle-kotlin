/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.logging.events

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.LogLevelConverter.convert
import org.gradle.internal.logging.events.operations.ProgressStartBuildOperationProgressDetails
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.logging.LogEventLevel

@Suppress("deprecation")
class ProgressStartEvent(
    private val progressOperationId: OperationIdentifier?,
    @JvmField val parentProgressOperationId: OperationIdentifier?,
    timestamp: Long,
    category: String,
    private val description: String,
    private val loggingHeader: String?,
    @JvmField val status: String,
    @JvmField val totalProgress: Int,
    /**
     * Whether this progress start represent the start of a build operation,
     * as opposed to a progress operation within a build operation.
     */
    val isBuildOperationStart: Boolean,
    /**
     * When this event is a build operation start event, this property will be non-null and will have the same value as [.getProgressOperationId].
     */
    @JvmField val buildOperationId: OperationIdentifier?,
    buildOperationCategory: BuildOperationCategory?
) : CategorisedOutputEvent(timestamp, category, LogLevel.LIFECYCLE), ProgressStartBuildOperationProgressDetails {
    @JvmField
    val buildOperationCategory: BuildOperationCategory

    init {
        this.buildOperationCategory = if (buildOperationCategory == null) BuildOperationCategory.UNCATEGORIZED else buildOperationCategory
    }

    override fun getDescription(): String {
        return description
    }

    override fun getLoggingHeader(): String? {
        return loggingHeader
    }

    override fun toString(): String {
        return "ProgressStart (p:" + progressOperationId + " parent p:" + parentProgressOperationId + " b:" + buildOperationId + ") " + description
    }

    fun getProgressOperationId(): OperationIdentifier {
        return progressOperationId!!
    }

    fun withParentProgressOperation(parentProgressOperationId: OperationIdentifier): ProgressStartEvent {
        return ProgressStartEvent(
            progressOperationId, parentProgressOperationId, timestamp, category, description, loggingHeader, status, totalProgress,
            this.isBuildOperationStart, buildOperationId, buildOperationCategory
        )
    }

    override fun getLevel(): LogEventLevel {
        return convert(logLevel)
    }

    companion object {
        const val TASK_CATEGORY: String = "class org.gradle.internal.buildevents.TaskExecutionLogger"
        const val BUILD_OP_CATEGORY: String = "org.gradle.internal.logging.progress.ProgressLoggerFactory"
    }
}
