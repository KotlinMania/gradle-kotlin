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
package org.gradle.api.internal.jvm

import org.gradle.util.internal.TextUtil

class ClassDirectoryBinaryNamingScheme(val outputDirectoryBase: String) {
    private val collapsedName: String

    init {
        this.collapsedName = collapseMain(this.outputDirectoryBase)
    }

    val description: String
        get() = "classes '" + this.outputDirectoryBase + "'"

    fun getTaskName(verb: String?): String {
        return getTaskName(verb, null)
    }

    fun getTaskName(verb: String?, target: String?): String {
        var name = this.outputDirectoryBase
        if (target != null) {
            name = collapsedName
        }
        return TextUtil.toLowerCamelCase(nullToEmpty(verb) + " " + name + " " + nullToEmpty(target))!!
    }

    private fun nullToEmpty(input: String?): String {
        return if (input == null) "" else input
    }

    companion object {
        private fun collapseMain(name: String): String {
            return if (name == "main") "" else name
        }
    }
}
