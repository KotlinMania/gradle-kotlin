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

/**
 * Listener for steps in the process of printing a failure by [FailurePrinter].
 */
interface FailurePrinterListener {
    /**
     * Invoked after a failure header has been printed, and before any stack frames have been printed.
     */
    fun beforeFrames()

    /**
     * Invoked before a given stack frame is printed.
     */
    fun beforeFrame(element: StackTraceElement, relevance: StackTraceRelevance)

    /**
     * Invoked after all stack frames of a failure have been printed.
     */
    fun afterFrames()

    companion object {
        val NO_OP: FailurePrinterListener = object : FailurePrinterListener {
            override fun beforeFrames() {}

            override fun beforeFrame(element: StackTraceElement, relevance: StackTraceRelevance) {}

            override fun afterFrames() {}
        }
    }
}
