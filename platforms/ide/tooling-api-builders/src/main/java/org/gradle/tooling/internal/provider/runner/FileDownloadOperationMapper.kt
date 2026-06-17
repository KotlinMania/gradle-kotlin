/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.build.event.BuildEventSubscriptions
import org.gradle.internal.build.event.types.AbstractOperationResult
import org.gradle.internal.build.event.types.DefaultFailure
import org.gradle.internal.build.event.types.DefaultFileDownloadDescriptor
import org.gradle.internal.build.event.types.DefaultFileDownloadFailureResult
import org.gradle.internal.build.event.types.DefaultFileDownloadSuccessResult
import org.gradle.internal.build.event.types.DefaultOperationFinishedProgressEvent
import org.gradle.internal.build.event.types.DefaultOperationStartedProgressEvent
import org.gradle.internal.build.event.types.DefaultStatusEvent
import org.gradle.internal.build.event.types.NotFoundFileDownloadSuccessResult
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressDetails
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.events.InternalOperationFinishedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalOperationStartedProgressEvent
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent
import java.net.URI
import java.net.URISyntaxException

class FileDownloadOperationMapper : BuildOperationMapper<ExternalResourceReadBuildOperationType.Details?, DefaultFileDownloadDescriptor?> {
    override fun isEnabled(subscriptions: BuildEventSubscriptions): Boolean {
        return subscriptions.isRequested(OperationType.FILE_DOWNLOAD)
    }

    override fun getDetailsType(): Class<ExternalResourceReadBuildOperationType.Details?> {
        return ExternalResourceReadBuildOperationType.Details::class.java
    }

    override fun createDescriptor(details: ExternalResourceReadBuildOperationType.Details, buildOperation: BuildOperationDescriptor, parent: OperationIdentifier?): DefaultFileDownloadDescriptor {
        try {
            return DefaultFileDownloadDescriptor(buildOperation.getId(), buildOperation.getName(), buildOperation.getDisplayName(), parent, URI(details.location))
        } catch (e: URISyntaxException) {
            throw throwAsUncheckedException(e)
        }
    }

    override fun createStartedEvent(
        descriptor: DefaultFileDownloadDescriptor?,
        details: ExternalResourceReadBuildOperationType.Details?,
        startEvent: OperationStartEvent
    ): InternalOperationStartedProgressEvent {
        return DefaultOperationStartedProgressEvent(startEvent.getStartTime(), descriptor)
    }

    override fun createProgressEvent(descriptor: DefaultFileDownloadDescriptor?, progressEvent: OperationProgressEvent): InternalProgressEvent? {
        if (progressEvent.getDetails() is OperationProgressDetails) {
            val details = progressEvent.getDetails() as OperationProgressDetails
            return DefaultStatusEvent(progressEvent.getTime(), descriptor, details.getProgress(), details.getTotal(), details.getUnits())
        } else {
            return null
        }
    }

    override fun createFinishedEvent(
        descriptor: DefaultFileDownloadDescriptor?,
        details: ExternalResourceReadBuildOperationType.Details?,
        finishEvent: OperationFinishEvent
    ): InternalOperationFinishedProgressEvent {
        val operationResult = finishEvent.getResult() as ExternalResourceReadBuildOperationType.Result?
        val endTime = finishEvent.getEndTime()
        val result: AbstractOperationResult = Companion.createFileDownloadResult(operationResult!!, finishEvent.getFailure(), finishEvent.getStartTime(), endTime)
        return DefaultOperationFinishedProgressEvent(endTime, descriptor, result)
    }

    companion object {
        private fun createFileDownloadResult(operationResult: ExternalResourceReadBuildOperationType.Result, failure: Throwable?, startTime: Long, endTime: Long): AbstractOperationResult {
            if (operationResult.isMissing) {
                return NotFoundFileDownloadSuccessResult(startTime, endTime)
            }
            if (failure == null) {
                return DefaultFileDownloadSuccessResult(startTime, endTime, operationResult.bytesRead)
            }
            return DefaultFileDownloadFailureResult(startTime, endTime, mutableListOf<InternalFailure?>(DefaultFailure.fromThrowable(failure)), operationResult.bytesRead)
        }
    }
}
