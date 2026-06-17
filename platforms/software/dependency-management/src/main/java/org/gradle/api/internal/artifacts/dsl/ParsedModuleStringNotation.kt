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
package org.gradle.api.internal.artifacts.dsl

import org.gradle.api.IllegalDependencyNotation

class ParsedModuleStringNotation(moduleNotation: String, artifactType: String) {
    private var group: String? = null
    private var name: String? = null
    private var version: String? = null
    private var classifier: String? = null
    val artifactType: String

    init {
        assignValuesFromModuleNotation(moduleNotation)
        this.artifactType = artifactType
    }

    private fun assignValuesFromModuleNotation(moduleNotation: String) {
        var count = 0
        var idx = 0
        var cur = -1
        while (++cur < moduleNotation.length) {
            if (':' == moduleNotation.get(cur)) {
                val fragment = moduleNotation.substring(idx, cur)
                assignValue(count, fragment)
                idx = cur + 1
                count++
            }
        }
        assignValue(count, moduleNotation.substring(idx, cur))
        count++
        if (count < 2 || count > 4) {
            throw IllegalDependencyNotation("Supplied String module notation '" + moduleNotation + "' is invalid. Example notations: 'org.gradle:gradle-core:2.2', 'org.mockito:mockito-core:1.9.5:javadoc'.")
        }
    }

    private fun assignValue(count: Int, fragment: String) {
        when (count) {
            0 -> group = if ("" == fragment) null else fragment
            1 -> name = fragment
            2 -> version = if ("" == fragment) null else fragment
            3 -> classifier = fragment
        }
    }

    fun getGroup(): String {
        return group!!
    }

    fun getName(): String {
        return name!!
    }

    fun getVersion(): String {
        return version!!
    }

    fun getClassifier(): String {
        return classifier!!
    }
}
