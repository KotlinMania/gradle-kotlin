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
package org.gradle.internal.logging.services

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.text.AbstractLineChoppingStyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.time.Clock

/**
 * A [StyledTextOutput] implementation which generates events of type [ ]. This implementation is not thread-safe.
 */
class LoggingBackedStyledTextOutput(private val listener: OutputEventListener, private val category: String, private val logLevel: LogLevel, private val clock: Clock) :
    AbstractLineChoppingStyledTextOutput() {
    private val buffer = StringBuilder()
    private var spans: MutableList<StyledTextOutputEvent.Span?> = ArrayList<StyledTextOutputEvent.Span?>()
    private var style = StyledTextOutput.Style.Normal

    override fun doStyleChange(style: StyledTextOutput.Style) {
        if (buffer.length > 0) {
            spans.add(StyledTextOutputEvent.Span(this.style, buffer.toString()))
            buffer.setLength(0)
        }
        this.style = style
    }

    override fun doLineText(text: CharSequence?) {
        buffer.append(text)
    }

    override fun doEndLine(endOfLine: CharSequence?) {
        buffer.append(endOfLine)
        spans.add(StyledTextOutputEvent.Span(this.style, buffer.toString()))
        buffer.setLength(0)
        val buildOperationId = CurrentBuildOperationRef.instance().getId()
        listener.onOutput(StyledTextOutputEvent(clock.currentTime, category, logLevel, buildOperationId, spans))
        spans = ArrayList<StyledTextOutputEvent.Span?>()
    }
}
