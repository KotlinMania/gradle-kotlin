/*
 * Copyright 2025 Gradle and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.docs.asciidoctor

import org.apache.commons.io.IOUtils
import org.asciidoctor.ast.Document
import org.asciidoctor.extension.Postprocessor
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * This processor adds the ability to copy code blocks to the clipboard
 */
class ClipboardPostprocessor : Postprocessor() {
    override fun process(document: Document, output: String): String {
        var output = output
        if (!document.isBasebackend("html")) {
            return output
        }

        val codeBlockPattern = Pattern.compile("</code>", Pattern.DOTALL)
        val matcher = codeBlockPattern.matcher(output)
        if (!matcher.find()) {
            return output // No <code> block found
        }

        output = addExternalJs(output, "https://cdnjs.cloudflare.com/ajax/libs/clipboard.js/2.0.11/clipboard.min.js")
        output = addJs(output, "/clipboard.js")
        output = addCss(output, "/clipboard.css")

        return output
    }

    companion object {
        private fun readResource(resourcePath: String): String? {
            try {
                ClipboardPostprocessor::class.java.getResourceAsStream(resourcePath).use { inputStream ->
                    return IOUtils.toString(inputStream, StandardCharsets.UTF_8)
                }
            } catch (e: IOException) {
                throw IllegalStateException("Unable to read source resource for Clipboard: " + e.message)
            }
        }

        private fun addCss(output: String, resource: String): String {
            val css: String? = readResource(resource)
            val replacement = StringBuffer()
                .append("<style>").append(css).append("</style>")
                .append("</head>")
                .toString()
            return output.replace("</head>", replacement)
        }

        private fun addJs(output: String, resource: String): String {
            val javascript: String? = readResource(resource)
            val replacement = StringBuffer()
                .append("<script type='text/javascript'>").append(javascript).append("</script>")
                .append("</html>")
                .toString()
            return output.replace("</html>", replacement)
        }

        private fun addExternalJs(output: String, url: String?): String {
            val replacement = StringBuffer()
                .append("</body>")
                .append("<script type='text/javascript' src='").append(url).append("'></script>")
                .toString()
            return output.replace("</body>", replacement)
        }
    }
}

