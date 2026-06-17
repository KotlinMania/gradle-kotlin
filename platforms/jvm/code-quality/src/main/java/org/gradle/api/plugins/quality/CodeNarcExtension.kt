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

import com.google.common.collect.Sets
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.resources.TextResource
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty

/**
 * Configuration options for the CodeNarc plugin.
 *
 * @see CodeNarcPlugin
 */
abstract class CodeNarcExtension(private val project: Project) : CodeQualityExtension() {
    /**
     * The CodeNarc configuration to use. Replaces the `configFile` property.
     *
     * @since 2.2
     */
    /**
     * The CodeNarc configuration to use. Replaces the `configFile` property.
     *
     * @since 2.2
     */
    @get:ToBeReplacedByLazyProperty
    var config: TextResource? = null
    /**
     * The maximum number of priority 1 violations allowed before failing the build.
     */
    /**
     * The maximum number of priority 1 violations allowed before failing the build.
     */
    @get:ToBeReplacedByLazyProperty
    var maxPriority1Violations: Int = 0
    /**
     * The maximum number of priority 2 violations allowed before failing the build.
     */
    /**
     * The maximum number of priority 2 violations allowed before failing the build.
     */
    @get:ToBeReplacedByLazyProperty
    var maxPriority2Violations: Int = 0
    /**
     * The maximum number of priority 3 violations allowed before failing the build.
     */
    /**
     * The maximum number of priority 3 violations allowed before failing the build.
     */
    @get:ToBeReplacedByLazyProperty
    var maxPriority3Violations: Int = 0

    /**
     * The format type of the CodeNarc report. One of `html`, `xml`, `text`, `console`.
     */
    @get:ToBeReplacedByLazyProperty
    var reportFormat: String? = null
        /**
         * The format type of the CodeNarc report. One of `html`, `xml`, `text`, `console`.
         */
        set(reportFormat) {
            if (REPORT_FORMATS.contains(reportFormat)) {
                field = reportFormat
            } else {
                throw InvalidUserDataException("'" + reportFormat + "' is not a valid codenarc report format")
            }
        }

    @get:ToBeReplacedByLazyProperty
    var configFile: File
        /**
         * The CodeNarc configuration file to use.
         */
        get() = this.config!!.asFile()
        /**
         * The CodeNarc configuration file to use.
         */
        set(file) {
            this.config = project.getResources().getText().fromFile(file)
        }

    companion object {
        private val REPORT_FORMATS: MutableSet<String> = Sets.newHashSet<String>("xml", "html", "console", "text")
    }
}
