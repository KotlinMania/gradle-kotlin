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
package org.gradle.tooling.internal.provider.runner

import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails
import org.gradle.internal.operations.BuildOperationAncestryTracker
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationStartEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/**
 * Test listener that forwards all receiving events to the client via the provided `ProgressEventConsumer` instance.
 */
internal class TaskForTestEventTracker(private val ancestryTracker: BuildOperationAncestryTracker) : BuildOperationTracker {
    private val runningTasks: MutableMap<Any?, String?> = ConcurrentHashMap<Any?, String?>()

    /**
     * Returns the path for the test task that is an ancestor of the given build operation.
     */
    fun getTaskPath(buildOperationId: OperationIdentifier?): String {
        return ancestryTracker.findClosestExistingAncestor<String>(buildOperationId, Function { key: OperationIdentifier? -> runningTasks.get(key) }).get()
    }

    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent?) {
        val details = buildOperation.getDetails()
        if (details is ExecuteTaskBuildOperationDetails) {
            val task = details.getTask()
            val previous = runningTasks.put(buildOperation.getId(), task.getIdentityPath().asString())
            check(previous == null) { "Build operation " + buildOperation.getId() + " already started." }
        }
    }

    override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent?) {
        if (buildOperation.getDetails() is ExecuteTaskBuildOperationDetails) {
            runningTasks.remove(buildOperation.getId())
        }
    }
}
