/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.internal.Factory
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.collect.PersistentSet
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Arrays
import java.util.stream.Collectors

class LoggingExcludeFactory internal constructor(delegate: ExcludeFactory?) : DelegatingExcludeFactory(delegate) {
    private val subject: Subject

    init {
        this.subject = computeWhatToLog()
    }

    override fun anyOf(one: ExcludeSpec, two: ExcludeSpec?): ExcludeSpec? {
        return log("anyOf", org.gradle.internal.Factory { super.anyOf(one, two) }, one, two)
    }

    override fun allOf(one: ExcludeSpec, two: ExcludeSpec?): ExcludeSpec? {
        return log("allOf", org.gradle.internal.Factory { super.allOf(one, two) }, one, two)
    }

    override fun anyOf(specs: PersistentSet<ExcludeSpec?>): ExcludeSpec? {
        return log("anyOf", org.gradle.internal.Factory { super.anyOf(specs) }, specs)
    }

    override fun allOf(specs: PersistentSet<ExcludeSpec?>): ExcludeSpec? {
        return log("allOf", org.gradle.internal.Factory { super.allOf(specs) }, specs)
    }

    private fun log(operationName: String?, factory: Factory<ExcludeSpec?>, vararg operands: Any?): ExcludeSpec? {
        val spec: ExcludeSpec?
        try {
            spec = factory.create()
        } catch (e: StackOverflowError) {
            if (subject.isTraceStackOverflows) {
                val sw = StringWriter()
                sw.append("{\"stackoverflow\": [")
                val printWriter = PrintWriter(sw)
                val stackTrace = e.getStackTrace()
                printWriter.print(
                    Arrays.stream<StackTraceElement?>(stackTrace)
                        .limit(100)
                        .map<String?> { d: StackTraceElement? -> "\"" + d.toString() + "\"" }
                        .collect(Collectors.joining(", "))
                )
                sw.append("]}")
                LOGGER.debug("{\"operation\": { \"name\": \"{}\", \"operands\": {}, \"result\": {} } }", operationName, Companion.toList(operands), sw.toString())
            }
            throw throwAsUncheckedException(e)
        }
        if (subject.isTraceOperations) {
            LOGGER.debug("{\"operation\": { \"name\": \"{}\", \"operands\": {}, \"result\": {} } }", operationName, Companion.toList(operands), spec)
        }
        return spec
    }

    private enum class Subject(val isTraceOperations: Boolean, val isTraceStackOverflows: Boolean) {
        all(true, true),
        stackoverflow(false, true),
        operations(true, false)
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(LoggingExcludeFactory::class.java)

        private fun computeWhatToLog(): Subject {
            val subjectString = System.getProperty("org.gradle.internal.dm.trace.excludes", Subject.all.toString())
            return Subject.valueOf(subjectString.lowercase())
        }

        fun maybeLog(factory: ExcludeFactory?): ExcludeFactory? {
            if (LOGGER.isDebugEnabled()) {
                return LoggingExcludeFactory(factory)
            }
            return factory
        }

        private fun toList(operands: Array<Any?>): MutableCollection<*>? {
            return if (singleCollection(operands)) operands[0] as MutableCollection<*>? else Arrays.asList<Any?>(*operands)
        }

        private fun singleCollection(operands: Array<Any?>): Boolean {
            return operands.size == 1 && operands[0] is MutableCollection<*>
        }
    }
}
