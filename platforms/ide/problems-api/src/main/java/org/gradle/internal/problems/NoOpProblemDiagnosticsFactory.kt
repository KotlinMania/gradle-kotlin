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

import com.google.common.base.Supplier
import org.gradle.internal.code.UserCodeSource
import org.gradle.internal.problems.failure.Failure
import org.gradle.problems.Location
import org.gradle.problems.ProblemDiagnostics
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory
import org.gradle.problems.buildtree.ProblemStream

class NoOpProblemDiagnosticsFactory : ProblemDiagnosticsFactory {
    override fun newStream(): ProblemStream {
        return EMPTY_STREAM
    }

    override fun newUnlimitedStream(): ProblemStream {
        return EMPTY_STREAM
    }

    override fun forException(exception: Throwable): ProblemDiagnostics {
        return EMPTY_DIAGNOSTICS
    }

    companion object {
        val EMPTY_DIAGNOSTICS: ProblemDiagnostics = object : ProblemDiagnostics {
            override fun getFailure(): Failure? {
                return null
            }

            override fun getException(): Throwable? {
                return null
            }

            override fun getStack(): MutableList<StackTraceElement> {
                return mutableListOf<StackTraceElement>()
            }

            override fun getLocation(): Location? {
                return null
            }

            override fun getSource(): UserCodeSource? {
                return null
            }
        }

        val EMPTY_STREAM: ProblemStream = object : ProblemStream {
            override fun forCurrentCaller(transformer: ProblemStream.StackTraceTransformer): ProblemDiagnostics {
                return EMPTY_DIAGNOSTICS
            }

            override fun forCurrentCaller(exception: Throwable?): ProblemDiagnostics {
                return EMPTY_DIAGNOSTICS
            }

            override fun forCurrentCaller(): ProblemDiagnostics {
                return EMPTY_DIAGNOSTICS
            }

            override fun forCurrentCaller(exceptionFactory: Supplier<out Throwable>): ProblemDiagnostics {
                return EMPTY_DIAGNOSTICS
            }
        }
    }
}
