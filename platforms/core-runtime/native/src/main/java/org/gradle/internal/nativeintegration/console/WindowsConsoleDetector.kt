/*
 * Copyright 2010 the original author or authors.
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

import org.fusesource.jansi.io.WindowsAnsiProcessor
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream

class WindowsConsoleDetector : ConsoleDetector {
    override fun getConsole(): ConsoleMetaData? {
        // Use Jansi's detection mechanism
        try {
            WindowsAnsiProcessor(PrintStream(ByteArrayOutputStream()), true)
            val disableUnicodeSupportDetection: Boolean = NativePlatformConsoleDetector.isWindowsWithNonUnicodeCodePage
            return object : UnicodeProxyConsoleMetaData(FallbackConsoleMetaData.ATTACHED) {
                override fun supportsUnicode(): Boolean {
                    if (disableUnicodeSupportDetection) {
                        return false
                    }
                    return metaData.supportsUnicode()
                }
            }
        } catch (ignore: IOException) {
            // Not attached to a console
            return null
        }
    }

    override fun isInteractiveConsole(): Boolean {
        return System.console() != null
    }
}
