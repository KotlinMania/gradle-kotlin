/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.docs.asciidoctor

import java.util.regex.Pattern

/**
 * Generates valid Asciidoctor identifiers from strings.
 * Tries to mimic original Asciidoctor behaviour.
 *
 * @see [How a section ID is computed](https://docs.asciidoctor.org/asciidoc/latest/sections/auto-ids/.how-a-section-id-is-computed)
 */
object IdGenerator {
    // Matches invalid ID characters in a section title. (taken from https://www.rubydoc.info/gems/asciidoctor/Asciidoctor)
    private val ID_PATTERN: Pattern = Pattern.compile("<[^>]+>|&(?:[a-z][a-z]+\\d{0,2}|#\\d\\d\\d{0,4}|#x[\\da-f][\\da-f][\\da-f]{0,3});|[^ a-zA-Z0-9_\\-.]+?")
    private val SEPARATOR_PATTERN: Pattern = Pattern.compile("[ _.-]+")

    private const val PART_SEPARATOR = "-"

    @JvmStatic
    fun generateId(source: String): String {
        var result = source.lowercase()

        // replace invalid characters
        result = ID_PATTERN.matcher(result).replaceAll("")

        // normalize separators
        result = SEPARATOR_PATTERN.matcher(result).replaceAll(PART_SEPARATOR)

        // strip separator from the end
        if (result.endsWith(PART_SEPARATOR)) {
            result = result.substring(0, result.length - 1)
        }

        // string separator from the start
        if (result.startsWith(PART_SEPARATOR)) {
            result = result.substring(1)
        }
        return result
    }
}
