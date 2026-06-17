/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.scan.config

/**
 * Represents the aspects of Build Scan configuration that Gradle contributes.
 * Does not include configuration aspects that the scan plugin manages (e.g. server address).
 * Currently, this is effectively the --scan and --no-scan invocation options.
 *
 * @since 4.0
 */
interface BuildScanConfig {
    /**
     * Indicates whether a scan was **explicitly** requested.
     *
     * This effectively maps to [StartParameter.isBuildScan].
     */
    val isEnabled: Boolean

    /**
     * Indicates whether a scan was **explicitly not** requested.
     *
     * This effectively maps to [StartParameter.isNoBuildScan].
     */
    val isDisabled: Boolean

    /**
     * Indicates whether the Develocity plugin should not apply itself because its known to be incompatible.
     *
     * @since 4.4
     */
    val unsupportedMessage: String?

    /**
     * Attributes about the build environment that the Develocity plugin needs to know about.
     *
     * This is effectively an insulation layer between the plugin and internal API.
     *
     * @return the attributes
     * @since 4.4
     */
    val attributes: Attributes?

    interface Attributes {
        /**
         * No longer actually used, but needed for binary compatibility.
         */
        val isRootProjectHasVcsMappings: Boolean

        /**
         * Whether the currently executing build is intended to execute tasks.
         *
         * @since 5.0
         */
        val isTaskExecutingBuild: Boolean
    }
}
