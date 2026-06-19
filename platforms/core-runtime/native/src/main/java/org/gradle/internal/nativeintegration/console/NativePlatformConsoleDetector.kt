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

import net.rubygrapefruit.platform.NativeException
import net.rubygrapefruit.platform.terminal.Terminals
import org.fusesource.jansi.internal.Kernel32
import org.gradle.internal.os.OperatingSystem

class NativePlatformConsoleDetector(private val terminals: Terminals) : ConsoleDetector {
    override fun getConsole(): ConsoleMetaData? {
        // Dumb terminal doesn't support ANSI control codes.
        // TODO - remove this when we use Terminal rather than JAnsi to render to console
        val term = System.getenv("TERM")
        val operatingSystem = OperatingSystem.current()
        if ("dumb" == term || (operatingSystem.isUnix && term == null)) {
            return null
        }

        val isStdoutATerminal = terminals.isTerminal(Terminals.Output.Stdout)
        val isStderrATerminal = terminals.isTerminal(Terminals.Output.Stderr)
        val disableUnicodeSupportDetection: Boolean = isWindowsWithNonUnicodeCodePage

        try {
            if (isStdoutATerminal) {
                return NativePlatformConsoleMetaData(isStdoutATerminal, isStderrATerminal, terminals.getTerminal(Terminals.Output.Stdout), disableUnicodeSupportDetection)
            } else if (isStderrATerminal) {
                return NativePlatformConsoleMetaData(isStdoutATerminal, isStderrATerminal, terminals.getTerminal(Terminals.Output.Stderr), disableUnicodeSupportDetection)
            } else {
                return null
            }
        } catch (ex: NativeException) {
            // if a native terminal exists but cannot be resolved, use dumb terminal settings
            // this can happen if a terminal is in use that does not have its terminfo installed
            return null
        }
    }

    override fun isInteractiveConsole(): Boolean {
        return terminals.isTerminalInput()
    }

    companion object {
        private const val WINDOWS_UTF8_CODEPAGE_ID = 65001

        val isWindowsWithNonUnicodeCodePage: Boolean
            get() =//see https://learn.microsoft.com/en-us/windows/win32/intl/code-page-identifiers (e.g. code page 65001 is UTF-8)
                OperatingSystem.current()
                    .isWindows && Kernel32.GetConsoleOutputCP() != WINDOWS_UTF8_CODEPAGE_ID
    }
}
