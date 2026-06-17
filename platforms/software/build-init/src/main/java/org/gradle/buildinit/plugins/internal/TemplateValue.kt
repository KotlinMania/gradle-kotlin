/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.buildinit.plugins.internal

import org.gradle.util.internal.TextUtil

class TemplateValue(val raw: String) {
    val groovyComment: String
        get() = raw.replace("\\", "\\\\")

    val groovyString: String
        get() {
            val result = StringBuilder()
            for (i in 0..<raw.length) {
                val ch = raw.get(i)
                when (ch) {
                    '\\' -> result.append('\\').append('\\')
                    '\'' -> result.append('\\').append('\'')
                    '\n' -> result.append('\\').append('n')
                    '\r' -> result.append('\\').append('r')
                    '\t' -> result.append('\\').append('t')
                    '\f' -> result.append('\\').append('f')
                    '\b' -> result.append('\\').append('b')
                    else -> result.append(ch)
                }
            }
            return result.toString()
        }

    val statement: String
        get() {
            if (raw.isEmpty()) {
                return ""
            } else {
                return this.raw + TextUtil.getPlatformLineSeparator()
            }
        }

    val javaStatement: String
        get() {
            if (raw.isEmpty()) {
                return ""
            } else {
                return this.raw + ";" + TextUtil.getPlatformLineSeparator()
            }
        }

    val multilineComment: String
        get() {
            if (raw.isEmpty()) {
                return ""
            }

            val sb = StringBuilder()
            sb.append("/*").append(TextUtil.getPlatformLineSeparator())
            for (line in raw.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                sb.append(" *")
                if (!line.isEmpty()) {
                    sb.append(" ").append(line)
                }
                sb.append(TextUtil.getPlatformLineSeparator())
            }
            sb.append(" */").append(TextUtil.getPlatformLineSeparator())
            return sb.toString()
        }

    override fun toString(): String {
        return ">>>" + this.raw + "<<<"
    }
}
