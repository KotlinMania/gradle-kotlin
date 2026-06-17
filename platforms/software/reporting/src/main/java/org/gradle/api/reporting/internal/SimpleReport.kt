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
package org.gradle.api.reporting.internal

import groovy.lang.Closure
import org.gradle.api.Describable
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemLocationProperty
import org.gradle.api.reporting.ConfigurableReport
import org.gradle.api.reporting.Report
import org.gradle.util.internal.ConfigureUtil

abstract class SimpleReport(private val name: String, private val displayName: Describable, private val outputType: Report.OutputType) : ConfigurableReport {
    override fun getName(): String {
        return name
    }

    override fun getDisplayName(): String {
        return displayName.getDisplayName()
    }

    override fun toString(): String {
        return "Report " + getName()
    }

    abstract override fun getOutputLocation(): FileSystemLocationProperty<out FileSystemLocation>?

    override fun getOutputType(): Report.OutputType {
        return outputType
    }

    override fun configure(configure: Closure<*>): Report {
        return ConfigureUtil.configureSelf<SimpleReport>(configure, this)
    }
}
