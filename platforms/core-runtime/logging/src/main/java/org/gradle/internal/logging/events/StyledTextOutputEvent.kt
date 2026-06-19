/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.logging.events

import org.gradle.api.logging.LogLevel
import org.gradle.internal.SystemProperties
import org.gradle.internal.logging.events.LogLevelConverter.convert
import org.gradle.internal.logging.events.operations.StyledTextBuildOperationProgressDetails
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.logging.LogEventLevel

@Suppress("deprecation")
class StyledTextOutputEvent(timestamp: Long, category: String, logLevel: LogLevel, buildOperationIdentifier: OperationIdentifier?, spans: MutableList<Span>) :
    RenderableOutputEvent(timestamp, category, logLevel, buildOperationIdentifier), StyledTextBuildOperationProgressDetails {
    private val spans: MutableList<Span>

    constructor(timestamp: Long, category: String, logLevel: LogLevel, buildOperationIdentifier: OperationIdentifier?, text: String) : this(
        timestamp, category, logLevel, buildOperationIdentifier, mutableListOf(
            Span(StyledTextOutput.Style.Normal, text)
        )
    )

    init {
        this.spans = ArrayList<Span>(spans)
    }

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append('[').append(logLevel).append("] [")
        builder.append(category).append("] ")
        for (span in spans) {
            builder.append('<')
            builder.append(span.style)
            builder.append(">")
            builder.append(span.text)
            builder.append("</")
            builder.append(span.style)
            builder.append(">")
        }
        return builder.toString()
    }

    fun withLogLevel(logLevel: LogLevel): StyledTextOutputEvent {
        return StyledTextOutputEvent(timestamp, category, logLevel, buildOperationId, spans)
    }

    public override fun withBuildOperationId(buildOperationId: OperationIdentifier): StyledTextOutputEvent {
        return StyledTextOutputEvent(timestamp, category, logLevel, buildOperationId, spans)
    }

    override fun getSpans(): MutableList<Span> {
        return spans
    }

    public override fun render(output: StyledTextOutput) {
        for (span in spans) {
            output.style(span.style)
            output.text(span.text)
        }
    }

    override fun getLevel(): LogEventLevel {
        return convert(logLevel)
    }

    class Span : StyledTextBuildOperationProgressDetails.Span {
        private val text: String
        @JvmField
        val style: StyledTextOutput.Style

        constructor(style: StyledTextOutput.Style, text: String) {
            this.style = style
            this.text = text
        }

        constructor(text: String) {
            this.style = StyledTextOutput.Style.Normal
            this.text = text
        }

        override fun equals(obj: Any?): Boolean {
            if (obj === this) {
                return true
            }
            if (obj == null || obj.javaClass != javaClass) {
                return false
            }
            val other = obj as Span
            return text == other.text && style == other.style
        }

        override fun hashCode(): Int {
            return text.hashCode() xor style.hashCode()
        }

        override fun getStyleName(): String {
            return this.style.name
        }

        override fun getText(): String {
            return text
        }

        override fun toString(): String {
            return style.toString() + ":" + text
        }
    }

    companion object {
        @JvmField
        val EOL: Span = Span(SystemProperties.getInstance().getLineSeparator())
    }
}
