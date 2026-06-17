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
import org.gradle.internal.logging.events.StyledTextOutputEvent

class DefaultRedrawableLabel internal constructor(// Relative coordinate system
    val writePosition: Cursor
) : RedrawableLabel {
    private var spans = mutableListOf<StyledTextOutputEvent.Span>()
    private var writtenSpans = mutableListOf<StyledTextOutputEvent.Span>()
    private var absolutePositionRow = 0 // Absolute coordinate system
    private var previousWriteRow = absolutePositionRow
    private var isVisible = true
    private val previousVisibility = isVisible

    override fun setText(text: String) {
        this.spans = mutableListOf<StyledTextOutputEvent.Span>(StyledTextOutputEvent.Span(text))
    }

    override fun setText(span: StyledTextOutputEvent.Span) {
        this.spans = mutableListOf<StyledTextOutputEvent.Span>(span)
    }

    override fun setText(spans: MutableList<StyledTextOutputEvent.Span>) {
        this.spans = spans
    }

    fun setVisible(isVisible: Boolean) {
        this.isVisible = isVisible
    }

    fun isOverlappingWith(cursor: Cursor): Boolean {
        return cursor.row == writePosition.row && writePosition.col > cursor.col
    }

    override fun redraw(ansi: AnsiContext) {
        if (writePosition.row < 0) {
            // Does not need to be redrawn if component is out of bound
            return
        }

        if (!isVisible && previousVisibility) {
            if (previousWriteRow == absolutePositionRow && writtenSpans.isEmpty()) {
                // Does not need to be redrawn
                return
            }

            writePosition.col = 0
            ansi.cursorAt(this.writePosition)!!.eraseAll()

            writtenSpans = mutableListOf<StyledTextOutputEvent.Span>()
        }

        if (isVisible) {
            if (previousWriteRow == absolutePositionRow && writtenSpans == spans) {
                // Does not need to be redrawn
                return
            }

            val writtenTextLength = writePosition.col
            writePosition.col = 0
            redrawText(ansi.writeAt(this.writePosition)!!, writtenTextLength)

            writtenSpans = spans
            previousWriteRow = absolutePositionRow
        }
    }

    private fun redrawText(ansi: AnsiContext, writtenTextLength: Int) {
        var textLength = 0
        for (span in spans) {
            val length = span.getText().length
            if (length > 0) {
                ansi.withStyle(span.style, writeText(span.getText()))
                textLength += length
            }
        }

        if (previousWriteRow == absolutePositionRow && textLength < writtenTextLength) {
            ansi.eraseForward()
        }
        // Note: We can't conclude anything if the label scrolled so we leave the erasing to the parent widget.
    }

    // Only for relative positioning
    fun newLineAdjustment() {
        writePosition.row++
    }

    // According to absolute positioning
    fun scrollBy(rows: Int) {
        writePosition.row -= rows
        absolutePositionRow += rows
    }

    // According to absolute positioning
    fun scrollUpBy(rows: Int) {
        scrollBy(-rows)
    }

    // According to absolute positioning
    fun scrollDownBy(rows: Int) {
        scrollBy(rows)
    }

    companion object {
        private fun writeText(text: String): Action<AnsiContext> {
            return object : Action<AnsiContext> {
                override fun execute(ansi: AnsiContext) {
                    ansi.a(text)
                }
            }
        }
    }
}
