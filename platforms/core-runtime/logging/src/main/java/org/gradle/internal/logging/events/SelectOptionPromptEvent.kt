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

import org.gradle.util.internal.TextUtil

class SelectOptionPromptEvent(timestamp: Long, @JvmField val question: String, @JvmField val options: MutableList<String>, @JvmField val defaultOption: Int) : PromptOutputEvent(timestamp) {
    override fun getPrompt(): String {
        val builder = StringBuilder()
        builder.append(question)
        builder.append(":")
        builder.append(TextUtil.getPlatformLineSeparator())
        for (i in options.indices) {
            builder.append("  ")
            builder.append(i + 1)
            builder.append(": ")
            builder.append(options.get(i))
            builder.append(TextUtil.getPlatformLineSeparator())
        }
        builder.append("Enter selection (default: ")
        builder.append(options.get(defaultOption))
        builder.append(") [1..")
        builder.append(options.size)
        builder.append("] ")
        return builder.toString()
    }

    override fun convert(text: String): PromptResult<Int> {
        if (text.isEmpty()) {
            return PromptResult.Companion.response<Int>(defaultOption)
        }
        val trimmed = text.trim { it <= ' ' }
        if (trimmed.matches("\\d+".toRegex())) {
            val value = trimmed.toInt()
            if (value > 0 && value <= options.size) {
                return PromptResult.Companion.response<Int>(value - 1)
            }
        }
        return PromptResult.Companion.newPrompt<Int>("Please enter a value between 1 and " + options.size + ": ")
    }
}
