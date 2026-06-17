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

class TextQuestionPromptEvent(timestamp: Long, @JvmField val question: String?, @JvmField val defaultValue: String?) : PromptOutputEvent(timestamp) {
    val prompt: String?
        get() {
            val builder = StringBuilder()
            builder.append(question)
            builder.append(" (default: ")
            builder.append(defaultValue)
            builder.append("): ")
            return builder.toString()
        }

    public override fun convert(text: String): PromptResult<String?> {
        if (text.isEmpty()) {
            return PromptResult.response(defaultValue)
        }
        return PromptResult.response(text)
    }
}
