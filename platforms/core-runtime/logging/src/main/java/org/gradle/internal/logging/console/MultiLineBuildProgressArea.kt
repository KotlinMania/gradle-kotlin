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

import org.gradle.internal.logging.console.Cursor.Companion.at
import org.gradle.internal.logging.console.Cursor.Companion.newBottomLeft
import java.util.Collections

class MultiLineBuildProgressArea : BuildProgressArea {
    // 2 lines: 1 for BuildStatus and 1 for Cursor parking space
    private val entries: MutableList<DefaultRedrawableLabel> = ArrayList<DefaultRedrawableLabel>(2)
    private val progressBarLabel: DefaultRedrawableLabel

    private val buildProgressLabels: MutableList<StyledLabel> = ArrayList<StyledLabel>()
    private val parkingLabel: DefaultRedrawableLabel

    /**
     * The location of the top left of this progress area.
     */
    val writePosition: Cursor = Cursor()
    private var isVisible = false
    private var isPreviouslyVisible = false

    init {
        progressBarLabel = newLabel(0)
        entries.add(progressBarLabel)

        // Parking space for the write cursor
        parkingLabel = newLabel(-1)
        entries.add(parkingLabel)
    }

    public override fun getBuildProgressLabels(): MutableList<StyledLabel> {
        return Collections.unmodifiableList<StyledLabel>(buildProgressLabels)
    }

    val progressBar: StyledLabel
        get() = progressBarLabel

    val cursorParkLine: StyledLabel
        get() = parkingLabel

    val height: Int
        get() = entries.size

    override fun resizeBuildProgressTo(buildProgressLabelCount: Int) {
        var delta = buildProgressLabelCount - buildProgressLabels.size
        if (delta <= 0) {
            // We don't support shrinking at the moment
            return
        }

        var row = parkingLabel.getWritePosition().row
        parkingLabel.scrollDownBy(delta)
        while (delta-- > 0) {
            val label: DefaultRedrawableLabel = newLabel(row--)
            entries.add(entries.size - 1, label)
            buildProgressLabels.add(label)
        }
    }

    fun isVisible(): Boolean {
        return isVisible
    }

    override fun setVisible(isVisible: Boolean) {
        this.isVisible = isVisible
        for (label in entries) {
            label.setVisible(isVisible)
        }
    }

    fun isOverlappingWith(cursor: Cursor): Boolean {
        for (label in entries) {
            if (label.isOverlappingWith(cursor)) {
                return true
            }
        }
        return false
    }

    fun newLineAdjustment() {
        writePosition.row++
        for (label in entries) {
            label.newLineAdjustment()
        }
    }

    fun redraw(ansi: AnsiContext) {
        val newLines = 0 - writePosition.row + this.height - 1
        if (isVisible && newLines > 0) {
            ansi.cursorAt(newBottomLeft())!!.newLines(newLines)
        }

        // Redraw every entry of this area
        for (i in entries.indices) {
            val label = entries.get(i)

            label.redraw(ansi)

            // Ensure a clean end of the line when the area scrolls
            if (isVisible && newLines > 0 && (i + newLines) < entries.size) {
                val currentLength = label.getWritePosition().col
                val previousLength = entries.get(i + newLines).getWritePosition().col
                if (currentLength < previousLength) {
                    ansi.writeAt(label.getWritePosition())!!.eraseForward()
                }
            }
        }

        if (isPreviouslyVisible || isVisible) {
            ansi.cursorAt(parkCursor())
        }
        isPreviouslyVisible = isVisible
    }

    // According to absolute positioning
    private fun scrollBy(rows: Int) {
        writePosition.row -= rows
        for (label in entries) {
            label.scrollBy(rows)
        }
    }

    // According to absolute positioning
    fun scrollUpBy(rows: Int) {
        scrollBy(-rows)
    }

    // According to absolute positioning
    fun scrollDownBy(rows: Int) {
        scrollBy(rows)
    }

    private fun parkCursor(): Cursor {
        if (isVisible || writePosition.row < 0) {
            return newBottomLeft()
        } else {
            return this.writePosition
        }
    }

    companion object {
        private fun newLabel(row: Int): DefaultRedrawableLabel {
            return DefaultRedrawableLabel(at(row, 0))
        }
    }
}
