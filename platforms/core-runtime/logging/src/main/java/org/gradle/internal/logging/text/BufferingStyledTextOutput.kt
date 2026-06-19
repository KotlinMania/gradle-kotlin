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
package org.gradle.internal.logging.text

import org.gradle.api.Action

/**
 * A [StyledTextOutput] which buffers the content written to it, for later forwarding to another [StyledTextOutput] instance.
 */
class BufferingStyledTextOutput : AbstractStyledTextOutput() {
    private val events: MutableList<Action<StyledTextOutput>> = ArrayList<Action<StyledTextOutput>>()
    var hasContent: Boolean = false
        private set

    /**
     * Writes the buffered contents of this output to the given target, and clears the buffer.
     */
    fun writeTo(output: StyledTextOutput) {
        for (event in events) {
            event.execute(output)
        }
        events.clear()
    }

    override fun doStyleChange(style: StyledTextOutput.Style?) {
        if (!events.isEmpty() && (events.get(events.size - 1) is ChangeStyleAction)) {
            events.removeAt(events.size - 1)
        }
        events.add(ChangeStyleAction(style))
    }

    override fun doAppend(text: String?) {
        val text = text ?: "null"
        if (text.isEmpty()) {
            return
        }
        hasContent = true
        events.add(object : Action<StyledTextOutput> {
            override fun execute(styledTextOutput: StyledTextOutput) {
                styledTextOutput.text(text)
            }
        })
    }

    private class ChangeStyleAction(private val style: StyledTextOutput.Style?) : Action<StyledTextOutput> {
        override fun execute(styledTextOutput: StyledTextOutput) {
            styledTextOutput.style(style)
        }
    }
}
