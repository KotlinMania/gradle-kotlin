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

import org.gradle.internal.nativeintegration.console.ConsoleMetaData
import kotlin.math.min

class ConsoleLayoutCalculator
/**
 * @param consoleMetaData used to get console dimensions
 */(private val consoleMetaData: ConsoleMetaData) {
    private var maximumAvailableLines = -1

    /**
     * Calculate number of Console lines to use for work-in-progress display.
     *
     * @param ideal number of Console lines
     * @return height of progress area.
     */
    fun calculateNumWorkersForConsoleDisplay(ideal: Int): Int {
        if (maximumAvailableLines == -1) {
            // Disallow work-in-progress to take up more than half of the console display
            // If the screen size is unknown, allow 4 lines
            val rows = consoleMetaData.rows
            maximumAvailableLines = if (rows == 0) 4 else rows / 2
        }

        return min(ideal, maximumAvailableLines)
    }
}
