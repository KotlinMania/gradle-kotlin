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

import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer

class SpanSerializer(private val styleSerializer: Serializer<StyledTextOutput.Style?>) : Serializer<StyledTextOutputEvent.Span?> {
    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: StyledTextOutputEvent.Span) {
        styleSerializer.write(encoder, value.style)
        encoder.writeString(value.getText())
    }

    @Throws(Exception::class)
    override fun read(decoder: Decoder): StyledTextOutputEvent.Span {
        return StyledTextOutputEvent.Span(styleSerializer.read(decoder)!!, decoder.readString())
    }
}
