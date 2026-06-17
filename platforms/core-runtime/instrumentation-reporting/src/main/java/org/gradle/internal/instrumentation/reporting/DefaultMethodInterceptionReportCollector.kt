/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.instrumentation.reporting

import org.gradle.internal.UncheckedException
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Collects method interception reports and prints them to the console at the end of the build.
 */
class DefaultMethodInterceptionReportCollector : MethodInterceptionReportCollector, Closeable {
    private val reports: MutableList<File>

    init {
        this.reports = ArrayList<File>()
    }

    override fun collect(report: File) {
        reports.add(report)
    }

    override fun close() {
        if (reports.stream().mapToLong { obj: File? -> obj!!.length() }.sum() > 0) {
            println("\nIntercepted methods:")
            reports.stream().flatMap<String> { report: File? ->
                try {
                    return@flatMap Files.readAllLines(report!!.toPath(), StandardCharsets.UTF_8).stream()
                } catch (e: IOException) {
                    throw UncheckedException.throwAsUncheckedException(e)
                }
            }.distinct().forEach { x: String? -> println(x) }
        }
        reports.clear()
    }
}
