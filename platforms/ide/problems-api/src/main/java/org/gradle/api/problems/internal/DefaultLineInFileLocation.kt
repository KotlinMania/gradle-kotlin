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
package org.gradle.api.problems.internal

import com.google.common.base.Objects
import org.gradle.api.problems.FileLocation
import org.gradle.api.problems.LineInFileLocation

class DefaultLineInFileLocation private constructor(path: String, private val line: Int, private val column: Int, private val length: Int) : DefaultFileLocation(path), LineInFileLocation {
    override fun getLine(): Int {
        return line
    }

    override fun getColumn(): Int {
        return column
    }

    override fun getLength(): Int {
        return length
    }

    override fun equals(o: Any): Boolean {
        if (o !is DefaultLineInFileLocation) {
            return false
        }
        val that = o
        return line == that.line && column == that.column && length == that.length && Objects.equal(getPath(), that.getPath())
    }

    override fun hashCode(): Int {
        return Objects.hashCode(line, column, length, getPath())
    }

    companion object {
        fun from(path: String, line: Int): FileLocation {
            return DefaultLineInFileLocation(path, line, -1, -1)
        }

        fun from(path: String, line: Int, column: Int): FileLocation {
            return DefaultLineInFileLocation(path, line, column, -1)
        }

        fun from(path: String, line: Int, column: Int, length: Int): FileLocation {
            return DefaultLineInFileLocation(path, line, column, length)
        }
    }
}
