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
package org.gradle.api.problems

import org.gradle.api.Action
import org.gradle.api.Incubating

/**
 * Provides options to configure problems.
 *
 * @see ProblemReporter
 *
 * @since 8.6
 */
@Incubating
interface ProblemSpec {
    /**
     * Declares a short, but context-dependent message for this problem.
     *
     *
     * The label is expected to span a single line. Any newline characters will be removed.
     *
     * @param contextualLabel the short message
     * @return this
     * @since 8.8
     */
    fun contextualLabel(contextualLabel: String?): ProblemSpec?

    /**
     * Declares where this problem is documented.
     *
     * @return this
     * @since 8.6
     */
    fun documentedAt(url: String?): ProblemSpec?

    /**
     * Declares that this problem is in a file.
     *
     * @param path the file location
     * @return this
     * @since 8.6
     */
    fun fileLocation(path: String?): ProblemSpec?

    /**
     * Declares that this problem is in a file on a line.
     *
     * @param path the file location
     * @param line the one-indexed line number
     * @return this
     * @since 8.6
     */
    fun lineInFileLocation(path: String?, line: Int): ProblemSpec?

    /**
     * Declares that this problem is in a file with on a line at a certain position.
     *
     * @param path the file location
     * @param line the one-indexed line number
     * @param column the one-indexed column
     * @return this
     * @since 8.6
     */
    fun lineInFileLocation(path: String?, line: Int, column: Int): ProblemSpec?

    /**
     * Declares that this problem is in a file with on a line at a certain position.
     *
     * @param path the file location
     * @param line the one-indexed line number
     * @param column the one-indexed column
     * @param length the length of the text
     * @return this
     * @since 8.6
     */
    fun lineInFileLocation(path: String?, line: Int, column: Int, length: Int): ProblemSpec?

    /**
     * Declares that this problem is in a file at a certain global position with a given length.
     *
     * @param path the file location
     * @param offset the zero-indexed global offset from the beginning of the file
     * @param length the length of the text
     * @return this
     * @since 8.6
     */
    fun offsetInFileLocation(path: String?, offset: Int, length: Int): ProblemSpec?

    /**
     * Declares that this problem is at the same place where it's reported. The stack trace will be used to determine the location.
     *
     * @return this
     * @since 8.6
     */
    fun stackLocation(): ProblemSpec?

    /**
     * Declares a long description detailing the problem.
     *
     *
     * Details can elaborate on the problem, and provide more information about the problem.
     * They can be multiple lines long, but should not detail solutions; for that, use [.solution].
     *
     * @param details the details
     * @return this
     * @since 8.6
     */
    fun details(details: String?): ProblemSpec?

    /**
     * Declares solutions and advice that contain context-sensitive data, e.g. the message contains references to variables, locations, etc.
     *
     * @param solution the solution.
     * @return this
     * @since 8.6
     */
    fun solution(solution: String?): ProblemSpec?

    /**
     * Declares additional data attached to the problem.
     *
     * @param type The type of the additional data.
     * This can be any type that implements [AdditionalData] including `abstract` classes and interfaces.
     * This type will be instantiated and provided as an argument for the `Action` passed as the second argument.
     *
     *
     * The type can have the following properties:
     *
     *  * getters and setters for collections, simple types and other types that itself follow these restrictions
     *
     *  * simple types: [String], [Integer], [Boolean], etc.
     *  * collections: [java.util.List], [java.util.Set], [java.util.Map]
     *  * primitives: `int`, `boolean`, etc.
     *
     *
     *  * Provider API types
     *
     *  * [org.gradle.api.provider.Property]
     *  * [org.gradle.api.provider.ListProperty]
     *  * [org.gradle.api.provider.SetProperty]
     *  * [org.gradle.api.provider.MapProperty]
     *
     *
     *
     *
     * @param config The configuration action for the additional data.
     *
     * @return this
     * @since 8.13
     */
    fun <T : AdditionalData?> additionalData(type: Class<T?>?, config: Action<in T?>?): ProblemSpec?

    /**
     * Declares the exception causing this problem.
     *
     * @param t the exception.
     * @return this
     * @since 8.11
     */
    fun withException(t: Throwable?): ProblemSpec?

    /**
     * Declares the severity of the problem.
     *
     * @param severity the severity
     * @return this
     * @since 8.6
     */
    @Deprecated("Severity is now determined automatically: use {@link ProblemReporter#report} for warnings and {@link ProblemReporter#throwing} for errors.")
    fun severity(severity: Severity?): ProblemSpec?
}
