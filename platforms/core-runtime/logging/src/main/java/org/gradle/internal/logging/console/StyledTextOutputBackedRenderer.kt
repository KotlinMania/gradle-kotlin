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
package org.gradle.internal.logging.console

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.LogLevelChangeEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.RenderableOutputEvent
import org.gradle.internal.logging.text.AbstractLineChoppingStyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutput
import java.text.SimpleDateFormat
import java.util.Date

class StyledTextOutputBackedRenderer(textOutput: StyledTextOutput) : OutputEventListener {
    private val textOutput: OutputEventTextOutputImpl
    private var debugOutput = false
    private var dateFormat: SimpleDateFormat? = null
    private var lastEvent: RenderableOutputEvent? = null

    init {
        this.textOutput = OutputEventTextOutputImpl(textOutput)
    }

    override fun onOutput(event: OutputEvent) {
        if (event is LogLevelChangeEvent) {
            val changeEvent = event
            val newLogLevelIsDebug = changeEvent.newLogLevel == LogLevel.DEBUG
            if (newLogLevelIsDebug && dateFormat == null) {
                dateFormat = SimpleDateFormat(ISO_8601_DATE_TIME_FORMAT)
            }
            debugOutput = newLogLevelIsDebug
        }
        if (event is RenderableOutputEvent) {
            val outputEvent = event
            textOutput.style(if (outputEvent.logLevel == LogLevel.ERROR) StyledTextOutput.Style.Error else StyledTextOutput.Style.Normal)
            if (debugOutput && (textOutput.atEndOfLine || lastEvent == null || (lastEvent!!.category != outputEvent.category))) {
                if (!textOutput.atEndOfLine) {
                    textOutput.println()
                }
                textOutput.text(dateFormat!!.format(Date(outputEvent.timestamp)))
                textOutput.text(" [")
                textOutput.text(outputEvent.logLevel)
                textOutput.text("] [")
                textOutput.text(outputEvent.category)
                textOutput.text("] ")
            }
            outputEvent.render(textOutput)
            lastEvent = outputEvent
            textOutput.style(StyledTextOutput.Style.Normal)
        }
    }

    private class OutputEventTextOutputImpl(private val textOutput: StyledTextOutput) : AbstractLineChoppingStyledTextOutput() {
        var atEndOfLine = true
            private set

        override fun doStyleChange(style: StyledTextOutput.Style?) {
            textOutput.style(style)
        }

        override fun doLineText(text: CharSequence?) {
            textOutput.text(text)
            atEndOfLine = false
        }

        override fun doEndLine(endOfLine: CharSequence?) {
            textOutput.text(endOfLine)
            atEndOfLine = true
        }
    }

    companion object {
        const val ISO_8601_DATE_TIME_FORMAT: String = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
    }
}
