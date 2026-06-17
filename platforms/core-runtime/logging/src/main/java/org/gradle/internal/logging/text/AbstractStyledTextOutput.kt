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

import com.google.errorprone.annotations.FormatMethod
import org.gradle.api.logging.StandardOutputListener
import org.gradle.internal.SystemProperties
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.problems.failure.FailurePrinter
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Subclasses need to implement [.doAppend], and optionally [.doStyleChange].
 */
abstract class AbstractStyledTextOutput : StyledTextOutput, StandardOutputListener {
    var style: StyledTextOutput.Style? = StyledTextOutput.Style.Normal
        private set

    override fun append(c: Char): StyledTextOutput {
        text(c.toString())
        return this
    }

    override fun append(csq: CharSequence?): StyledTextOutput {
        text(if (csq == null) "null" else csq)
        return this
    }

    override fun append(csq: CharSequence?, start: Int, end: Int): StyledTextOutput {
        text(if (csq == null) "null" else csq.subSequence(start, end))
        return this
    }

    @FormatMethod
    override fun format(pattern: String, vararg args: Any?): StyledTextOutput {
        text(String.format(pattern, *args))
        return this
    }

    override fun println(text: Any?): StyledTextOutput {
        text(text)
        println()
        return this
    }

    @FormatMethod
    override fun formatln(pattern: String, vararg args: Any?): StyledTextOutput {
        format(pattern, *args)
        println()
        return this
    }

    override fun onOutput(output: CharSequence?) {
        text(output)
    }

    override fun println(): StyledTextOutput {
        text(SystemProperties.getInstance().getLineSeparator())
        return this
    }

    override fun text(text: Any?): StyledTextOutput {
        doAppend(if (text == null) "null" else text.toString())
        return this
    }

    override fun exception(throwable: Throwable): StyledTextOutput {
        val out = StringWriter()
        val writer = PrintWriter(out)
        throwable.printStackTrace(writer)
        writer.close()
        text(out.toString())
        return this
    }

    /**
     * Appends the stacktrace of the given failure using the current style.
     *
     * @return this
     */
    fun failure(failure: Failure): StyledTextOutput {
        text(FailurePrinter.printToString(failure))
        return this
    }

    override fun withStyle(style: StyledTextOutput.Style?): StyledTextOutput {
        return StyleOverrideTextOutput(style, this)
    }

    override fun style(style: StyledTextOutput.Style?): StyledTextOutput {
        if (style != this.style) {
            this.style = style
            doStyleChange(style)
        }
        return this
    }

    protected abstract fun doAppend(text: String?)

    protected open fun doStyleChange(style: StyledTextOutput.Style?) {
    }

    private class StyleOverrideTextOutput(private val style: StyledTextOutput.Style?, private val textOutput: AbstractStyledTextOutput) : StyledTextOutput {
        override fun append(c: Char): StyledTextOutput {
            val original = textOutput.style
            textOutput.style(style).append(c).style(original)
            return this
        }

        override fun append(csq: CharSequence?): StyledTextOutput {
            val original = textOutput.style
            textOutput.style(style).append(csq).style(original)
            return this
        }

        override fun append(csq: CharSequence?, start: Int, end: Int): StyledTextOutput? {
            throw UnsupportedOperationException()
        }

        override fun style(style: StyledTextOutput.Style?): StyledTextOutput? {
            throw UnsupportedOperationException()
        }

        override fun withStyle(style: StyledTextOutput.Style?): StyledTextOutput? {
            throw UnsupportedOperationException()
        }

        override fun text(text: Any?): StyledTextOutput {
            val original = textOutput.style
            textOutput.style(style).text(text).style(original)
            return this
        }

        override fun println(text: Any?): StyledTextOutput {
            val original = textOutput.style
            textOutput.style(style).text(text).style(original).println()
            return this
        }

        override fun format(pattern: String?, vararg args: Any?): StyledTextOutput {
            val original = textOutput.style
            textOutput.style(style).format(pattern, *args).style(original)
            return this
        }

        override fun formatln(pattern: String?, vararg args: Any?): StyledTextOutput? {
            throw UnsupportedOperationException()
        }

        override fun println(): StyledTextOutput? {
            throw UnsupportedOperationException()
        }

        override fun exception(throwable: Throwable?): StyledTextOutput? {
            throw UnsupportedOperationException()
        }
    }
}
