/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.process.internal.util

import org.gradle.internal.os.OperatingSystem

object LongCommandLineDetectionUtil {
    // See http://msdn.microsoft.com/en-us/library/windows/desktop/ms682425(v=vs.85).aspx
    const val MAX_COMMAND_LINE_LENGTH_WINDOWS: Int = 32766

    // Derived from default when running getconf ARG_MAX in OSX
    const val MAX_COMMAND_LINE_LENGTH_OSX: Int = 262144

    // Dervied from MAX_ARG_STRLEN as per http://man7.org/linux/man-pages/man2/execve.2.html
    const val MAX_COMMAND_LINE_LENGTH_NIX: Int = 131072
    private const val WINDOWS_LONG_COMMAND_EXCEPTION_MESSAGE = "The filename or extension is too long"
    private const val NIX_LONG_COMMAND_EXCEPTION_MESSAGE = "error=7, Argument list too long"

    /**
     * Java 25 changed the error message format of exec failures.
     */
    private const val NEW_NIX_LONG_COMMAND_EXCEPTION_MESSAGE = "Exec failed, error: 7 (Argument list too long)"

    fun hasCommandLineExceedMaxLength(command: String, arguments: MutableList<String>): Boolean {
        val commandLineLength = command.length + arguments.sumOf { it.length } + arguments.size
        return commandLineLength > maxCommandLineLength
    }

    private val maxCommandLineLength: Int
        get() {
            var defaultMax = MAX_COMMAND_LINE_LENGTH_NIX
            if (OperatingSystem.current().isMacOsX) {
                defaultMax = MAX_COMMAND_LINE_LENGTH_OSX
            } else if (OperatingSystem.current().isWindows) {
                defaultMax = MAX_COMMAND_LINE_LENGTH_WINDOWS
            }
            // in chars
            return Integer.getInteger("org.gradle.internal.cmdline.max.length", defaultMax)
        }

    fun hasCommandLineExceedMaxLengthException(failureCause: Throwable): Boolean {
        var cause: Throwable? = failureCause
        while (cause != null) {
            val message: String = cause.message.orEmpty()
            if (message.contains(WINDOWS_LONG_COMMAND_EXCEPTION_MESSAGE)
                || message.contains(NIX_LONG_COMMAND_EXCEPTION_MESSAGE)
                || message.contains(NEW_NIX_LONG_COMMAND_EXCEPTION_MESSAGE)
            ) {
                return true
            }
            cause = cause.cause
        }

        return false
    }
}
