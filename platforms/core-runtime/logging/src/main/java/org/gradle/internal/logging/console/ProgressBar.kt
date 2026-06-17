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

import com.google.common.collect.ImmutableList
import org.apache.commons.lang3.StringUtils
import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.format.TersePrettyDurationFormatter
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

class ProgressBar(
    private val consoleMetaData: ConsoleMetaData,
    private val progressBarPrefix: String,
    private val progressBarWidth: Int,
    private val progressBarSuffix: String,
    private val fillerChar: Char,
    private val incompleteChar: Char,
    private val suffix: String,
    private var current: Int,
    private var total: Int
) {
    private val elapsedTimeFormatter = TersePrettyDurationFormatter()

    private var deadlockPreventer: ExecutorService? = null
    private var failing = false
    private var lastElapsedTimeStr: String? = null
    private var formatted: MutableList<StyledTextOutputEvent.Span>? = null

    fun moreProgress(totalProgress: Int) {
        total += totalProgress
        formatted = null
    }

    fun update(failing: Boolean) {
        this.current++
        if (current > total) {
            if (deadlockPreventer == null) {
                deadlockPreventer = Executors.newSingleThreadExecutor()
            }
            val ignored = deadlockPreventer!!.submit(Runnable {
                // do not do this directly or a deadlock happens
                // to prevent that deadlock, execute it separately in another thread
                LOGGER.warn("More progress was logged than there should be ({} > {})", current, total)
            })
        }
        this.failing = this.failing || failing
        formatted = null
    }

    fun formatProgress(timerEnabled: Boolean, elapsedTime: Long): MutableList<StyledTextOutputEvent.Span> {
        val elapsedTimeStr = elapsedTimeFormatter.format(elapsedTime)
        if (formatted != null && elapsedTimeStr == lastElapsedTimeStr) {
            return formatted!!
        }

        val consoleCols = consoleMetaData.cols

        // Calculate progress percentage for both display and taskbar
        val progressPercent = (current * 100.0 / total).toInt()

        // Prepend taskbar progress sequence (invisible control sequence)
        val taskbarSequence = buildTaskbarProgressSequence(progressPercent, failing)
        val statusPrefix: String = trimToConsole(consoleCols, 0, taskbarSequence + progressBarPrefix)

        if (consoleMetaData.supportsUnicode()) {
            return getUnicodeFormatted(timerEnabled, consoleCols, statusPrefix, progressPercent, elapsedTimeStr)
        } else {
            return getAsciiFormatted(timerEnabled, consoleCols, statusPrefix, progressPercent, elapsedTimeStr)
        }
    }

    private fun getAsciiFormatted(timerEnabled: Boolean, consoleCols: Int, statusPrefix: String, progressPercent: Int, elapsedTimeStr: String): MutableList<StyledTextOutputEvent.Span> {
        val completedWidth = this.completedWidth
        val remainingWidth = progressBarWidth - completedWidth

        val coloredProgress: String = trimToConsole(consoleCols, statusPrefix.length, StringUtils.repeat(fillerChar, completedWidth))
        val statusSuffix: String = trimToConsole(
            consoleCols, statusPrefix.length + coloredProgress.length, StringUtils.repeat(incompleteChar, remainingWidth)
                    + renderProgressStatus(timerEnabled, progressPercent, elapsedTimeStr)
        )

        lastElapsedTimeStr = elapsedTimeStr
        return createFormattedList(statusPrefix, coloredProgress, statusSuffix).also { formatted = it }
    }

    private fun createFormattedList(statusPrefix: String, coloredProgress: String, statusSuffix: String): MutableList<StyledTextOutputEvent.Span> {
        return ImmutableList.of<StyledTextOutputEvent.Span>(
            StyledTextOutputEvent.Span(StyledTextOutput.Style.Header, statusPrefix),
            StyledTextOutputEvent.Span(if (failing) StyledTextOutput.Style.FailureHeader else StyledTextOutput.Style.SuccessHeader, coloredProgress),
            StyledTextOutputEvent.Span(StyledTextOutput.Style.Header, statusSuffix)
        )
    }

    private fun getUnicodeFormatted(timerEnabled: Boolean, consoleCols: Int, statusPrefix: String, progressPercent: Int, elapsedTimeStr: String): MutableList<StyledTextOutputEvent.Span> {
        // Unicode mode: use block characters for finer granularity (8x resolution)
        val progress = this.progressString

        val coloredProgress: String = trimToConsole(consoleCols, statusPrefix.length, progress)
        val statusSuffix: String = trimToConsole(
            consoleCols, statusPrefix.length + coloredProgress.length,
            renderProgressStatus(timerEnabled, progressPercent, elapsedTimeStr)
        )

        lastElapsedTimeStr = elapsedTimeStr
        return createFormattedList(statusPrefix, coloredProgress, statusSuffix).also { formatted = it }
    }

    private fun renderProgressStatus(timerEnabled: Boolean, progressPercent: Int, elapsedTimeStr: String): String {
        return (progressBarSuffix + " " + progressPercent + '%' + ' ' + suffix
                + (if (timerEnabled) " [" + elapsedTimeStr + "]" else ""))
    }

    private val progressString: String
        get() {
            val progressRatio = this.progressRatio

            // Calculate progress in eighths (8 sub-divisions per character)
            val totalEighths = progressBarWidth * 8.0
            val completedEighths = (progressRatio * totalEighths).toInt()

            val progress = StringBuilder(progressBarWidth)
            for (i in 0..<progressBarWidth) {
                val eighthsAtPosition = i * 8
                val remainingEighths = min(8, max(0, completedEighths - eighthsAtPosition))

                progress.append(UNICODE_BLOCKS[remainingEighths])
            }

            return progress.toString()
        }

    private val completedWidth: Int
        get() {
            // ASCII mode: traditional hash-based progress
            if (current > total) {
                return progressBarWidth - 1
            }
            return (current.toDouble() / total * progressBarWidth).toInt()
        }

    private val progressRatio: Double
        get() {
            if (current > total) {
                // progress was reported excessively, show almost complete
                return (progressBarWidth - 1.0) / progressBarWidth
            }
            return current.toDouble() / total
        }

    /**
     * Generates OSC 9;4 sequence for taskbar progress (ConEmu, Ghostty).
     * Format: ESC ] 9 ; 4 ; state ; progress ST
     * States: 0=remove, 1=normal, 2=error, 3=indeterminate, 4=paused
     */
    private fun buildTaskbarProgressSequence(progressPercent: Int, isError: Boolean): String {
        if (!consoleMetaData.supportsTaskbarProgress()) {
            return ""
        }
        val state = if (isError) 2 else 1 // 1=normal, 2=error
        return buildOsc94Sequence(state.toString() + ";" + progressPercent)
    }

    companion object {
        const val PROGRESS_BAR_WIDTH: Int = 15

        // Unicode progress bar style (Linux/macOS) - avoids ligature-triggering sequences
        const val UNICODE_PROGRESS_BAR_PREFIX: String = "│"
        const val UNICODE_PROGRESS_BAR_SUFFIX: String = "│"

        // ASCII progress bar style (fallback/compatibility) - simple hash-based progress for non-Unicode terminals
        const val ASCII_PROGRESS_BAR_PREFIX: String = "["
        const val ASCII_PROGRESS_BAR_COMPLETE_CHAR: Char = '#'
        const val ASCII_PROGRESS_BAR_INCOMPLETE_CHAR: Char = '.'
        const val ASCII_PROGRESS_BAR_SUFFIX: String = "]"
        private val LOGGER: Logger = LoggerFactory.getLogger(ProgressBar::class.java)

        // Unicode block characters for smoother progress display (U+258F to U+2588)
        // Note: These characters require font support. Modern monospace fonts (Fira Code,
        // JetBrains Mono, Cascadia Code, DejaVu Sans Mono) support them well. Older fonts
        // like Courier New may only have the full block (█) and show replacement characters
        // for partial blocks. The detection logic in ConsoleMetaData.supportsUnicode() uses
        // conservative heuristics (UTF-8 locale, modern terminal detection) to avoid enabling
        // Unicode mode when fonts are unlikely to support these characters.
        private val UNICODE_BLOCKS = charArrayOf(
            '·',  // Empty
            '▏',  // ▏ 1/8 block  '\u258F',
            '▎',  // ▎ 2/8 block  '\u258E',
            '▍',  // ▍ 3/8 block  '\u258D',
            '▌',  // ▌ 4/8 block  '\u258C',
            '▋',  // ▋ 5/8 block  '\u258B',
            '▊',  // ▊ 6/8 block  '\u258A',
            '▉',  // ▉ 7/8 block  '\u2589',
            '█' // █ full block '\u2588'
        )

        fun createProgressBar(consoleMetaData: ConsoleMetaData, initialSuffix: String, totalProgress: Int): ProgressBar {
            // Use Unicode progress bars if terminal supports it, otherwise use ASCII
            if (consoleMetaData.supportsUnicode()) {
                return getUnicodeProgressBar(consoleMetaData, initialSuffix, totalProgress)
            } else {
                return getAsciiProgressBar(consoleMetaData, initialSuffix, totalProgress)
            }
        }

        // Unicode mode: smooth progress with block characters
        fun getUnicodeProgressBar(consoleMetaData: ConsoleMetaData, initialSuffix: String, totalProgress: Int): ProgressBar {
            return ProgressBar(
                consoleMetaData,
                UNICODE_PROGRESS_BAR_PREFIX,
                PROGRESS_BAR_WIDTH,
                UNICODE_PROGRESS_BAR_SUFFIX,
                ' ',  // Not used in Unicode mode
                ' ',  // Not used in Unicode mode
                initialSuffix,
                0,
                totalProgress
            )
        }

        // ASCII mode: hash-based progress for compatibility
        fun getAsciiProgressBar(consoleMetaData: ConsoleMetaData, initialSuffix: String, totalProgress: Int): ProgressBar {
            return ProgressBar(
                consoleMetaData,
                ASCII_PROGRESS_BAR_PREFIX,
                PROGRESS_BAR_WIDTH,
                ASCII_PROGRESS_BAR_SUFFIX,
                ASCII_PROGRESS_BAR_COMPLETE_CHAR,
                ASCII_PROGRESS_BAR_INCOMPLETE_CHAR,
                initialSuffix,
                0,
                totalProgress
            )
        }

        private fun trimToConsole(cols: Int, prefixLength: Int, str: String): String {
            val consoleWidth = cols - 1
            val remainingWidth = consoleWidth - prefixLength

            if (consoleWidth < 0) {
                return str
            }
            if (remainingWidth <= 0) {
                return ""
            }
            if (consoleWidth < str.length) {
                return str.substring(0, consoleWidth)
            }
            return str
        }

        /**
         * Returns the OSC 9;4;0 sequence to remove taskbar progress (ConEmu, Ghostty),
         * or an empty string if the terminal does not support taskbar progress.
         * Should be sent when the build ends or is interrupted (e.g. SIGINT).
         */
        @JvmStatic
        fun buildTaskbarProgressResetSequence(consoleMetaData: ConsoleMetaData): String {
            if (!consoleMetaData.supportsTaskbarProgress()) {
                return ""
            }
            // State 0 = remove; progress field is omitted as it is not applicable
            return buildOsc94Sequence("0")
        }

        // ESC ] 9 ; 4 ; state [; progress] BEL
        // Using BEL (0x07) instead of ST (ESC \) for broader compatibility
        private fun buildOsc94Sequence(command: String): String {
            return "\u001B]9;4;" + command + "\u0007"
        }
    }
}
