/*
 * Copyright 2018 the original author or authors.
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

enum class TestConsoleMetadata(private val attachedToStdout: Boolean, private val attachedToStderr: Boolean) : ConsoleMetaData {
    BOTH(true, true),
    STDOUT_ONLY(true, false),
    STDERR_ONLY(false, true);

    override fun isStdOutATerminal(): Boolean {
        return attachedToStdout
    }

    override fun isStdErrATerminal(): Boolean {
        return attachedToStderr
    }

    override fun getCols(): Int {
        return 130
    }

    override fun getRows(): Int {
        return 40
    }

    override fun isWrapStreams(): Boolean {
        return false
    }

    val commandLineArgument: String
        get() = "-D" + TestOverrideConsoleDetector.Companion.TEST_CONSOLE_PROPERTY + "=" + name
}
