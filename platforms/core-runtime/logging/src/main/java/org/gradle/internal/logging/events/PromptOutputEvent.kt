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
package org.gradle.internal.logging.events

import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.operations.OperationIdentifier

/**
 * Requests that the client present the given prompt to the user and return the user's response as a single line of text.
 *
 * The response is delivered to the [org.gradle.api.internal.tasks.userinput.UserInputReader] service.
 */
abstract class PromptOutputEvent(timestamp: Long) : RenderableOutputEvent(timestamp, "prompt", LogLevel.QUIET, null), InteractiveEvent {
    override fun render(output: StyledTextOutput) {
        // Add a newline at the start of each question
        output.println()
        output.text(this.prompt)
    }

    /**
     * Converts the given text into the response object, or returns a new prompt to display to the user.
     */
    abstract fun convert(text: String): PromptResult<*>?

    class PromptResult<T> private constructor(val response: T?, val newPrompt: String?) {
        companion object {
            fun <T> response(response: T?): PromptResult<T?> {
                return PromptResult<T?>(response, null)
            }

            @JvmStatic
            fun <T> newPrompt(newPrompt: String): PromptResult<T?> {
                return PromptResult<T?>(null, newPrompt)
            }
        }
    }

    abstract val prompt: String?

    override fun toString(): String {
        return "[" + getLogLevel() + "] [" + category + "] '" + this.prompt + "'"
    }

    override fun withBuildOperationId(buildOperationId: OperationIdentifier): RenderableOutputEvent? {
        throw UnsupportedOperationException()
    }
}
