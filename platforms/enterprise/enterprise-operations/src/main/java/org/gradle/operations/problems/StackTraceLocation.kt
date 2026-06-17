/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.operations.problems

/**
 * A stack trace for the location of a problem.
 *
 *
 * For example, the stacktrace of a direct deprecation warning.
 *
 * @since 8.14
 */
interface StackTraceLocation : ProblemLocation {
    /**
     * The source file location inferred from the stacktrace.
     *
     *
     * Most of the time, this is a location in a build file.
     * `null` if the source file location could not be inferred.
     *
     * @since 8.14
     */
    val fileLocation: FileLocation?

    /**
     * The stack trace elements that lead to the problem.
     *
     * @since 8.14
     */
    val stackTrace: MutableList<StackTraceElement>?
}
