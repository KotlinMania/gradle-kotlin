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
package org.gradle.language.nativeplatform.internal.incremental.sourceparser

import java.io.IOException
import java.io.Reader

/**
 * Replaces c-style comments with a single space, and removes line-continuation characters.
 * This code is largely adopted from org.apache.tools.ant.filters.StripJavaComments.
 */
class PreprocessingReader(private val reader: Reader) {
    /**
     * The read-ahead characters, used for reading ahead up to 2 characters and pushing back into stream.
     * A value of -1 indicates that no character is in the buffer.
     */
    private val readAheadChars = IntArray(2)

    /**
     * Whether or not the parser is currently in the middle of a string literal.
     */
    private var inString = false

    /**
     * Whether or not the last char has been a backslash.
     */
    private var quoted = false

    init {
        readAheadChars[0] = -1
        readAheadChars[1] = -1
    }

    /**
     * Collects the next line from the filtered stream into the given buffer. Does not include the line separators.
     *
     * @return true if next line is available (possibly empty), false when end of stream reached.
     */
    @Throws(IOException::class)
    fun readNextLine(buffer: Appendable): Boolean {
        var ch: Int
        var read = false
        while ((read().also { ch = it }) >= 0) {
            if (ch == '\n'.code) {
                return true
            }
            if (ch == '\r'.code) {
                val next = next()
                if (next != '\n'.code) {
                    pushBack(next)
                }
                return true
            }
            buffer.append(ch.toChar())
            read = true
        }
        return read
    }

    /**
     * Returns the next character in the filtered stream:
     *
     *  * Comments will be replaced by a single space
     *  * Line continuation (backslash-newline) will be removed
     *
     */
    @Throws(IOException::class)
    private fun read(): Int {
        var ch = next()

        if (ch == '\\'.code) {
            if (discardNewLine()) {
                return read()
            }
        }

        if (ch == '"'.code && !quoted) {
            inString = !inString
            quoted = false
        } else if (ch == '\\'.code) {
            quoted = !quoted
        } else {
            quoted = false
            if (!inString) {
                if (ch == '/'.code) {
                    ch = next()
                    if (ch == '/'.code) {
                        while (ch != '\n'.code && ch != -1 && ch != '\r'.code) {
                            ch = next()
                        }
                    } else if (ch == '*'.code) {
                        while (ch != -1) {
                            ch = next()
                            if (ch == '*'.code) {
                                ch = next()
                                while (ch == '*'.code) {
                                    ch = next()
                                }

                                if (ch == '/'.code) {
                                    ch = ' '.code
                                    break
                                }
                            }
                        }
                    } else {
                        pushBack(ch)
                        ch = '/'.code
                    }
                }
            }
        }

        return ch
    }

    @Throws(IOException::class)
    private fun discardNewLine(): Boolean {
        val nextChar = next()
        if (nextChar == '\n'.code) {
            return true // '\\\n' discarded from stream
        } else if (nextChar == '\r'.code) {
            val followingChar = next()
            if (followingChar == '\n'.code) {
                return true // '\\\r\n' discarded from stream
            }
            pushBack(nextChar)
            pushBack(followingChar)
            return false
        } else {
            pushBack(nextChar)
            return false
        }
    }

    @Throws(IOException::class)
    private fun next(): Int {
        if (readAheadChars[0] != -1) {
            val ch = readAheadChars[0]
            readAheadChars[0] = readAheadChars[1]
            readAheadChars[1] = -1
            return ch
        }

        return reader.read()
    }

    private fun pushBack(ch: Int) {
        check(readAheadChars[1] == -1)
        if (readAheadChars[0] != -1) {
            readAheadChars[1] = ch
        } else {
            readAheadChars[0] = ch
        }
    }
}
