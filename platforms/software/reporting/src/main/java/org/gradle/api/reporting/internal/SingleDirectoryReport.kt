/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Describable
import org.gradle.api.reporting.DirectoryReport
import org.gradle.api.reporting.Report
import org.gradle.internal.Describables
import java.io.File
import javax.inject.Inject

abstract class SingleDirectoryReport @Inject constructor(name: String, owner: Describable, private val relativeEntryPath: String?) :
    SimpleReport(name, Describables.of(name, "report for", owner), Report.OutputType.DIRECTORY), DirectoryReport {
    init {
        getRequired().convention(false)
    }

    @get:Inject
    protected abstract val projectLayout: ProjectLayout?

    override fun getEntryPoint(): File {
        if (relativeEntryPath == null) {
            return getOutputLocation().getAsFile().get()
        } else {
            return File(getOutputLocation().getAsFile().getOrNull(), relativeEntryPath)
        }
    }
}
