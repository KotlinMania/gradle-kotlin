/*
 * Copyright 2024 the original author or authors.
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

class IntQuestionPromptEvent(timestamp: Long, @JvmField val question: String, @JvmField val minValue: Int, @JvmField val defaultValue: Int) : PromptOutputEvent(timestamp) {
    override fun getPrompt(): String {
        val builder = StringBuilder()
        builder.append(question)
        builder.append(" (min: ")
        builder.append(minValue)
        builder.append(", default: ")
        builder.append(defaultValue)
        builder.append("): ")
        return builder.toString()
    }

    override fun convert(text: String): PromptResult<Int> {
        if (text.isEmpty()) {
            return PromptResult.response<Int>(defaultValue)
        }
        val trimmed = text.trim { it <= ' ' }
        try {
            val result = trimmed.toInt()
            if (result >= minValue) {
                return PromptResult.response<Int>(result)
            }
            return PromptResult.newPrompt<Int>("Please enter an integer value >= " + minValue + " (default: " + defaultValue + "): ")
        } catch (e: NumberFormatException) {
            return PromptResult.newPrompt<Int>("Please enter an integer value (min: " + minValue + ", default: " + defaultValue + "): ")
        }
    }
}
