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
package org.gradle.api.plugins.quality

import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty

/**
 * Configuration options for the Checkstyle plugin.
 *
 * @see CheckstylePlugin
 */
abstract class CheckstyleExtension(private val project: Project) : CodeQualityExtension() {
    /**
     * The Checkstyle configuration to use. Replaces the `configFile` property.
     *
     * @since 2.2
     */
    /**
     * The Checkstyle configuration to use. Replaces the `configFile` property.
     *
     * @since 2.2
     */
    @get:ToBeReplacedByLazyProperty
    var config: TextResource? = null
    /**
     * The properties available for use in the configuration file. These are substituted into the configuration file.
     */
    /**
     * The properties available for use in the configuration file. These are substituted into the configuration file.
     */
    @get:ToBeReplacedByLazyProperty
    var configProperties: MutableMap<String, Any> = LinkedHashMap<String, Any>()
    /**
     * The maximum number of errors that are tolerated before breaking the build
     * or setting the failure property. Defaults to `0`.
     *
     *
     * Example: maxErrors = 42
     *
     * @return the maximum number of errors allowed
     * @since 3.4
     */
    /**
     * Set the maximum number of errors that are tolerated before breaking the build.
     *
     * @param maxErrors number of errors allowed
     * @since 3.4
     */
    @get:ToBeReplacedByLazyProperty
    var maxErrors: Int = 0
    /**
     * The maximum number of warnings that are tolerated before breaking the build
     * or setting the failure property. Defaults to `Integer.MAX_VALUE`.
     *
     *
     * Example: maxWarnings = 1000
     *
     * @return the maximum number of warnings allowed
     * @since 3.4
     */
    /**
     * Set the maximum number of warnings that are tolerated before breaking the build.
     *
     * @param maxWarnings number of warnings allowed
     * @since 3.4
     */
    @get:ToBeReplacedByLazyProperty
    var maxWarnings: Int = Int.MAX_VALUE
    /**
     * Whether rule violations are to be displayed on the console. Defaults to `true`.
     *
     * Example: showViolations = false
     */
    /**
     * Whether rule violations are to be displayed on the console. Defaults to `true`.
     *
     * Example: showViolations = false
     */
    @get:ToBeReplacedByLazyProperty
    var isShowViolations: Boolean = true

    init {
        this.enableExternalDtdLoad.convention(false)
    }

    @get:ToBeReplacedByLazyProperty
    var configFile: File
        /**
         * The Checkstyle configuration file to use.
         */
        get() = this.config!!.asFile()
        /**
         * The Checkstyle configuration file to use.
         */
        set(configFile) {
            this.config = project.getResources().getText().fromFile(configFile)
        }

    /**
     * Path to other Checkstyle configuration files. By default, this path is `$rootProject.projectDir/config/checkstyle`
     *
     *
     * This path will be exposed as the variable `config_loc` in Checkstyle's configuration files.
     *
     *
     * @return path to other Checkstyle configuration files
     * @since 4.7
     */
    abstract val configDirectory: DirectoryProperty?

    @get:Input
    @get:Optional
    @get:Incubating
    abstract val enableExternalDtdLoad: Property<Boolean>?
}
