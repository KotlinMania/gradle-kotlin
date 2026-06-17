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
package org.gradle.internal.io

import java.io.IOException
import java.io.PrintStream
import java.util.Locale

class LinePerThreadBufferingOutputStream(private val handler: TextStream) : PrintStream(NullOutputStream.Companion.INSTANCE, true) {
    private val stream = ThreadLocal<PrintStream>()
    private val lineSeparator: String = System.getProperty("line.separator")

    private fun getStream(): PrintStream {
        var printStream = stream.get()
        if (printStream == null) {
            printStream = PrintStream(LineBufferingOutputStream(handler, lineSeparator))
            stream.set(printStream)
        }
        return printStream
    }

    private fun maybeGetStream(): PrintStream {
        return stream.get()
    }

    override fun append(csq: CharSequence): PrintStream {
        getStream().append(csq)
        return this
    }

    override fun append(c: Char): PrintStream {
        getStream().append(c)
        return this
    }

    override fun append(csq: CharSequence, start: Int, end: Int): PrintStream {
        getStream().append(csq, start, end)
        return this
    }

    override fun checkError(): Boolean {
        return getStream().checkError()
    }

    override fun close() {
        val currentStream = maybeGetStream()
        if (currentStream != null) {
            stream.remove()
            currentStream.close()
        } else {
            handler.endOfStream(null)
        }
    }

    override fun flush() {
        val stream = maybeGetStream()
        if (stream != null) {
            stream.flush()
        }
    }

    override fun format(format: String, vararg args: Any): PrintStream {
        getStream().format(format, *args)
        return this
    }

    override fun format(l: Locale, format: String, vararg args: Any): PrintStream {
        getStream().format(l, format, *args)
        return this
    }

    override fun print(b: Boolean) {
        getStream().print(b)
    }

    override fun print(c: Char) {
        getStream().print(c)
    }

    override fun print(d: Double) {
        getStream().print(d)
    }

    override fun print(f: Float) {
        getStream().print(f)
    }

    override fun print(i: Int) {
        getStream().print(i)
    }

    override fun print(l: Long) {
        getStream().print(l)
    }

    override fun print(obj: Any) {
        getStream().print(obj)
    }

    override fun print(s: CharArray) {
        getStream().print(s)
    }

    override fun print(s: String) {
        getStream().print(s)
    }

    override fun printf(format: String, vararg args: Any): PrintStream {
        getStream().printf(format, *args)
        return this
    }

    override fun printf(l: Locale, format: String, vararg args: Any): PrintStream {
        getStream().printf(l, format, *args)
        return this
    }

    override fun println() {
        getStream().print(lineSeparator)
    }

    override fun println(x: Boolean) {
        val stream = getStream()
        synchronized(this) {
            stream.print(x)
            stream.print(lineSeparator)
        }
    }

    override fun println(x: Char) {
        val stream = getStream()
        synchronized(this) {
            stream.print(x)
            stream.print(lineSeparator)
        }
    }

    override fun println(x: CharArray) {
        val stream = getStream()
        synchronized(this) {
            stream.print(x)
            stream.print(lineSeparator)
        }
    }

    override fun println(x: Double) {
        val stream = getStream()
        synchronized(this) {
            stream.print(x)
            stream.print(lineSeparator)
        }
    }

    override fun println(x: Float) {
        val stream = getStream()
        synchronized(this) {
            stream.print(x)
            stream.print(lineSeparator)
        }
    }

    override fun println(x: Int) {
        val stream = getStream()
        synchronized(this) {
            stream.print(x)
            stream.print(lineSeparator)
        }
    }

    override fun println(x: Long) {
        val stream = getStream()
        synchronized(this) {
            stream.print(x)
            stream.print(lineSeparator)
        }
    }

    override fun println(x: Any) {
        val stream = getStream()
        synchronized(this) {
            stream.print(x)
            stream.print(lineSeparator)
        }
    }

    override fun println(x: String) {
        val stream = getStream()
        synchronized(this) {
            stream.print(x)
            stream.print(lineSeparator)
        }
    }

    override fun write(b: Int) {
        getStream().write(b)
    }

    override fun write(buf: ByteArray, off: Int, len: Int) {
        getStream().write(buf, off, len)
    }

    @Throws(IOException::class)
    override fun write(b: ByteArray) {
        getStream().write(b)
    }
}
