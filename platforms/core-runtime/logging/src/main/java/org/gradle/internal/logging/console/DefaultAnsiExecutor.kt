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

import org.fusesource.jansi.Ansi
import org.gradle.api.Action
import org.gradle.internal.UncheckedException
import org.gradle.internal.logging.text.Style
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import java.io.IOException

class DefaultAnsiExecutor(
    private val target: Appendable,
    private val colorMap: ColorMap,
    private val factory: AnsiFactory,
    private val consoleMetaData: ConsoleMetaData,
    private val writeCursor: Cursor,
    private val listener: NewLineListener
) : AnsiExecutor {
    override fun write(action: Action<in AnsiContext>) {
        val ansi = factory.create()
        action.execute(DefaultAnsiExecutor.AnsiContextImpl(ansi, colorMap, writeCursor))
        write(ansi)
    }

    override fun writeAt(writePos: Cursor, action: Action<in AnsiContext>) {
        val ansi = factory.create()
        positionCursorAt(writePos, ansi)
        action.execute(DefaultAnsiExecutor.AnsiContextImpl(ansi, colorMap, writePos))
        write(ansi)
    }

    private fun charactersWritten(cursor: Cursor, count: Int) {
        writeCursor.col += count
        cursor.copyFrom(writeCursor)
    }

    private fun newLineWritten(cursor: Cursor) {
        writeCursor.col = 0

        // On any line except the bottom most one, a new line simply move the cursor to the next row.
        // Note: the next row has a lower index.
        if (writeCursor.row > 0) {
            writeCursor.row--
        } else {
            writeCursor.row = 0
        }
        cursor.copyFrom(writeCursor)
    }

    private fun positionCursorAt(position: Cursor, ansi: Ansi) {
        if (writeCursor.row == position.row) {
            if (writeCursor.col == position.col) {
                return
            }
            if (writeCursor.col < position.col) {
                ansi.cursorRight(position.col - writeCursor.col)
            } else {
                ansi.cursorLeft(writeCursor.col - position.col)
            }
        } else {
            if (writeCursor.col > 0) {
                ansi.cursorLeft(writeCursor.col)
            }
            if (writeCursor.row < position.row) {
                ansi.cursorUp(position.row - writeCursor.row)
            } else {
                ansi.cursorDown(writeCursor.row - position.row)
            }
            if (position.col > 0) {
                ansi.cursorRight(position.col)
            }
        }
        writeCursor.copyFrom(position)
    }

    private fun write(ansi: Ansi) {
        try {
            target.append(ansi.toString())
        } catch (e: IOException) {
            throw UncheckedException.throwAsUncheckedException(e)
        }
    }

    internal interface NewLineListener {
        fun beforeNewLineWritten(ansi: AnsiContext, writeCursor: Cursor)

        fun beforeLineWrap(ansi: AnsiContext, writeCursor: Cursor)
        fun afterLineWrap(ansi: AnsiContext, writeCursor: Cursor)
    }

    private inner class AnsiContextImpl(private val delegate: Ansi, private val colorMap: ColorMap, private val writePos: Cursor) : AnsiContext {
        override fun withColor(color: ColorMap.Color, action: Action<in AnsiContext>): AnsiContext {
            color.on(delegate)
            action.execute(this)
            color.off(delegate)
            return this
        }

        override fun withStyle(style: Style, action: Action<in AnsiContext>): AnsiContext {
            if (Style.NORMAL == style) {
                action.execute(this)
                return this
            }
            return withColor(colorMap.getColourFor(style), action)
        }

        override fun withStyle(style: StyledTextOutput.Style, action: Action<in AnsiContext>): AnsiContext {
            return withColor(colorMap.getColourFor(style), action)
        }

        override fun a(value: CharSequence): AnsiContext {
            if (value.length > 0) {
                val cols = consoleMetaData.cols

                val numberOfWrapBefore = if (cols > 0) writeCursor.col / (cols + 1) else 0
                delegate.a(value)
                charactersWritten(writePos, value.length)
                val numberOfWrapAfter = if (cols > 0) writeCursor.col / (cols + 1) else 0

                var numberOfWrap = numberOfWrapAfter - numberOfWrapBefore
                if (numberOfWrap > 0) {
                    while (numberOfWrap-- > 0) {
                        listener.beforeLineWrap(this, Cursor.Companion.at(writePos.row, cols))
                        if (writePos.row != 0) {
                            --writePos.row
                        }
                        if (writeCursor.row != 0) {
                            --writeCursor.row // We don't adjust the column value as in the event we unwrap, we want to keep correctness
                        }
                    }

                    val col = writeCursor.col % cols
                    listener.afterLineWrap(this, Cursor.Companion.at(writePos.row, col))
                }
            }
            return this
        }

        override fun newLines(numberOfNewLines: Int): AnsiContext {
            var numberOfNewLines = numberOfNewLines
            while (0 < numberOfNewLines--) {
                newLine()
            }
            return this
        }

        override fun newLine(): AnsiContext {
            val cols = consoleMetaData.cols
            val col = if (cols > 0) writeCursor.col % cols else 0
            listener.beforeNewLineWritten(this, Cursor.Companion.at(writeCursor.row, col))
            delegate.newline()
            newLineWritten(writePos)
            return this
        }

        override fun eraseForward(): AnsiContext {
            delegate.eraseLine(Ansi.Erase.FORWARD)
            return this
        }

        override fun eraseAll(): AnsiContext {
            delegate.eraseLine(Ansi.Erase.ALL)
            return this
        }

        override fun cursorAt(cursor: Cursor): AnsiContext {
            positionCursorAt(cursor, delegate)
            return this
        }

        override fun writeAt(writePos: Cursor): AnsiContext {
            positionCursorAt(writePos, delegate)
            return DefaultAnsiExecutor.AnsiContextImpl(delegate, colorMap, writePos)
        }
    }
}
