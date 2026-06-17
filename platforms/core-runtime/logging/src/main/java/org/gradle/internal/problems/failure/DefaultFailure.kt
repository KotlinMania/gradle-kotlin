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

import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import org.gradle.api.problems.internal.ProblemInternal

internal class DefaultFailure(
    original: Throwable,
    stackTrace: MutableList<StackTraceElement>,
    frameRelevance: MutableList<StackTraceRelevance>,
    suppressed: MutableList<Failure>,
    causes: MutableList<Failure>,
    problems: MutableList<ProblemInternal>
) : Failure {
    private val original: Throwable
    private val stackTrace: MutableList<StackTraceElement>
    private val frameRelevance: MutableList<StackTraceRelevance>
    private val suppressed: MutableList<Failure>
    private val causes: MutableList<Failure>
    private val problems: MutableList<ProblemInternal>

    init {
        require(stackTrace.size == frameRelevance.size) { "stackTrace and frameRelevance must have the same size." }

        this.original = original
        this.stackTrace = ImmutableList.copyOf<StackTraceElement>(stackTrace)
        this.frameRelevance = ImmutableList.copyOf<StackTraceRelevance>(frameRelevance)
        this.suppressed = ImmutableList.copyOf<Failure>(suppressed)
        this.causes = ImmutableList.copyOf<Failure>(causes)
        this.problems = ImmutableList.copyOf<ProblemInternal>(problems)
    }

    override fun getExceptionType(): Class<out Throwable> {
        return original.javaClass
    }

    override fun getHeader(): String {
        return original.toString()
    }

    override fun getMessage(): String {
        // TODO: Remove this when we handle problems uniformly and don't do the compilation failure as a special case
        return (if (original is org.gradle.internal.exceptions.CompilationFailedIndicator) (original as org.gradle.internal.exceptions.CompilationFailedIndicator).getShortMessage() else original.message)!!
    }

    override fun getStackTrace(): MutableList<StackTraceElement> {
        return stackTrace
    }

    override fun getStackTraceRelevance(frameIndex: Int): StackTraceRelevance {
        return frameRelevance.get(frameIndex)
    }

    override fun getSuppressed(): MutableList<Failure> {
        return suppressed
    }

    override fun getCauses(): MutableList<Failure> {
        return causes
    }

    override fun getProblems(): MutableList<ProblemInternal> {
        return problems
    }

    override fun getOriginal(): Throwable {
        return original
    }

    override fun indexOfStackFrame(fromIndex: Int, predicate: StackFramePredicate): Int {
        val size = stackTrace.size
        for (i in fromIndex..<size) {
            if (predicate.test(stackTrace.get(i), getStackTraceRelevance(i))) {
                return i
            }
        }
        return -1
    }

    override fun toString(): String {
        return "DefaultFailure{" +
                "original=" + original +
                ", stackTrace=" + stackTrace +
                ", frameRelevance=" + frameRelevance +
                ", suppressed=" + suppressed +
                ", causes=" + causes +
                ", problems=" + problems +
                '}'
    }

    override fun equals(o: Any): Boolean {
        if (o !is DefaultFailure) {
            return false
        }
        val that = o
        return Objects.equal(original, that.original) &&
                Objects.equal(stackTrace, that.stackTrace) &&
                Objects.equal(frameRelevance, that.frameRelevance) &&
                Objects.equal(suppressed, that.suppressed) &&
                Objects.equal(causes, that.causes) &&
                Objects.equal(problems, that.problems)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(original, stackTrace, frameRelevance, suppressed, causes, problems)
    }
}
