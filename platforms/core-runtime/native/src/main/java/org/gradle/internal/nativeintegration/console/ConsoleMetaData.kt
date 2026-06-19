/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal.nativeintegration.console

import org.gradle.util.internal.VersionNumber

interface ConsoleMetaData {
    /**
     * Returns true if the current process' stdout is attached to the console.
     */
    fun isStdOutATerminal(): Boolean

    /**
     * Returns true if the current process' stderr is attached to the console.
     */
    fun isStdErrATerminal(): Boolean

    /**
     *
     * Returns the number of columns available in the console.
     *
     * @return The number of columns available in the console. If no information is available return 0.
     */
    fun getCols(): Int

    /**
     *
     * Returns the number of rows available in the console.
     *
     * @return The height of the console (rows). If no information is available return 0.
     */
    fun getRows(): Int

    fun isWrapStreams(): Boolean

    /**
     *
     * Returns true if the console supports Unicode characters (e.g., box-drawing, block elements).
     *
     *
     * This is determined by checking terminal capabilities such as UTF-8 encoding support,
     * terminal type, and platform-specific indicators.
     *
     * @return true if Unicode characters can be safely displayed, false otherwise
     */
    fun supportsUnicode(): Boolean {
        // Auto-detection logic
        // Check for UTF-8 encoding in locale, including common "UTF8" alias
        val lang = System.getenv("LANG")
        val lcAll = System.getenv("LC_ALL")
        if ((lang != null && (lang.uppercase().contains("UTF-8") || lang.uppercase().contains("UTF8"))) ||
            (lcAll != null && (lcAll.uppercase().contains("UTF-8") || lcAll.uppercase().contains("UTF8")))
        ) {
            return true
        }

        // Check for modern terminal types that support Unicode
        val term = System.getenv("TERM")
        if (term != null) {
            val lowerTerm = term.lowercase()
            // Modern terminals that support Unicode well
            if (lowerTerm.contains("xterm") ||
                lowerTerm.contains("256color") ||
                lowerTerm.contains("screen") ||
                lowerTerm.contains("tmux") ||
                lowerTerm.contains("rxvt") ||
                lowerTerm.contains("konsole") ||
                lowerTerm.contains("gnome") ||
                lowerTerm.contains("alacritty") ||
                lowerTerm.contains("kitty") ||
                lowerTerm.contains("ghostty") ||
                lowerTerm.contains("wezterm") ||
                lowerTerm.contains("contour") ||
                lowerTerm.contains("foot") ||
                lowerTerm.contains("mlterm") ||
                lowerTerm == "st" ||
                lowerTerm.startsWith("st-") ||
                lowerTerm.contains("qterminal") ||
                lowerTerm.contains("weston")
            ) {
                return true
            }
            // Explicitly dumb terminals don't support Unicode
            if (lowerTerm == "dumb" || lowerTerm == "unknown") {
                return false
            }
        }

        // Windows Terminal and other modern Windows consoles support Unicode
        if (System.getenv("WT_SESSION") != null || System.getenv("WT_PROFILE_ID") != null) {
            return true
        }

        // ConEmu supports Unicode
        if (System.getenv("ConEmuPID") != null) {
            return true
        }

        // On Windows, fallback to checking if Windows 10+ (which has better Unicode support)
        // but be conservative - default to false unless we can confirm support
        return false
    }

    /**
     *
     * Returns true if the terminal supports OSC 9;4 taskbar progress sequences.
     *
     *
     * This sequence (ESC ] 9 ; 4 ; state ; progress ST) allows applications to control
     * progress indicators in the Windows taskbar or terminal window decorations.
     *
     *
     * Supported by: ConEmu, Ghostty, and potentially other terminals.
     *
     * @return true if OSC 9;4 sequences are supported, false otherwise
     */
    fun supportsTaskbarProgress(): Boolean {
        return TASK_BAR_PROGRESS_SUPPORTED
    }

    companion object {
        fun evaluateTaskBarProgressSupport(): Boolean {
            // ConEmu explicitly supports OSC 9;4 sequences
            if (System.getenv("ConEmuPID") != null) {
                return true
            }

            // Ghostty and kitty support OSC 9;4 sequences
            val term = System.getenv("TERM")
            if (term != null) {
                val termLowerCase = term.lowercase()
                if (termLowerCase.contains("ghostty") ||
                    termLowerCase.contains("kitty")
                ) {
                    return true
                }
            }

            // iTerm2 supports OSC progress bar starting from version 3.6.6
            val termProgram = System.getenv("TERM_PROGRAM")
            if ("iTerm.app" == termProgram) {
                val version = System.getenv("TERM_PROGRAM_VERSION")
                if (version != null) {
                    val versionNumber = VersionNumber.parse(version)
                    return versionNumber.compareTo(VersionNumber.parse("3.6.6")) >= 0
                }
            }

            return false
        }

        val TASK_BAR_PROGRESS_SUPPORTED: Boolean = evaluateTaskBarProgressSupport()
    }
}
