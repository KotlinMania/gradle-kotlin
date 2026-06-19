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
package org.gradle.internal.problems

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Supplier
import com.google.common.collect.ImmutableList
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.code.UserCodeSource
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.problems.failure.FailureFactory
import org.gradle.problems.Location
import org.gradle.problems.ProblemDiagnostics
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory
import org.gradle.problems.buildtree.ProblemStream
import org.jspecify.annotations.NullMarked
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class DefaultProblemDiagnosticsFactory @VisibleForTesting internal constructor(
    private val failureFactory: FailureFactory,
    private val locationAnalyzer: ProblemLocationAnalyzer,
    private val userCodeContext: UserCodeApplicationContext,
    private val maxStackTraces: Int
) : ProblemDiagnosticsFactory {
    private class CopyStackTraceTransFormer : ProblemStream.StackTraceTransformer {
        override fun transform(original: Array<StackTraceElement>): MutableList<StackTraceElement> {
            return ImmutableList.copyOf<StackTraceElement>(original)
        }
    }

    @Inject
    constructor(
        failureFactory: FailureFactory,
        locationAnalyzer: ProblemLocationAnalyzer,
        userCodeContext: UserCodeApplicationContext,
        buildModelParameters: BuildModelParameters
    ) : this(failureFactory, locationAnalyzer, userCodeContext, getMaxStackTraces(buildModelParameters))

    override fun newStream(): ProblemStream {
        return DefaultProblemStream()
    }

    override fun newUnlimitedStream(): ProblemStream {
        val defaultProblemStream: DefaultProblemStream = DefaultProblemStream()
        defaultProblemStream.remainingStackTraces.set(Int.MAX_VALUE)
        return defaultProblemStream
    }

    override fun forException(exception: Throwable): ProblemDiagnostics {
        return locationFromStackTrace(exception, true, true, NO_OP)
    }

    private fun locationFromStackTrace(throwable: Throwable?, fromException: Boolean, keepException: Boolean, transformer: ProblemStream.StackTraceTransformer): ProblemDiagnostics {
        val applicationContext = userCodeContext.current()

        if (applicationContext == null && throwable == null) {
            return NoOpProblemDiagnosticsFactory.Companion.EMPTY_DIAGNOSTICS
        }

        var stackTrace = mutableListOf<StackTraceElement>()
        var stackTracingFailure: Failure? = null
        var location: Location? = null
        if (throwable != null) {
            stackTrace = transformer.transform(throwable.getStackTrace())!!
            stackTracingFailure = failureFactory.create(throwable)
            location = locationAnalyzer.locationForUsage(stackTracingFailure!!, fromException)
        }

        val source = if (applicationContext != null) applicationContext.getSource() else null
        return DefaultProblemDiagnostics(stackTracingFailure, if (keepException) throwable else null, stackTrace, location, source)
    }

    @NullMarked
    private inner class DefaultProblemStream : ProblemStream {
        val remainingStackTraces = AtomicInteger()

        init {
            remainingStackTraces.set(maxStackTraces)
        }

        override fun forCurrentCaller(exception: Throwable?): ProblemDiagnostics {
            if (exception == null) {
                return locationFromStackTrace(getImplicitThrowable(EXCEPTION_FACTORY), false, false, NO_OP)
            } else {
                return locationFromStackTrace(exception, true, true, NO_OP)
            }
        }

        override fun forCurrentCaller(): ProblemDiagnostics {
            return locationFromStackTrace(getImplicitThrowable(EXCEPTION_FACTORY), false, false, NO_OP)
        }

        override fun forCurrentCaller(exceptionFactory: Supplier<out Throwable>): ProblemDiagnostics {
            return locationFromStackTrace(getImplicitThrowable(exceptionFactory), false, true, NO_OP)
        }

        override fun forCurrentCaller(transformer: ProblemStream.StackTraceTransformer): ProblemDiagnostics {
            return locationFromStackTrace(getImplicitThrowable(EXCEPTION_FACTORY), false, false, transformer)
        }

        fun getImplicitThrowable(factory: Supplier<out Throwable>): Throwable? {
            if (remainingStackTraces.getAndDecrement() > 0) {
                return factory.get()
            } else {
                return null
            }
        }
    }

    private class DefaultProblemDiagnostics(
        private val failure: Failure?,
        private val exception: Throwable?,
        private val stackTrace: MutableList<StackTraceElement>,
        private val location: Location?,
        private val source: UserCodeSource?
    ) : ProblemDiagnostics {
        override fun getFailure(): Failure? {
            return failure
        }

        override fun getException(): Throwable? {
            return exception
        }

        override fun getStack(): MutableList<StackTraceElement> {
            return stackTrace
        }

        override fun getLocation(): Location? {
            return location
        }

        override fun getSource(): UserCodeSource? {
            return source
        }
    }

    companion object {
        private val NO_OP: ProblemStream.StackTraceTransformer = CopyStackTraceTransFormer()

        private val EXCEPTION_FACTORY: Supplier<Throwable> = object : Supplier<Throwable> {
            override fun get(): Throwable {
                return Exception()
            }
        }

        private const val MAX_STACKTRACE_COUNT = 50
        private const val ISOLATED_PROJECTS_MAX_STACKTRACE_COUNT = 5000

        private fun getMaxStackTraces(buildModelParameters: BuildModelParameters): Int {
            return if (buildModelParameters.isIsolatedProjects()) ISOLATED_PROJECTS_MAX_STACKTRACE_COUNT else MAX_STACKTRACE_COUNT
        }
    }
}
