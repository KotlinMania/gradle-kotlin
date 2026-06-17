/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.plugins

import org.gradle.util.internal.GroovyDependencyUtil
import org.gradle.util.internal.VersionNumber
import java.io.File
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern

class GroovyJarFile private constructor(val file: File?, private val matcher: Matcher) {
    val baseName: String?
        get() = matcher.group(1)

    val version: VersionNumber?
        get() = VersionNumber.parse(matcher.group(2))

    val isGroovyAll: Boolean
        get() = this.baseName == "groovy-all"

    val isIndy: Boolean
        get() = matcher.group(3) != null

    val dependencyNotation: String
        get() = GroovyDependencyUtil.groovyModuleDependency(this.baseName, this.version, if (this.isIndy) "indy" else null)

    companion object {
        private val FILE_NAME_PATTERN: Pattern = Pattern.compile("(groovy(?:-all)?)-(\\d.*?)(-indy)?.jar")

        fun parse(file: File): GroovyJarFile? {
            var file = file
            try {
                if (file.getName().contains("groovy")) {
                    // Resolve a symlink file to the real location
                    file = file.toPath().toRealPath().toFile()
                }
            } catch (e: IOException) {
                // Let the code use the original File otherwise
            }
            val matcher: Matcher = FILE_NAME_PATTERN.matcher(file.getName())
            return if (matcher.matches()) GroovyJarFile(file, matcher) else null
        }
    }
}
