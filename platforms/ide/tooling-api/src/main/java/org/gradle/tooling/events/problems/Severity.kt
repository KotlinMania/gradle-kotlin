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
package org.gradle.tooling.events.problems

import org.gradle.api.Incubating
import org.gradle.tooling.events.problems.internal.DefaultSeverity

/**
 * Represents a problem severity.
 *
 * @since 8.6
 */
@Incubating
interface Severity {
    /**
     * The severity level represented by a string.
     *
     * @return the severity
     * @since 8.6
     */
    val severity: Int

    /**
     * Returns true if this severity is one of [.ADVICE], [.WARNING], or [.ERROR].
     *
     * @return if this instance is a known severity
     * @since 8.6
     */
    val isKnown: Boolean

    companion object {
        // Note: the static fields must be in sync with entries from org.gradle.api.problems.Severity.
        /**
         * Advice-level severity. Only emitted by Gradle versions &lt; 9.6.
         *
         * @since 8.6
         */
        val ADVICE: Severity = DefaultSeverity(0, true)

        /**
         * Warning-level severity.
         * Gradle versions &lt; 9.6 allow explicitly setting severity to warning. Starting from 9.6, warning severity is auto-assigned to problems that won't fail the build.
         *
         * @since 8.6
         */
        val WARNING: Severity = DefaultSeverity(1, true)

        /**
         * Error-level severity.
         * Gradle versions &lt; 9.6 allow explicitly setting severity to error. Starting from 9.6, error severity is auto-assigned to problems that will fail the build.
         *
         * @since 8.6
         */
        val ERROR: Severity = DefaultSeverity(2, true)
    }
}
