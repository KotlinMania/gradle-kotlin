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

import org.gradle.api.resources.ResourceException
import org.gradle.internal.logging.progress.ProgressLoggingInputStream
import org.gradle.internal.logging.progress.ResourceOperation
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType
import org.gradle.internal.resource.ExternalResourceReadMetadataBuildOperationType
import org.gradle.internal.resource.ResourceExceptions
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import java.io.InputStream
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

class ProgressLoggingExternalResourceAccessor(private val delegate: ExternalResourceAccessor, private val buildOperationRunner: BuildOperationRunner) : AbstractProgressLoggingHandler(),
    ExternalResourceAccessor {
    @Throws(ResourceException::class)
    override fun <T> withContent(location: ExternalResourceName, revalidate: Boolean, action: ExternalResource.ContentAndMetadataAction<T?>): T? {
        return buildOperationRunner.call<T?>(ProgressLoggingExternalResourceAccessor.DownloadOperation<T?>(location, revalidate, action))
    }

    override fun getMetaData(location: ExternalResourceName, revalidate: Boolean): ExternalResourceMetaData? {
        return buildOperationRunner.call<ExternalResourceMetaData>(ProgressLoggingExternalResourceAccessor.MetadataOperation(location, revalidate))
    }

    private fun createBuildOperationDetails(resourceName: ExternalResourceName): BuildOperationDescriptor.Builder {
        val operationDetails: ExternalResourceReadBuildOperationType.Details = ReadOperationDetails(resourceName.getUri())
        return BuildOperationDescriptor
            .displayName("Download " + resourceName.getUri())
            .progressDisplayName(resourceName.getShortDisplayName())
            .details(operationDetails)
    }

    private class MetadataOperationDetails(location: URI) : LocationDetails(location), ExternalResourceReadMetadataBuildOperationType.Details {
        override fun toString(): String {
            return "ExternalResourceReadMetadataBuildOperationType.Details{location=" + getLocation() + ", " + '}'
        }
    }

    private class ReadOperationDetails(location: URI) : LocationDetails(location), ExternalResourceReadBuildOperationType.Details {
        override fun toString(): String {
            return "ExternalResourceReadBuildOperationType.Details{location=" + getLocation() + ", " + '}'
        }
    }

    private class ReadOperationResult(val bytesRead: Long, val isMissing: Boolean) : ExternalResourceReadBuildOperationType.Result {
        override fun toString(): String {
            return "ExternalResourceReadBuildOperationType.Result{" +
                    "bytesRead=" + bytesRead +
                    ", missing=" + this.isMissing +
                    '}'
        }
    }

    private inner class DownloadOperation<T>(private val location: ExternalResourceName, private val revalidate: Boolean, private val action: ExternalResource.ContentAndMetadataAction<T?>) :
        CallableBuildOperation<T?> {
        override fun call(context: BuildOperationContext): T? {
            val downloadOperation = createResourceOperation(context, ResourceOperation.Type.download)
            val metadata = AtomicReference<ExternalResourceMetaData>()
            try {
                return delegate.withContent<T?>(location, revalidate, ExternalResource.ContentAndMetadataAction { inputStream: InputStream?, metaData: ExternalResourceMetaData? ->
                    downloadOperation.setContentLength(metaData!!.getContentLength())
                    metadata.set(metaData)
                    if (metaData.wasMissing()) {
                        context.failed(ResourceExceptions.getMissing(metaData.getLocation()))
                        return@withContent null
                    }
                    val stream = ProgressLoggingInputStream(
                        inputStream!!,
                        org.gradle.internal.logging.progress.ProgressLoggingInputStreamListener { processedBytes: Int -> downloadOperation.logProcessedBytes(processedBytes) })
                    action.execute(stream, metaData)
                })
            } finally {
                val externalResourceMetaData = metadata.get()
                context.setResult(
                    ReadOperationResult(
                        downloadOperation.totalProcessedBytes,
                        externalResourceMetaData != null && externalResourceMetaData.wasMissing()
                    )
                )
            }
        }

        override fun description(): BuildOperationDescriptor.Builder {
            return createBuildOperationDetails(location)
        }
    }

    private inner class MetadataOperation(private val location: ExternalResourceName, private val revalidate: Boolean) : CallableBuildOperation<ExternalResourceMetaData> {
        override fun call(context: BuildOperationContext): ExternalResourceMetaData {
            try {
                return delegate.getMetaData(location, revalidate)!!
            } finally {
                context.setResult(METADATA_RESULT)
            }
        }

        override fun description(): BuildOperationDescriptor.Builder {
            return BuildOperationDescriptor
                .displayName("Metadata of " + location.getDisplayName())
                .details(MetadataOperationDetails(location.getUri()))
        }
    }

    companion object {
        private val METADATA_RESULT: ExternalResourceReadMetadataBuildOperationType.Result = object : ExternalResourceReadMetadataBuildOperationType.Result {
        }
    }
}
