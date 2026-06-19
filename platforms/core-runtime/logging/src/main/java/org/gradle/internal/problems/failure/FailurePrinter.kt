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
package org.gradle.internal.problems.failure

import org.gradle.internal.SystemProperties
import org.gradle.internal.UncheckedException
import java.io.IOException

/**
 * Utility to print [Failure]s in the format matching that of [Throwable.printStackTrace].
 *
 *
 * Failures with multiple causes are printed similarly to [org.gradle.internal.exceptions.MultiCauseException].
 *
 *
 * The printer additionally allows reacting to each frame to be printed via a [FailurePrinterListener].
 */
object FailurePrinter {
    @JvmStatic
    fun printToString(failure: Failure): String {
        val output = StringBuilder()
        print(output, failure, FailurePrinterListener.Companion.NO_OP)
        return output.toString()
    }

    fun print(output: Appendable, failure: Failure, listener: FailurePrinterListener) {
        Job(output, listener).print(failure)
    }

    private class Job(private val builder: Appendable, private val listener: FailurePrinterListener) {
        private val lineSeparator = SystemProperties.getInstance().getLineSeparator()

        fun print(failure: Failure) {
            try {
                printRecursively("", "", null, failure)
            } catch (e: IOException) {
                throw UncheckedException.throwAsUncheckedException(e)
            }
        }

        @Throws(IOException::class)
        fun printRecursively(caption: String, prefix: String, parent: Failure?, failure: Failure) {
            builder.append(prefix)
                .append(caption)
                .append(failure.getHeader())
                .append(lineSeparator)

            listener.beforeFrames()
            appendFrames(prefix, parent, failure)
            listener.afterFrames()

            appendSuppressed(prefix, failure)
            appendCauses(prefix, failure)
        }

        @Throws(IOException::class)
        fun appendSuppressed(prefix: String, failure: Failure) {
            for (suppressed in failure.getSuppressed() ?: mutableListOf()) {
                printRecursively("Suppressed: ", prefix + "\t", failure, suppressed)
            }
        }

        @Throws(IOException::class)
        fun appendCauses(prefix: String, failure: Failure) {
            val causes = failure.getCauses() ?: mutableListOf()
            if (causes.size == 1) {
                printRecursively("Caused by: ", prefix, failure, causes.get(0))
            } else {
                for (i in causes.indices) {
                    printRecursively(String.format("Cause %s: ", i + 1), prefix, failure, causes.get(i))
                }
            }
        }

        @Throws(IOException::class)
        fun appendFrames(prefix: String, parent: Failure?, failure: Failure) {
            val stackTrace = failure.getStackTrace() ?: mutableListOf()

            val commonTailSize = if (parent == null) 0 else countCommonTailFrames(stackTrace, parent.getStackTrace() ?: mutableListOf())
            val end = stackTrace.size - commonTailSize

            for (i in 0..<end) {
                val stackTraceElement = stackTrace.get(i)
                val rel = failure.getStackTraceRelevance(i) ?: StackTraceRelevance.USER_CODE
                appendFrame(prefix, stackTraceElement, rel)
            }

            if (commonTailSize > 0) {
                builder.append(prefix)
                    .append("\t... ")
                    .append(commonTailSize.toString())
                    .append(" more")
                    .append(lineSeparator)
            }
        }

        @Throws(IOException::class)
        fun appendFrame(prefix: String, frame: StackTraceElement, relevance: StackTraceRelevance) {
            listener.beforeFrame(frame, relevance)

            builder.append(prefix)
                .append("\tat ")
                .append(frame.toString())
                .append(lineSeparator)
        }

        companion object {
            private fun countCommonTailFrames(frames1: MutableList<StackTraceElement>, frames2: MutableList<StackTraceElement>): Int {
                var j1 = frames1.size - 1
                var j2 = frames2.size - 1
                while (j1 >= 0 && j2 >= 0 && frames1.get(j1) == frames2.get(j2)) {
                    j1--
                    j2--
                }
                return frames1.size - (j1 + 1)
            }
        }
    }
}
