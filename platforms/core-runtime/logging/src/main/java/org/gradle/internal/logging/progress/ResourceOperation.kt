/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.internal.logging.progress

import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.util.NumberUtil

class ResourceOperation(private val context: BuildOperationContext, private val operationType: Type) {
    enum class Type {
        download,
        upload
    }

    private var contentLengthBytes: Long = 0
    private var contentLengthString: String? = null

    private var loggedKBytes: Long = 0
    var totalProcessedBytes: Long = 0
        private set

    fun setContentLength(contentLength: Long) {
        this.contentLengthBytes = contentLength
        if (contentLength <= 0) {
            this.contentLengthString = String.format(" %sed", operationType)
        } else {
            this.contentLengthString = String.format("/%s %sed", NumberUtil.formatBytes(contentLength), operationType)
        }
    }

    fun logProcessedBytes(processedBytes: Long) {
        totalProcessedBytes += processedBytes
        val processedKiB = totalProcessedBytes / NumberUtil.KIB_BASE
        if (processedKiB > loggedKBytes) {
            loggedKBytes = processedKiB
            val progressMessage = NumberUtil.formatBytes(totalProcessedBytes) + contentLengthString
            context.progress(totalProcessedBytes, contentLengthBytes, "bytes", progressMessage)
        }
    }
}
