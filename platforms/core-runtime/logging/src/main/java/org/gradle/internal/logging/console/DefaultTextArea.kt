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
package org.gradle.internal.logging.console

import org.gradle.api.Action
import org.gradle.internal.logging.text.AbstractLineChoppingStyledTextOutput

class DefaultTextArea(private val ansiExecutor: AnsiExecutor) : AbstractLineChoppingStyledTextOutput(), TextArea {
    /**
     * Returns the bottom right position of this text area.
     */
    val writePosition: Cursor = Cursor()

    fun newLineAdjustment() {
        writePosition.row++
    }

    override fun doLineText(text: CharSequence?) {
        if (text == null || text.length == 0) {
            return
        }

        ansiExecutor.writeAt(this.writePosition, object : Action<AnsiContext> {
            override fun execute(ansi: AnsiContext) {
                ansi.withStyle(style!!, object : Action<AnsiContext> {
                    override fun execute(ansi: AnsiContext) {
                        val textStr = text.toString()
                        var pos = 0
                        while (pos < text.length) {
                            val next = textStr.indexOf('\t', pos)
                            if (next == pos) {
                                val charsToNextStop: Int = CHARS_PER_TAB_STOP - (writePosition.col % CHARS_PER_TAB_STOP)
                                for (i in 0..<charsToNextStop) {
                                    ansi.a(" ")
                                }
                                pos++
                            } else if (next > pos) {
                                ansi.a(textStr.substring(pos, next))
                                pos = next
                            } else {
                                ansi.a(textStr.substring(pos))
                                pos = textStr.length
                            }
                        }
                    }
                })
            }
        })
    }

    override fun doEndLine(endOfLine: CharSequence?) {
        ansiExecutor.writeAt(this.writePosition, NEW_LINE_ACTION)
    }

    companion object {
        private val NEW_LINE_ACTION: Action<AnsiContext> = object : Action<AnsiContext> {
            override fun execute(ansi: AnsiContext) {
                ansi.newLine()
            }
        }
        private const val CHARS_PER_TAB_STOP = 8
    }
}
