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

import org.gradle.api.Action
import org.gradle.internal.UncheckedException
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import java.io.Flushable
import java.io.IOException

class AnsiConsole private constructor(target: Appendable, private val flushable: Flushable, colorMap: ColorMap, consoleMetaData: ConsoleMetaData, factory: AnsiFactory) : Console {
    private val redrawAction: Action<AnsiContext> = object : Action<AnsiContext> {
        override fun execute(ansiContext: AnsiContext) {
            buildStatusArea.redraw(ansiContext)
            // When build output area is not visible, position the cursor at the end of the output area
            if (!buildStatusArea.isVisible()) {
                ansiContext.cursorAt(buildOutputArea.writePosition)
            }
        }
    }
    private val buildStatusArea = MultiLineBuildProgressArea()
    override val buildOutputArea: DefaultTextArea
    private val ansiExecutor: AnsiExecutor
    override val statusBar: StyledLabel
        get() = buildStatusArea.progressBar
    override val buildProgressArea: BuildProgressArea
        get() = buildStatusArea

    constructor(target: Appendable, flushable: Flushable, colorMap: ColorMap, consoleMetaData: ConsoleMetaData, forceAnsi: Boolean) : this(
        target,
        flushable,
        colorMap,
        consoleMetaData,
        DefaultAnsiFactory(forceAnsi)
    )

    init {
        this.ansiExecutor = DefaultAnsiExecutor(target, colorMap, factory, consoleMetaData, Cursor.Companion.newBottomLeft(), Listener())

        buildOutputArea = DefaultTextArea(ansiExecutor)
    }

    override fun flush() {
        redraw()
        try {
            flushable.flush()
        } catch (e: IOException) {
            throw UncheckedException.throwAsUncheckedException(e)
        }
    }

    private fun redraw() {
        // Calculate how many rows of the status area overlap with the text area
        var numberOfOverlappedRows: Int = buildStatusArea.writePosition.row - buildOutputArea.writePosition.row

        // If textArea is on a status line but nothing was written, this means a new line was just written. While
        // we wait for additional text, we assume this row doesn't count as overlapping and use it as a status
        // line. In the opposite case, we want to scroll the progress area one more line. This avoid having an one
        // line gap between the text area and the status area.
        if (buildOutputArea.writePosition.col > 0) {
            numberOfOverlappedRows++
        }

        if (numberOfOverlappedRows > 0) {
            buildStatusArea.scrollDownBy(numberOfOverlappedRows)
        }

        ansiExecutor.write(redrawAction)
    }

    private inner class Listener : DefaultAnsiExecutor.NewLineListener {
        override fun beforeNewLineWritten(ansi: AnsiContext, writeCursor: Cursor) {
            if (buildStatusArea.isOverlappingWith(writeCursor)) {
                ansi.eraseForward()
            }

            if (writeCursor.row == 0) {
                buildOutputArea.newLineAdjustment()
                buildStatusArea.newLineAdjustment()
            }
        }

        override fun beforeLineWrap(ansi: AnsiContext, writeCursor: Cursor) {
            if (writeCursor.row == 0) {
                buildStatusArea.newLineAdjustment()
            }
        }

        override fun afterLineWrap(ansi: AnsiContext, writeCursor: Cursor) {
            if (buildStatusArea.isOverlappingWith(writeCursor)) {
                ansi.eraseForward()
            }
        }
    }
}
