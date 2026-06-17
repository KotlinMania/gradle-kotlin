/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.logging.text

/**
 * A [StyledTextOutput] which prefixes each line of text with some fixed prefix. Does not prefix the first line.
 */
class LinePrefixingStyledTextOutput @JvmOverloads constructor(private val output: StyledTextOutput, private val prefix: CharSequence?, private val prefixFirstLine: Boolean = true) :
    AbstractLineChoppingStyledTextOutput() {
    private var prefixed = false

    override fun doLineText(text: CharSequence?) {
        if (!prefixed && prefixFirstLine) {
            output.text(prefix)
            prefixed = true
        }
        output.text(text)
    }

    override fun doEndLine(endOfLine: CharSequence?) {
        output.text(endOfLine)
    }

    override fun doStartLine() {
        output.text(prefix)
    }

    override fun doStyleChange(style: StyledTextOutput.Style?) {
        output.style(style)
    }
}
