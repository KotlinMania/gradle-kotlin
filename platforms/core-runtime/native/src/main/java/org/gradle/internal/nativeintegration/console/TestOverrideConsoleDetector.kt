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
package org.gradle.internal.nativeintegration.console

import kotlin.String

class TestOverrideConsoleDetector(private val detector: ConsoleDetector) : ConsoleDetector {
    override fun getConsole(): ConsoleMetaData? {
        val testConsole = System.getProperty(TEST_CONSOLE_PROPERTY)
        if (testConsole != null) {
            return TestConsoleMetadata.valueOf(testConsole)
        }
        return detector.getConsole()
    }

    override fun isInteractiveConsole(): Boolean {
        if (java.lang.Boolean.getBoolean(INTERACTIVE_CONSOLE_TOGGLE)) {
            return true
        }
        return detector.isInteractiveConsole()
    }

    companion object {
        const val INTERACTIVE_CONSOLE_TOGGLE: String = "org.gradle.internal.interactive"
        const val TEST_CONSOLE_PROPERTY: String = "org.gradle.internal.console.test-console"
    }
}
