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
 * A failure reported on build operations.
 *
 * The main reason to have this type is so we can query failures
 * for metadata and associated problems.
 *
 * @since 9.0.0
 */
interface Failure {
    /**
     * The class name of the original throwable.
     *
     * @since 9.0.0
     */
    val exceptionType: String?

    /**
     * The message of the failure.
     *
     * @since 9.0.0
     */
    val message: String?

    /**
     * The metadata of the failure.
     *
     * @since 9.0.0
     */
    val metadata: MutableMap<String, String>?

    /**
     * The stack trace of the failure.
     *
     * @since 9.0.0
     */
    val stackTrace: MutableList<StackTraceElement>?

    /**
     * The class level annotations of the underlying exception class.
     *
     * @since 9.0.0
     */
    val classLevelAnnotations: MutableList<String>?

    /**
     * The causes of this failure.
     *
     * @since 9.0.0
     */
    val causes: MutableList<Failure>?

    /**
     * Problems associated with this failure.
     *
     * @since 9.0.0
     */
    val problems: MutableList<Problem>?
}
