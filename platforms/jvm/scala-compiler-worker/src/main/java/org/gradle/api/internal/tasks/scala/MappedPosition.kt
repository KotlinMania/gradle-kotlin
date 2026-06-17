/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.tasks.scala

import xsbti.Position
import java.io.File
import java.util.Optional

class MappedPosition(private val delegate: Position) : Position {
    override fun line(): Optional<Int?> {
        return delegate.line()
    }

    override fun lineContent(): String? {
        return delegate.lineContent()
    }

    override fun offset(): Optional<Int?>? {
        return delegate.offset()
    }

    override fun pointer(): Optional<Int?> {
        return delegate.pointer()
    }

    override fun pointerSpace(): Optional<String?>? {
        return delegate.pointerSpace()
    }

    override fun sourcePath(): Optional<String> {
        return delegate.sourcePath()
    }

    override fun sourceFile(): Optional<File?>? {
        return delegate.sourceFile()
    }

    override fun startOffset(): Optional<Int?>? {
        return delegate.startOffset()
    }

    override fun endOffset(): Optional<Int?>? {
        return delegate.endOffset()
    }

    override fun startLine(): Optional<Int?>? {
        return delegate.startLine()
    }

    override fun startColumn(): Optional<Int?>? {
        return delegate.startColumn()
    }

    override fun endLine(): Optional<Int?>? {
        return delegate.endLine()
    }

    override fun endColumn(): Optional<Int?>? {
        return delegate.endColumn()
    }

    override fun toString(): String {
        val sourcePath = this.sourcePath()
        val line = this.line()
        val column = this.pointer()

        if (sourcePath.isPresent() && line.isPresent() && column.isPresent()) {
            return sourcePath.get() + ":" + line.get() + ":" + (column.get() + 1)
        } else if (sourcePath.isPresent() && line.isPresent()) {
            return sourcePath.get() + ":" + line.get()
        } else {
            return sourcePath.orElse("")
        }
    }
}
