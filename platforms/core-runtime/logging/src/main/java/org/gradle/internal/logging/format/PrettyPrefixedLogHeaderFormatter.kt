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
package org.gradle.internal.logging.format

import org.gradle.internal.logging.events.StyledTextOutputEvent
import org.gradle.internal.logging.text.StyledTextOutput
import java.util.Arrays

class PrettyPrefixedLogHeaderFormatter : LogHeaderFormatter {
    override fun format(description: String?, status: String, failed: Boolean): MutableList<StyledTextOutputEvent.Span?> {
        if (status.isEmpty()) {
            return Arrays.asList<StyledTextOutputEvent.Span?>(header(description, failed), StyledTextOutputEvent.EOL)
        } else {
            return Arrays.asList<StyledTextOutputEvent.Span?>(header(description, failed), status(status, failed), StyledTextOutputEvent.EOL)
        }
    }

    private fun header(message: String?, failed: Boolean): StyledTextOutputEvent.Span {
        val messageStyle = if (failed) StyledTextOutput.Style.FailureHeader else StyledTextOutput.Style.Header
        return StyledTextOutputEvent.Span(messageStyle, "> " + message)
    }

    private fun status(status: String?, failed: Boolean): StyledTextOutputEvent.Span {
        val statusStyle = if (failed) StyledTextOutput.Style.Failure else StyledTextOutput.Style.Info
        return StyledTextOutputEvent.Span(statusStyle, " " + status)
    }
}
