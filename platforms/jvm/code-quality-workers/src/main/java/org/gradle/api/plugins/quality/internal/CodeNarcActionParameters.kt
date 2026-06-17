/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.api.plugins.quality.internal

import org.gradle.api.plugins.internal.ant.AntWorkParameters

/**
 * Parameters used to configure a [CodeNarcInvoker].
 */
interface CodeNarcActionParameters : AntWorkParameters {
    val compilationClasspath: ConfigurableFileCollection?

    val config: RegularFileProperty?

    val maxPriority1Violations: Property<Int?>?

    val maxPriority2Violations: Property<Int?>?

    val maxPriority3Violations: Property<Int?>?

    val enabledReports: ListProperty<EnabledReport?>?

    val ignoreFailures: Property<Boolean?>?

    val source: ConfigurableFileCollection?

    interface EnabledReport {
        val name: Property<String?>?

        val outputLocation: RegularFileProperty?
    }
}
