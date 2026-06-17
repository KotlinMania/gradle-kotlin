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
 * Parameters used to configure a [PmdInvoker].
 */
@Suppress("deprecation") // TargetJdk is deprecated; this internal type is removed alongside it.
interface PmdActionParameters : AntWorkParameters {
    val pmdClasspath: ConfigurableFileCollection?

    val targetJdk: Property<TargetJdk?>?

    val ruleSets: ListProperty<String?>?

    val ruleSetConfigFiles: ConfigurableFileCollection?

    val ignoreFailures: Property<Boolean?>?

    val consoleOutput: Property<Boolean?>?

    val stdOutIsAttachedToTerminal: Property<Boolean?>?

    val auxClasspath: ConfigurableFileCollection?

    val rulesMinimumPriority: Property<Int?>?

    val maxFailures: Property<Int?>?

    val incrementalAnalysis: Property<Boolean?>?

    val incrementalCacheFile: RegularFileProperty?

    val threads: Property<Int?>?

    val source: ConfigurableFileCollection?

    val enabledReports: ListProperty<EnabledReport?>?

    interface EnabledReport {
        val name: Property<String?>?

        val outputLocation: RegularFileProperty?
    }
}
