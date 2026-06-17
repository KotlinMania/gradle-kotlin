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
package org.gradle.problems.buildtree

import com.google.common.base.Supplier
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.problems.ProblemDiagnostics

@ServiceScope(Scope.BuildTree::class)
interface ProblemStream {
    /**
     * Returns diagnostics based on the state of the calling thread.
     *
     *
     * This method is here because stack trace sanitizing is currently performed by the caller.
     * However, each caller does this in a different way and they all do this in a different way
     * to the services used by this type.
     *
     *
     *
     *
     * Stack trace sanitization should be handled by this service and this method removed.
     *
     *
     * @param transformer A transformer to use to sanitize the stack trace.
     */
    fun forCurrentCaller(transformer: StackTraceTransformer): ProblemDiagnostics?

    /**
     * Returns diagnostics based on the state of the calling thread.
     *
     * @param exception The exception that represents the failure.
     */
    fun forCurrentCaller(exception: Throwable?): ProblemDiagnostics?

    /**
     * Returns diagnostics based on the state of the calling thread.
     */
    fun forCurrentCaller(): ProblemDiagnostics?

    /**
     * Returns diagnostics based on the state of the calling thread.
     *
     * @param exceptionFactory The factory to use to produce an exception when a stack trace is required.
     */
    fun forCurrentCaller(exceptionFactory: Supplier<out Throwable>): ProblemDiagnostics?

    interface StackTraceTransformer {
        fun transform(original: Array<StackTraceElement>): MutableList<StackTraceElement>?
    }
}
