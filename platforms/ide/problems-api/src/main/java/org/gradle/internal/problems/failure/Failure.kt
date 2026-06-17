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
 * Content of a thrown exception with classified stack frames.
 *
 *
 * Failures can have multiple causes via the [org.gradle.internal.exceptions.MultiCauseException].
 *
 *
 * Failures are guaranteed to not have circular references.
 *
 * @see FailureFactory
 */
interface Failure {
    @JvmField
    val exceptionType: Class<out Throwable>?

    /**
     * The original exception.
     */
    @JvmField
    val original: Throwable?

    /**
     * A failure summary usually containing the type of the original exception and its message.
     *
     * @see Throwable.toString
     */
    val header: String?

    /**
     * The message of the original exception.
     */
    @JvmField
    val message: String?

    /**
     * Stack frames from the original exception.
     */
    @JvmField
    val stackTrace: MutableList<StackTraceElement>?

    /**
     * Relevance of a given stack frame in the [stack trace][.getStackTrace].
     */
    fun getStackTraceRelevance(frameIndex: Int): StackTraceRelevance?

    /**
     * Failures suppressed in the original exception.
     */
    val suppressed: MutableList<Failure>?

    /**
     * List of causes for this failure.
     *
     *
     * There could be more than one cause if the failure was derived from a [org.gradle.internal.exceptions.MultiCauseException].
     */
    @JvmField
    val causes: MutableList<Failure>?

    /**
     * Returns the index of the first matching frame in the stack trace, or `-1` if not found.
     */
    fun indexOfStackFrame(start: Int, predicate: StackFramePredicate): Int

    /**
     * The problems associated with the failure.
     */
    @JvmField
    val problems: MutableList<ProblemInternal>?
}
