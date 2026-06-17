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

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer

class LogEventSerializer(private val logLevelSerializer: Serializer<LogLevel>, private val throwableSerializer: Serializer<Throwable>) : Serializer<LogEvent?> {
    @Throws(Exception::class)
    override fun write(encoder: Encoder, event: LogEvent) {
        encoder.writeLong(event.timestamp)
        encoder.writeString(event.category)
        logLevelSerializer.write(encoder, event.getLogLevel())
        encoder.writeNullableString(event.getMessage())
        throwableSerializer.write(encoder, event.getThrowable())
        if (event.buildOperationId == null) {
            encoder.writeBoolean(false)
        } else {
            encoder.writeBoolean(true)
            encoder.writeSmallLong(event.buildOperationId.getId())
        }
    }

    @Throws(Exception::class)
    override fun read(decoder: Decoder): LogEvent {
        val timestamp = decoder.readLong()
        val category = decoder.readString()
        val logLevel = logLevelSerializer.read(decoder)
        val message = decoder.readNullableString()
        val throwable = throwableSerializer.read(decoder)
        val buildOperationId = if (decoder.readBoolean()) OperationIdentifier(decoder.readSmallLong()) else null
        return LogEvent(timestamp, category, logLevel, message!!, throwable, buildOperationId)
    }
}
