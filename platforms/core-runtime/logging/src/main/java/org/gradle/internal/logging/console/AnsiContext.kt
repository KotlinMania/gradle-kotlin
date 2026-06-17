/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.logging.console

import org.gradle.api.Action
import org.gradle.internal.logging.text.Style
import org.gradle.internal.logging.text.StyledTextOutput

interface AnsiContext {
    /**
     * Change the ANSI color before executing the specified action. The color is reverted back after the action is executed.
     *
     * @param color the color to use
     * @param action the action to execute on ANSI with the specified color
     * @return the current context
     */
    fun withColor(color: ColorMap.Color, action: Action<in AnsiContext>): AnsiContext?

    /**
     * Change the ANSI style before executing the specified action. The style is reverted back after the action is executed.
     *
     * @param style the style to use
     * @param action the action to execute on ANSI with the specified style
     * @return the current context
     */
    fun withStyle(style: Style, action: Action<in AnsiContext>): AnsiContext?

    /**
     * Change the ANSI style before executing the specified action. The style is reverted back after the action is executed.
     *
     * @param style the style to use
     * @param action the action to execute on ANSI with the specified style
     * @return the current context
     */
    fun withStyle(style: StyledTextOutput.Style, action: Action<in AnsiContext>): AnsiContext?

    /**
     * @return the current context with the specified text written.
     */
    fun a(value: CharSequence): AnsiContext?

    /**
     * @return the current context with a new line written.
     */
    fun newLine(): AnsiContext?

    /**
     * @return the current context with the specified new line written.
     */
    fun newLines(numberOfNewLines: Int): AnsiContext?

    /**
     * @return the current context with the characters moving forward from the write position erased.
     */
    fun eraseForward(): AnsiContext?

    /**
     * @return the current context with the entire line erased.
     */
    fun eraseAll(): AnsiContext?

    /**
     * @return the current context moved to the specified position.
     */
    fun cursorAt(cursor: Cursor): AnsiContext?

    /**
     * @return a new context at the specified write position.
     */
    fun writeAt(writePos: Cursor): AnsiContext?
}
