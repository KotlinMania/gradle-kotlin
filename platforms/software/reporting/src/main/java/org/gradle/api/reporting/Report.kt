/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.reporting

import org.gradle.api.Namer
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.util.Configurable

/**
 * A file based report to be created.
 *
 *
 * Tasks that produce reports expose instances of this type for configuration via the [Reporting] interface.
 */
interface Report : Configurable<Report?> {
    @get:Input
    val name: String

    @get:Input
    val displayName: String?

    @get:Input
    val required: Property<Boolean>?

    @get:Internal("Implementations need to add the correct annotation, @OutputDirectory or @OutputFile")
    val outputLocation: Property<out FileSystemLocation>?

    /**
     * The type of output the report produces
     */
    enum class OutputType {
        /**
         * The report outputs a single file.
         *
         *
         * That is, the [.getOutputLocation] points to a single file.
         */
        FILE,

        /**
         * The report outputs files into a directory.
         *
         *
         * That is, the [.getOutputLocation] points to a directory.
         */
        DIRECTORY
    }

    @get:Input
    val outputType: OutputType?

    companion object {
        val NAMER: Namer<Report> = object : Namer<Report> {
            override fun determineName(report: Report): String {
                return report.name
            }
        }
    }
}
