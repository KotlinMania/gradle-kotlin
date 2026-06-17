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
package org.gradle.api.internal.catalog

import com.google.common.base.Splitter
import org.apache.commons.lang3.StringUtils
import java.io.IOException
import java.io.Writer
import java.util.regex.Pattern
import java.util.stream.Collectors

abstract class AbstractSourceGenerator(protected val writer: Writer) {
    private val ln: String = System.getProperty("line.separator", "\n")
    private var indent = 0

    @Throws(IOException::class)
    protected fun addImport(clazz: Class<*>) {
        addImport(clazz.getCanonicalName())
    }

    @Throws(IOException::class)
    protected fun addImport(clazz: String) {
        writeLn("import " + clazz + ";")
    }

    @Throws(IOException::class)
    protected fun writeLn() {
        writer.write(ln)
    }

    @Throws(IOException::class)
    fun writeLn(source: String) {
        writeIndent()
        writer.write(source + ln)
    }

    @Throws(IOException::class)
    protected fun writeIndent() {
        for (i in 0..<indent) {
            writer.write("    ")
        }
    }

    @Throws(IOException::class)
    fun indent(action: WriteAction) {
        try {
            indent++
            action.run()
        } finally {
            indent--
        }
    }

    internal fun interface WriteAction {
        @Throws(IOException::class)
        fun run()
    }

    companion object {
        private val SEPARATOR_PATTERN: Pattern = Pattern.compile("[.\\-_]")
        fun toJavaName(alias: String): String {
            return nameSplitter()
                .splitToList(alias)
                .stream()
                .map<String> { str: String? -> StringUtils.capitalize(str) }
                .collect(Collectors.joining())
        }

        protected fun nameSplitter(): Splitter {
            return Splitter.on(SEPARATOR_PATTERN)
        }
    }
}
