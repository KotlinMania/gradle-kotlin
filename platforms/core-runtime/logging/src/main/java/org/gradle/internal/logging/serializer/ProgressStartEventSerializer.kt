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
package org.gradle.internal.logging.serializer

import org.gradle.internal.logging.events.ProgressStartEvent
import org.gradle.internal.operations.BuildOperationCategory
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer

/**
 * Since Gradle creates a high volume of progress events, this serializer trades simplicity
 * for the smallest possible serialized form. It uses a single byte indicating the presence of optional
 * fields instead of writing an "absent" byte for each of them like most of our serializers do.
 * It also encodes the [BuildOperationCategory] in this byte, since that enum only has 3 values
 * for the forseeable future.
 */
class ProgressStartEventSerializer : Serializer<ProgressStartEvent?> {
    init {
        val maxCategory = BuildOperationCategory.entries[BuildOperationCategory.entries.size - 1]
        require((BUILD_OP_CATEGORY_MASK.toInt() and maxCategory.ordinal) == maxCategory.ordinal) { "Too many categories to fit into flags." }
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, event: ProgressStartEvent) {
        var flags = 0
        val parentProgressOperationId = event.parentProgressOperationId
        if (parentProgressOperationId != null) {
            flags = flags or PARENT_PROGRESS_ID.toInt()
        }

        val description = event.getDescription()

        val loggingHeader = event.getLoggingHeader()
        if (loggingHeader != null) {
            if (description.endsWith(loggingHeader)) {
                flags = flags or LOGGING_HEADER_IS_SUB_DESCRIPTION.toInt()
            } else {
                flags = flags or LOGGING_HEADER.toInt()
            }
        }

        val status = event.status
        if (!status.isEmpty()) {
            if (description.endsWith(status)) {
                flags = flags or STATUS_IS_SUB_DESCRIPTION.toInt()
            } else {
                flags = flags or STATUS.toInt()
            }
        }

        val buildOperationId = event.buildOperationId
        if (buildOperationId != null) {
            if (buildOperationId == event.getProgressOperationId()) {
                flags = flags or BUILD_OPERATION_ID_IS_PROGRESS_ID.toInt()
            } else {
                flags = flags or BUILD_OPERATION_ID.toInt()
            }
        }

        val buildOperationCategory = event.buildOperationCategory
        flags = flags or ((buildOperationCategory.ordinal and BUILD_OP_CATEGORY_MASK.toInt()) shl BUILD_OP_CATEGORY_OFFSET.toInt())

        if (event.isBuildOperationStart) {
            flags = flags or BUILD_OPERATION_START.toInt()
        }

        if (event.category == ProgressStartEvent.BUILD_OP_CATEGORY) {
            flags = flags or CATEGORY_IS_BUILD_OP.toInt()
        } else if (event.category == ProgressStartEvent.TASK_CATEGORY) {
            flags = flags or CATEGORY_IS_TASK.toInt()
        } else {
            flags = flags or CATEGORY_NAME.toInt()
        }

        encoder.writeSmallInt(flags)

        encoder.writeSmallLong(event.getProgressOperationId().getId())
        if (parentProgressOperationId != null) {
            encoder.writeSmallLong(parentProgressOperationId.getId())
        }
        encoder.writeLong(event.timestamp)
        if ((flags and CATEGORY_NAME.toInt()) != 0) {
            encoder.writeString(event.category)
        }
        encoder.writeString(description)
        if ((flags and LOGGING_HEADER.toInt()) != 0) {
            encoder.writeString(loggingHeader)
        } else if ((flags and LOGGING_HEADER_IS_SUB_DESCRIPTION.toInt()) != 0) {
            encoder.writeSmallInt(loggingHeader!!.length)
        }
        if ((flags and STATUS.toInt()) != 0) {
            encoder.writeString(status)
        } else if ((flags and STATUS_IS_SUB_DESCRIPTION.toInt()) != 0) {
            encoder.writeSmallInt(status.length)
        }
        encoder.writeSmallInt(event.totalProgress)

        if ((flags and BUILD_OPERATION_ID.toInt()) != 0) {
            encoder.writeSmallLong(buildOperationId!!.getId())
        }
    }

    @Throws(Exception::class)
    override fun read(decoder: Decoder): ProgressStartEvent {
        val flags = decoder.readSmallInt().toLong()
        val progressOperationId = OperationIdentifier(decoder.readSmallLong())

        var parentProgressOperationId: OperationIdentifier? = null
        if ((flags and PARENT_PROGRESS_ID.toLong()) != 0L) {
            parentProgressOperationId = OperationIdentifier(decoder.readSmallLong())
        }

        val timestamp = decoder.readLong()

        val category: String
        if ((flags and CATEGORY_IS_TASK.toLong()) != 0L) {
            category = ProgressStartEvent.TASK_CATEGORY
        } else if ((flags and CATEGORY_IS_BUILD_OP.toLong()) != 0L) {
            category = ProgressStartEvent.BUILD_OP_CATEGORY
        } else {
            category = decoder.readString()
        }

        val description = decoder.readString()

        var loggingHeader: String? = null
        if ((flags and LOGGING_HEADER.toLong()) != 0L) {
            loggingHeader = decoder.readString()
        } else if ((flags and LOGGING_HEADER_IS_SUB_DESCRIPTION.toLong()) != 0L) {
            val length = decoder.readSmallInt()
            loggingHeader = description.substring(description.length - length)
        }

        var status = ""
        if ((flags and STATUS.toLong()) != 0L) {
            status = decoder.readString()
        } else if ((flags and STATUS_IS_SUB_DESCRIPTION.toLong()) != 0L) {
            val length = decoder.readSmallInt()
            status = description.substring(description.length - length)
        }

        val totalProgress = decoder.readSmallInt()

        val buildOperationStart = (flags and BUILD_OPERATION_START.toLong()) != 0L

        var buildOperationId: OperationIdentifier? = null
        if ((flags and BUILD_OPERATION_ID.toLong()) != 0L) {
            buildOperationId = OperationIdentifier(decoder.readSmallLong())
        } else if ((flags and BUILD_OPERATION_ID_IS_PROGRESS_ID.toLong()) != 0L) {
            buildOperationId = progressOperationId
        }

        val buildOperationCategory: BuildOperationCategory? = BuildOperationCategory.entries[((flags shr BUILD_OP_CATEGORY_OFFSET.toInt()) and BUILD_OP_CATEGORY_MASK.toLong()).toInt()]

        return ProgressStartEvent(
            progressOperationId,
            parentProgressOperationId,
            timestamp,
            category,
            description,
            loggingHeader,
            status,
            totalProgress,
            buildOperationStart,
            buildOperationId,
            buildOperationCategory
        )
    }

    companion object {
        private const val PARENT_PROGRESS_ID: Short = 1
        private val LOGGING_HEADER = (1 shl 2).toShort()
        private val LOGGING_HEADER_IS_SUB_DESCRIPTION = (1 shl 3).toShort()
        private val STATUS = (1 shl 4).toShort()
        private val STATUS_IS_SUB_DESCRIPTION = (1 shl 5).toShort()
        private val BUILD_OPERATION_ID = (1 shl 6).toShort()
        private val BUILD_OPERATION_ID_IS_PROGRESS_ID = (1 shl 7).toShort()
        private val BUILD_OPERATION_START = (1 shl 8).toShort()
        private val CATEGORY_IS_TASK = (1 shl 9).toShort()
        private val CATEGORY_IS_BUILD_OP = (1 shl 10).toShort()
        private val CATEGORY_NAME = (1 shl 11).toShort()
        private const val BUILD_OP_CATEGORY_OFFSET: Short = 12
        private const val BUILD_OP_CATEGORY_MASK: Short = 0x7
    }
}
