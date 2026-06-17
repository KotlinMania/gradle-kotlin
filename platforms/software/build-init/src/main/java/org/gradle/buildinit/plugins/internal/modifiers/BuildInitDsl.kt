/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.buildinit.plugins.internal.modifiers

import com.google.common.collect.ImmutableList
import org.apache.commons.lang3.StringUtils
import org.gradle.api.GradleException
import org.gradle.internal.logging.text.TreeFormatter

enum class BuildInitDsl(private val fileExtension: String) : WithIdentifier {
    KOTLIN(".gradle.kts"),
    GROOVY(".gradle");

    override fun getId(): String {
        return Names.idFor(this)
    }

    fun fileNameFor(fileNameWithoutExtension: String?): String {
        return fileNameWithoutExtension + fileExtension
    }

    override fun toString(): String {
        return StringUtils.capitalize(name.lowercase())
    }

    companion object {
        fun fromName(name: String?): BuildInitDsl {
            if (name == null) {
                return BuildInitDsl.KOTLIN
            }
            for (language in entries) {
                if (language.getId() == name) {
                    return language
                }
            }
            val formatter = TreeFormatter()
            formatter.node("The requested build script DSL '" + name + "' is not supported. Supported DSLs")
            formatter.startChildren()
            for (dsl in entries) {
                formatter.node("'" + dsl.getId() + "'")
            }
            formatter.endChildren()
            throw GradleException(formatter.toString())
        }

        fun listSupported(): MutableList<String?> {
            val supported = ImmutableList.builder<String?>()
            for (dsl in entries) {
                supported.add(dsl.getId())
            }
            return supported.build()
        }
    }
}
