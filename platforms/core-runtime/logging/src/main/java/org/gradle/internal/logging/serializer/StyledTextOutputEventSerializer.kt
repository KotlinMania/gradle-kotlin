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
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer

class StyledTextOutputEventSerializer(private val logLevelSerializer: Serializer<LogLevel>, private val spanSerializer: Serializer<MutableList<StyledTextOutputEvent.Span?>>) :
    Serializer<StyledTextOutputEvent?> {
    @Throws(Exception::class)
    override fun write(encoder: Encoder, event: StyledTextOutputEvent) {
        encoder.writeLong(event.timestamp)
        encoder.writeString(event.category)
        logLevelSerializer.write(encoder, event.getLogLevel())
        if (event.buildOperationId == null) {
            encoder.writeBoolean(false)
        } else {
            encoder.writeBoolean(true)
            encoder.writeSmallLong(event.buildOperationId.getId())
        }
        spanSerializer.write(encoder, event.getSpans())
    }

    @Throws(Exception::class)
    override fun read(decoder: Decoder): StyledTextOutputEvent {
        val timestamp = decoder.readLong()
        val category = decoder.readString()
        val logLevel = logLevelSerializer.read(decoder)
        val buildOperationId = if (decoder.readBoolean()) OperationIdentifier(decoder.readSmallLong()) else null
        val spans = spanSerializer.read(decoder)
        return StyledTextOutputEvent(timestamp, category, logLevel, buildOperationId, spans)
    }
}

