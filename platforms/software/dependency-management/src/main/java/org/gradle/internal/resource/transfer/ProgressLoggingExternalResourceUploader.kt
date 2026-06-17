/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal.resource.transfer

import org.gradle.internal.logging.progress.ProgressLoggingInputStream
import org.gradle.internal.logging.progress.ResourceOperation
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationInvocationException
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceWriteBuildOperationType
import org.gradle.internal.resource.ReadableContent
import java.io.IOException
import java.io.InputStream
import java.net.URI

class ProgressLoggingExternalResourceUploader(private val delegate: ExternalResourceUploader, private val buildOperationRunner: BuildOperationRunner) : AbstractProgressLoggingHandler(),
    ExternalResourceUploader {
    @Throws(IOException::class)
    override fun upload(resource: ReadableContent, destination: ExternalResourceName) {
        try {
            buildOperationRunner.run(ProgressLoggingExternalResourceUploader.UploadOperation(destination, resource))
        } catch (e: BuildOperationInvocationException) {
            if (e.cause is IOException) {
                throw e.cause as IOException?
            }
            throw e
        }
    }

    private class ProgressLoggingReadableContent(private val delegate: ReadableContent, private val uploadOperation: ResourceOperation) : ReadableContent {
        override fun open(): InputStream {
            return ProgressLoggingInputStream(
                delegate.open(),
                org.gradle.internal.logging.progress.ProgressLoggingInputStreamListener { processedBytes: Int -> uploadOperation.logProcessedBytes(processedBytes) })
        }

        override fun getContentLength(): Long {
            return delegate.getContentLength()
        }
    }

    private class PutOperationDetails(location: URI) : LocationDetails(location), ExternalResourceWriteBuildOperationType.Details {
        override fun toString(): String {
            return "ExternalResourceWriteBuildOperationType.Details{location=" + getLocation() + ", " + '}'
        }
    }

    private inner class UploadOperation(private val destination: ExternalResourceName, private val resource: ReadableContent) : RunnableBuildOperation {
        @Throws(IOException::class)
        override fun run(context: BuildOperationContext) {
            val uploadOperation = createResourceOperation(context, ResourceOperation.Type.upload)
            uploadOperation.setContentLength(resource.getContentLength())
            try {
                delegate.upload(ProgressLoggingReadableContent(resource, uploadOperation), destination)
            } finally {
                context.setResult(object : ExternalResourceWriteBuildOperationType.Result {
                    override fun getBytesWritten(): Long {
                        return uploadOperation.totalProcessedBytes
                    }
                })
            }
        }

        override fun description(): BuildOperationDescriptor.Builder {
            return BuildOperationDescriptor
                .displayName("Upload " + destination.getUri())
                .progressDisplayName(destination.getShortDisplayName())
                .details(PutOperationDetails(destination.getUri()))
        }
    }
}
