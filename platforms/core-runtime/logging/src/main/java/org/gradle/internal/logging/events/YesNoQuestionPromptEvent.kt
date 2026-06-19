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
import org.gradle.internal.logging.events.PromptOutputEvent.PromptResult.Companion.newPrompt

class YesNoQuestionPromptEvent(timestamp: Long, @JvmField val question: String?) : PromptOutputEvent(timestamp) {
    override val prompt: String
        get() {
            val builder = StringBuilder()
            builder.append(question)
            builder.append(" [")
            builder.append(StringUtils.join(YES_NO_CHOICES, ", "))
            builder.append("] ")
            return builder.toString()
        }

    public override fun convert(text: String): PromptResult<Boolean> {
        val trimmed = text.trim { it <= ' ' }
        if (YES_NO_CHOICES.contains(trimmed)) {
            return PromptResult.response(BooleanUtils.toBoolean(trimmed))
        }
        return newPrompt<Boolean>("Please enter 'yes' or 'no': ")
    }

    companion object {
        val YES_NO_CHOICES: MutableList<String> = ImmutableList.of("yes", "no")
    }
}
