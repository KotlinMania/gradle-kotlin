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
package org.gradle.tooling.internal.provider.runner

import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType
import org.gradle.api.tasks.testing.TestFileAttachmentDataEvent
import org.gradle.api.tasks.testing.TestKeyValueDataEvent
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.build.event.types.DefaultTestFileAttachmentMetadataEvent
import org.gradle.internal.build.event.types.DefaultTestKeyValueDataMetadataEvent
import org.gradle.internal.build.event.types.DefaultTestMetadataDescriptor
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationIdFactory
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.tooling.internal.protocol.events.InternalTestMetadataDescriptor
import org.jspecify.annotations.NullMarked

/**
 * Test listener that forwards the test metadata events.
 *
 * This listener adapts build operation test events sent from the build into internal test events shared with the consumer.
 */
@NullMarked
internal class ClientForwardingTestMetadataOperationListener /* package */(private val eventConsumer: ProgressEventConsumer, private val idFactory: BuildOperationIdFactory) : BuildOperationListener {
    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent) {
    }

    override fun progress(buildOperationId: OperationIdentifier, progressEvent: OperationProgressEvent) {
        val details = progressEvent.getDetails()
        if (details is ExecuteTestBuildOperationType.Metadata) {
            val dataEvent = details
            val descriptor: InternalTestMetadataDescriptor = DefaultTestMetadataDescriptor(OperationIdentifier(idFactory.nextId()), buildOperationId)
            val data = dataEvent.getMetadata()
            if (data is TestKeyValueDataEvent) {
                val keyValueData = data
                eventConsumer.progress(DefaultTestKeyValueDataMetadataEvent(progressEvent.getTime(), descriptor, uncheckedNonnullCast<MutableMap<String, Any>?>(keyValueData.getValues())!!))
            } else if (data is TestFileAttachmentDataEvent) {
                val fileAttachment = data
                eventConsumer.progress(DefaultTestFileAttachmentMetadataEvent(progressEvent.getTime(), descriptor, fileAttachment.getPath().toFile(), fileAttachment.getMediaType()))
            } else {
                throw IllegalStateException("Unexpected test metadata event type: " + data.javaClass)
            }
        }
    }

    override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
    }
}
