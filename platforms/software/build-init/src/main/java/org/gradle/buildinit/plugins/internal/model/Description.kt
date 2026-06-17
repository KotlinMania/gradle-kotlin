/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.buildinit.plugins.internal.model

import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework
import org.gradle.buildinit.plugins.internal.modifiers.Language
import java.util.Arrays
import java.util.Optional
import java.util.TreeSet

class Description private constructor(
    val language: Language?,
    val defaultTestFramework: BuildInitTestFramework?,
    supportedTestFrameworks: MutableList<BuildInitTestFramework?>,
    private val pluginName: String?,
    val pluginVersionProperty: String?,
    val explicitPluginAlias: String?
) {
    val supportedTestFrameworks: MutableSet<BuildInitTestFramework?>

    init {
        this.supportedTestFrameworks = TreeSet<BuildInitTestFramework?>(supportedTestFrameworks)
    }

    fun getPluginName(): Optional<String?> {
        return Optional.ofNullable<String?>(pluginName)
    }

    companion object {
        val JAVA: Description = Description(
            Language.JAVA,
            BuildInitTestFramework.JUNIT_JUPITER,
            Arrays.asList<BuildInitTestFramework?>(BuildInitTestFramework.JUNIT, BuildInitTestFramework.JUNIT_JUPITER, BuildInitTestFramework.TESTNG, BuildInitTestFramework.SPOCK),
            null, null, null
        )

        val GROOVY: Description = Description(
            Language.GROOVY,
            BuildInitTestFramework.SPOCK,
            mutableListOf<BuildInitTestFramework?>(BuildInitTestFramework.SPOCK),
            "groovy", null, null
        )

        val SCALA: Description = Description(
            Language.SCALA,
            BuildInitTestFramework.SCALATEST,
            mutableListOf<BuildInitTestFramework?>(BuildInitTestFramework.SCALATEST),
            "scala", null, null
        )

        val KOTLIN: Description = Description(
            Language.KOTLIN,
            BuildInitTestFramework.KOTLINTEST,
            Arrays.asList<BuildInitTestFramework?>(BuildInitTestFramework.KOTLINTEST, BuildInitTestFramework.JUNIT_JUPITER),
            "org.jetbrains.kotlin.jvm", "kotlin", "kotlin-jvm"
        )
    }
}
