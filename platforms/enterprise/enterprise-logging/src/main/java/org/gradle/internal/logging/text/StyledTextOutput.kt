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
 * Provides streaming of styled text, that is, a stream of text with inline styling information. Implementations are not
 * required to be thread-safe.
 */
interface StyledTextOutput : Appendable {
    enum class Style {
        /**
         * Regular text.
         */
        Normal,

        /**
         * A header.
         */
        Header,

        /**
         * User input
         */
        UserInput,

        /**
         * An identifier for something
         */
        Identifier,

        /**
         * The description of something
         */
        Description,

        /**
         * Operation progress status
         */
        ProgressStatus,

        /**
         * General purpose success message
         */
        Success,

        /**
         * **Emphasized** success message
         */
        SuccessHeader,

        /**
         * General purpose failure message
         */
        Failure,

        /**
         * **Emphasized** failure message
         */
        FailureHeader,

        /**
         * General purpose informational text
         */
        Info,

        /**
         * General purpose error text
         */
        Error
    }

    /**
     * Appends a character using the current style.
     *
     * @param c The character
     * @return this
     */
    override fun append(c: Char): StyledTextOutput

    /**
     * Appends a sequence of characters using the current style.
     *
     * @param csq The character sequence
     * @return this.
     */
    override fun append(csq: CharSequence?): StyledTextOutput

    /**
     * Appends a sequence of characters using the current style.
     *
     * @param csq The character sequence
     * @return this.
     */
    override fun append(csq: CharSequence?, start: Int, end: Int): StyledTextOutput

    /**
     * Switches to a new style. The default style is [Style.Normal].
     *
     * @param style The style.
     * @return this
     */
    fun style(style: Style?): StyledTextOutput

    /**
     * Creates a copy of this output which uses the given style. This can be used to generate text in a different style
     * and then return to the current style. For example:
     * <pre>
     * output.style(Info)
     * output.withStyle(Description).format("%s %s", name, description) // output in Description style
     * output.println(" text") // output in Info style
    </pre> *
     *
     * @param style The temporary style
     * @return the copy
     */
    fun withStyle(style: Style?): StyledTextOutput

    /**
     * Appends text using the current style.
     *
     * @param text The text
     * @return this
     */
    fun text(text: Any?): StyledTextOutput

    /**
     * Appends text using the current style and starts a new line.
     *
     * @param text The text
     * @return this
     */
    fun println(text: Any?): StyledTextOutput

    /**
     * Appends a formatted string using the current style.
     *
     * @param pattern The pattern string
     * @param args    The args for the pattern
     * @return this
     */
    fun format(pattern: String, vararg args: Any?): StyledTextOutput

    /**
     * Appends a formatted string using the current style and starts a new line.
     *
     * @param pattern The pattern string
     * @param args    The args for the pattern
     * @return this
     */
    fun formatln(pattern: String, vararg args: Any?): StyledTextOutput

    /**
     * Starts a new line.
     *
     * @return this
     */
    fun println(): StyledTextOutput

    /**
     * Appends the stacktrace of the given exception using the current style.
     *
     * @param throwable The exception
     * @return this
     */
    fun exception(throwable: Throwable): StyledTextOutput
}
