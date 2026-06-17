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

import org.gradle.api.Incubating

/**
 * A problem severity.
 *
 * @since 8.6
 */
@Incubating
enum class Severity(displayName: String) {
    /**
     * Advice-level severity.
     */
    @Deprecated("Only kept for backward compatibility. Will be removed in Gradle 10.0.")
    ADVICE("Advice"),

    /**
     * Warning-level severity, for problems that won't fail the build.
     *
     */
    WARNING("Warning"),

    /**
     * Error-level severity, for problems that will fail the build.
     */
    ERROR("Error");

    private val displayName: String?

    init {
        this.displayName = displayName
    }

    override fun toString(): String {
        return displayName!!
    }
}
