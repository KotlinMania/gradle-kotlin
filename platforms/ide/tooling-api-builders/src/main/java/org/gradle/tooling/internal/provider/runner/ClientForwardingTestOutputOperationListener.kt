/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.internal.build.event.types.DefaultTestOutputDescriptor
import org.gradle.internal.build.event.types.DefaultTestOutputEvent
import org.gradle.internal.build.event.types.DefaultTestOutputResult
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationIdFactory
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.events.test.Destination
import org.gradle.tooling.internal.protocol.events.InternalTestOutputDescriptor
import org.jspecify.annotations.NullMarked

/**
 * Test listener that forwards the test output events.
 */
@NullMarked
internal class ClientForwardingTestOutputOperationListener(private val eventConsumer: ProgressEventConsumer, private val idFactory: BuildOperationIdFactory) : BuildOperationListener {
    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent) {
    }

    override fun progress(buildOperationId: OperationIdentifier, progressEvent: OperationProgressEvent) {
        val details = progressEvent.getDetails()
        if (details is ExecuteTestBuildOperationType.Output) {
            val progress = details
            val descriptor: InternalTestOutputDescriptor = DefaultTestOutputDescriptor(OperationIdentifier(idFactory.nextId()), buildOperationId)
            val destination: Int = getDestination(progress.getOutput().getDestination())
            val result = DefaultTestOutputResult(progressEvent.getTime(), progressEvent.getTime(), destination, progress.getOutput().getMessage())
            eventConsumer.progress(DefaultTestOutputEvent(progressEvent.getTime(), descriptor, result))
        }
    }

    override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
    }

    companion object {
        private fun getDestination(destination: TestOutputEvent.Destination): Int {
            when (destination) {
                TestOutputEvent.Destination.StdOut -> return Destination.StdOut.code
                TestOutputEvent.Destination.StdErr -> return Destination.StdErr.code
                else -> throw IllegalStateException("Unknown output destination type: " + destination)
            }
        }
    }
}
