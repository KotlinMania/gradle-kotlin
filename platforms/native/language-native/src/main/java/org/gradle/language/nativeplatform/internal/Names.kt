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
package org.gradle.language.nativeplatform.internal

import org.apache.commons.lang3.StringUtils

abstract class Names {
    abstract fun append(suffix: String?): Names?

    /**
     * The raw name.
     */
    abstract val name: String?

    /**
     * Camel case formatted base name.
     */
    abstract val baseName: String?

    /**
     * Lower case formatted base name, with '_' separators
     */
    abstract val lowerBaseName: String?

    abstract fun withPrefix(prefix: String?): String?

    abstract fun withSuffix(suffix: String?): String?

    abstract fun getTaskName(action: String?): String?

    abstract fun getCompileTaskName(language: String?): String?

    // Includes trailing '/'
    @JvmField
    abstract val dirName: String?

    private class Main : Names() {
        override fun getName(): String {
            return "main"
        }

        override fun getBaseName(): String {
            return "main"
        }

        override fun getLowerBaseName(): String {
            return "main"
        }

        override fun getCompileTaskName(language: String?): String {
            return "compile" + StringUtils.capitalize(language)
        }

        override fun getTaskName(action: String?): String? {
            return action
        }

        override fun getDirName(): String {
            return "main/"
        }

        override fun withPrefix(prefix: String?): String? {
            return prefix
        }

        override fun withSuffix(suffix: String?): String? {
            return suffix
        }

        override fun append(suffix: String?): Names {
            return of("main" + StringUtils.capitalize(suffix))
        }
    }

    private class Other(private val name: String?, name: String) : Names() {
        private val baseName: String
        private val lowerBaseName: String
        private val capitalizedBaseName: String
        private val dirName: String

        init {
            val baseName = StringBuilder()
            val lowerBaseName = StringBuilder()
            val capBaseName = StringBuilder()
            val dirName = StringBuilder()
            var startLast = 0
            var i = 0
            while (i < name.length) {
                if (Character.isUpperCase(name.get(i))) {
                    if (i > startLast) {
                        append(name, startLast, i, baseName, lowerBaseName, capBaseName, dirName)
                    }
                    startLast = i
                }
                i++
            }
            if (i > startLast) {
                append(name, startLast, i, baseName, lowerBaseName, capBaseName, dirName)
            }
            this.baseName = baseName.toString()
            this.lowerBaseName = lowerBaseName.toString()
            this.capitalizedBaseName = capBaseName.toString()
            this.dirName = dirName.toString()
        }

        override fun getName(): String? {
            return name
        }

        override fun getBaseName(): String {
            return baseName
        }

        override fun getLowerBaseName(): String {
            return lowerBaseName
        }

        override fun withPrefix(prefix: String?): String {
            return prefix + capitalizedBaseName
        }

        override fun withSuffix(suffix: String?): String {
            return baseName + StringUtils.capitalize(suffix)
        }

        override fun getTaskName(action: String?): String {
            return action + capitalizedBaseName
        }

        override fun getCompileTaskName(language: String?): String {
            return "compile" + capitalizedBaseName + StringUtils.capitalize(language)
        }

        // Includes trailing '/'
        override fun getDirName(): String {
            return dirName
        }

        override fun append(suffix: String?): Names {
            return of(name + StringUtils.capitalize(suffix))
        }

        fun append(name: String, start: Int, end: Int, baseName: StringBuilder, lowerBaseName: StringBuilder, capBaseName: StringBuilder, dirName: StringBuilder) {
            dirName.append(name.get(start).lowercaseChar())
            dirName.append(name.substring(start + 1, end))
            dirName.append('/')
            if (start != 0 || end != 4 || !name.startsWith("main")) {
                if (baseName.length == 0) {
                    baseName.append(name.get(start).lowercaseChar())
                    baseName.append(name.substring(start + 1, end))
                } else {
                    baseName.append(name.substring(start, end))
                    lowerBaseName.append('-')
                }
                lowerBaseName.append(name.get(start).lowercaseChar())
                lowerBaseName.append(name.substring(start + 1, end))
                capBaseName.append(name.get(start).uppercaseChar())
                capBaseName.append(name.substring(start + 1, end))
            }
        }
    }

    companion object {
        fun of(name: String): Names {
            if (name == "main") {
                return Main()
            }
            return Other(name, name)
        }

        @JvmStatic
        fun of(name: String?, baseName: String): Names {
            return Other(name, baseName)
        }
    }
}
