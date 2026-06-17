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

import com.google.common.collect.ImmutableList
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils

class BooleanQuestionPromptEvent(timestamp: Long, @JvmField val question: String, @JvmField val defaultValue: Boolean) : PromptOutputEvent(timestamp) {
    override fun getPrompt(): String {
        val builder = StringBuilder()
        builder.append(question)
        builder.append(" (default: ")
        val defaultString = if (defaultValue) "yes" else "no"
        builder.append(defaultString)
        builder.append(") [")
        builder.append(StringUtils.join(YesNoQuestionPromptEvent.YES_NO_CHOICES, ", "))
        builder.append("] ")
        return builder.toString()
    }

    override fun convert(text: String): PromptResult<Boolean> {
        if (text.isEmpty()) {
            return PromptResult.response<Boolean>(defaultValue)
        }
        val trimmed = text.lowercase().trim { it <= ' ' }
        if (LENIENT_YES_NO_CHOICES.contains(trimmed)) {
            return PromptResult.response<Boolean>(BooleanUtils.toBoolean(trimmed))
        }
        val defaultString = if (defaultValue) "yes" else "no"
        return PromptResult.newPrompt<Boolean>("Please enter 'yes' or 'no' (default: '" + defaultString + "'): ")
    }

    companion object {
        private val LENIENT_YES_NO_CHOICES: MutableList<String> = ImmutableList.of<String>("yes", "no", "y", "n")
    }
}
