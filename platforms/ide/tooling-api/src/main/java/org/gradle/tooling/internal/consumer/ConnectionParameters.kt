/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer

import org.gradle.tooling.internal.protocol.ConnectionParameters

interface ConnectionParameters : ConnectionParameters {
    val projectDir: File?

    /**
     * Specifies whether to search for root project, or null to use default.
     */
    val isSearchUpwards: Boolean?

    /**
     * Returns the Gradle user home directory, or null to use default.
     */
    val gradleUserHomeDir: File?

    /**
     * Returns the daemon base directory, or null to use default.
     */
    val daemonBaseDir: File?

    val isEmbedded: Boolean?

    val daemonMaxIdleTimeValue: Int?

    val daemonMaxIdleTimeUnits: TimeUnit?

    /**
     * Whether to log debug statements eagerly
     */
    val verboseLogging: Boolean

    val distributionBaseDir: File?
}
