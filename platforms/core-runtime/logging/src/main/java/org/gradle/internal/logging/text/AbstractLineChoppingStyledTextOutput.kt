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
import org.gradle.internal.SystemProperties
import kotlin.math.abs

/**
 * A [StyledTextOutput] that breaks text up into lines.
 */
abstract class AbstractLineChoppingStyledTextOutput protected constructor(private val eol: String = SystemProperties.getInstance().getLineSeparator()) : AbstractStyledTextOutput() {
    private val eolChars: CharArray
    private var seenFromEol: SeenFromEol
    private var currentState: State = INITIAL_STATE

    override fun doAppend(text: String) {
        val context: StateContext = AbstractLineChoppingStyledTextOutput.StateContext(text)

        while (context.hasChar()) {
            currentState.execute(context)
        }
        seenFromEol = context.seenFromEol
        context.flushLineText()
    }

    /**
     * Called before text is about to be appended to the start of a line.
     */
    protected open fun doStartLine() {
    }

    /**
     * Called when text is to be appended. Does not include any end-of-line separators.
     */
    protected abstract fun doLineText(text: CharSequence?)

    /**
     * Called when end of line is to be appended.
     */
    protected abstract fun doEndLine(endOfLine: CharSequence?)

    private interface State : Action<StateContext?>

    private inner class StateContext(private val text: String) {
        private val seenFromEol: SeenFromEol = this@AbstractLineChoppingStyledTextOutput.seenFromEol.copy()
        private val eolChars: CharArray = this@AbstractLineChoppingStyledTextOutput.eolChars
        private val eol: String = this@AbstractLineChoppingStyledTextOutput.eol

        private val max: Int

        private var start: Int
        private var pos: Int

        init {
            this.max = text.length
            this.pos = -seenFromEol.size()
            this.start = pos
        }

        fun next() {
            pos++
        }

        fun next(count: Int) {
            pos += count
        }

        fun isCurrentCharEquals(value: Char): Boolean {
            val ch: Char
            if (seenFromEol.size() + pos < 0) {
                ch = eolChars[pos + this@AbstractLineChoppingStyledTextOutput.seenFromEol.size()]
            } else {
                ch = text.get(pos + seenFromEol.size())
            }
            return ch == value
        }

        fun hasChar(): Boolean {
            return pos + seenFromEol.size() < max
        }

        fun setState(state: State) {
            currentState = state
        }

        fun reset() {
            start = pos
            seenFromEol.clear()
        }

        fun flushLineText() {
            // Left over data from previous append is only possible when a multi-chars new line is
            // been processed and split across multiple append calls.
            if (start < pos) {
                var data = ""
                // Flushing data split across previous and current appending
                if (start < 0 && pos >= 0) {
                    data = seenFromEol.string(abs(start)) + text.substring(0, pos)
                    // Flushing data coming only from current appending
                } else if (start >= 0) {
                    data = text.substring(start, pos)
                }

                if (data.length > 0) {
                    doLineText(data)
                }
            }
        }

        fun flushEndLine(eol: String?) {
            doEndLine(eol)
        }

        fun flushStartLine() {
            doStartLine()
        }
    }

    init {
        eolChars = eol.toCharArray()
        seenFromEol = SeenFromEol(eolChars)
    }

    private class SeenFromEol {
        private val eol: CharArray
        private val seen: CharArray
        private var count: Int

        internal constructor(eol: CharArray) {
            this.eol = eol
            this.seen = CharArray(eol.size)
            this.count = 0
        }

        private constructor(eol: CharArray, seen: CharArray, count: Int) {
            this.eol = eol
            this.seen = seen
            this.count = count
        }

        fun copy(): SeenFromEol {
            return SeenFromEol(eol, seen.copyOf(seen.size), count)
        }

        fun add(c: Char) {
            seen[count++] = c
        }

        fun add() {
            seen[count] = eol[count]
            count++
        }

        fun clear() {
            count = 0
        }

        fun size(): Int {
            return count
        }

        fun all(): Boolean {
            return count == seen.size
        }

        fun none(): Boolean {
            return count == 0
        }

        fun string(length: Int): String {
            return String(seen, 0, length)
        }
    }

    companion object {
        private val SYSTEM_EOL_PARSING_STATE: State = object : State {
            override fun execute(context: StateContext) {
                if (!context.seenFromEol.all()) {
                    if (context.eol != "\r\n" && context.isCurrentCharEquals(context.eolChars[context.seenFromEol.size()])) {
                        context.seenFromEol.add()
                        if (context.seenFromEol.all()) {
                            context.flushLineText()
                            context.flushEndLine(context.eol)
                            context.next(context.seenFromEol.size())
                            context.reset()
                            context.setState(START_LINE_STATE)
                        }
                        return
                    } else if (context.seenFromEol.none()) {
                        WELL_KNOWN_EOL_PARSING_STATE.execute(context)
                        return
                    }
                }

                context.next(context.seenFromEol.size())
                context.flushLineText()
                context.reset()
                context.setState(INITIAL_STATE)
            }
        }

        private val INITIAL_STATE: State = SYSTEM_EOL_PARSING_STATE

        private val WELL_KNOWN_EOL_PARSING_STATE: State = object : State {
            override fun execute(context: StateContext) {
                if (context.isCurrentCharEquals('\r')) {
                    context.seenFromEol.add('\r')
                    context.setState(WINDOWS_EOL_PARSING_ODDITY_STATE)
                } else if (context.isCurrentCharEquals('\n')) {
                    context.flushLineText()
                    context.flushEndLine("\n")
                    context.next()
                    context.reset()
                    context.setState(START_LINE_STATE)
                } else {
                    context.next()
                    context.setState(INITIAL_STATE)
                }
            }
        }

        private val WINDOWS_EOL_PARSING_ODDITY_STATE: State = object : State {
            override fun execute(context: StateContext) {
                if (context.isCurrentCharEquals('\n')) {
                    context.flushLineText()
                    context.flushEndLine("\r\n")
                    context.next(2)
                    context.reset()
                    context.setState(START_LINE_STATE)
                } else if (context.isCurrentCharEquals('\r')) {
                    context.next()
                } else {
                    context.next()
                    context.seenFromEol.clear()
                    context.setState(INITIAL_STATE)
                }
            }
        }

        private val START_LINE_STATE: State = object : State {
            override fun execute(context: StateContext) {
                context.flushStartLine()
                context.setState(INITIAL_STATE)
            }
        }
    }
}
