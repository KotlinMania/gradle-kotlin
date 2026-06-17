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

import net.rubygrapefruit.platform.terminal.TerminalOutput

class NativePlatformConsoleMetaData(
    private val isStdoutATerminal: Boolean,
    private val isStderrATerminal: Boolean,
    private val terminal: TerminalOutput,
    private val disableUnicodeSupportDetection: Boolean
) : ConsoleMetaData {
    override fun isStdOutATerminal(): Boolean {
        return isStdoutATerminal
    }

    override fun isStdErrATerminal(): Boolean {
        return isStderrATerminal
    }

    override fun getCols(): Int {
        return terminal.getTerminalSize().getCols()
    }

    override fun getRows(): Int {
        return terminal.getTerminalSize().getRows()
    }

    override fun isWrapStreams(): Boolean {
        return true
    }

    override fun supportsUnicode(): Boolean {
        if (disableUnicodeSupportDetection) {
            return false
        }
        return super.supportsUnicode()
    }
}
