/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.buildinit.plugins.internal

import org.gradle.api.Incubating
import org.gradle.api.file.Directory
import org.gradle.buildinit.InsecureProtocolOption
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.util.Optional

class InitSettings(
    val projectName: String, @get:Incubating @get:Incubating val isUseTestSuites: Boolean, subprojects: MutableList<String>, val modularizationOption: ModularizationOption,
    val dsl: BuildInitDsl, private val packageName: String?, val testFramework: BuildInitTestFramework, private val insecureProtocolOption: InsecureProtocolOption, val target: Directory,
    private val javaLanguageVersion: JavaLanguageVersion?, @get:Incubating val isWithComments: Boolean
) {
    val subprojects: MutableList<String>

    constructor(
        projectName: String, useIncubatingAPIs: Boolean, subprojects: MutableList<String>, modularizationOption: ModularizationOption,
        dsl: BuildInitDsl, packageName: String?, testFramework: BuildInitTestFramework, target: Directory
    ) : this(projectName, useIncubatingAPIs, subprojects, modularizationOption, dsl, packageName, testFramework, InsecureProtocolOption.WARN, target, null, true)

    init {
        this.subprojects = getSubprojects(subprojects, modularizationOption)
    }

    fun getPackageName(): String {
        return packageName!!
    }

    fun getInsecureProtocolOption(): InsecureProtocolOption? {
        return insecureProtocolOption
    }

    @Incubating
    fun getJavaLanguageVersion(): Optional<JavaLanguageVersion> {
        return Optional.ofNullable<JavaLanguageVersion>(javaLanguageVersion)
    }

    companion object {
        const val CONVENTION_PLUGIN_NAME_PREFIX: String = "buildlogic"

        private fun getSubprojects(subprojects: MutableList<String>, modularizationOption: ModularizationOption): MutableList<String> {
            if (!subprojects.isEmpty() && modularizationOption == ModularizationOption.SINGLE_PROJECT) {
                return mutableListOf<String>(subprojects.get(0))
            }
            return subprojects
        }
    }
}
