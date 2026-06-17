/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.runtimeshaded

import org.gradle.internal.util.Trie
import org.gradle.model.internal.asm.AsmConstants
import org.objectweb.asm.commons.Remapper
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.UncheckedIOException
import java.net.URL
import java.nio.charset.Charset

internal class ImplementationDependencyRelocator(resource: URL) : Remapper(AsmConstants.ASM_LEVEL) {
    private val prefixes: Trie

    override fun map(name: String): String {
        val classNameStart: Int = classNameStart(name)
        val actualName = if (classNameStart < 0) name else name.substring(classNameStart)
        val relocated = maybeRelocateResource(actualName)
        if (relocated == null) {
            return name
        }
        if (classNameStart < 0) {
            return relocated
        }
        return name.substring(0, classNameStart) + relocated
    }

    fun maybeRelocateResource(resource: String): String? {
        if (prefixes.find(resource)) {
            return "org/gradle/internal/impldep/" + resource
        }
        return null
    }

    fun keepOriginalResource(resource: String?): Boolean {
        return resource == null || maybeRelocateResource(resource) == null || !mustBeRelocated(resource)
    }

    private val mustRelocateList: MutableList<String> = mutableListOf<String?>( // In order to use a newer version of jna the resources must not be available in the old location
        "com/sun/jna",
        "org/apache/groovy",  // JGit properties work from their relocated locations and conflict if they are left in place.
        "org/eclipse/jgit"
    )

    init {
        prefixes = readPrefixes(resource)
    }

    private fun mustBeRelocated(resource: String): Boolean {
        for (mustRelocate in mustRelocateList) {
            if (resource.startsWith(mustRelocate)) {
                return true
            }
        }
        return false
    }

    fun maybeRemap(literal: String): ClassLiteralRemapping? {
        if (literal.startsWith("class$")) {
            val className = literal.substring(6).replace('$', '.')
            val replacement = maybeRelocateResource(className.replace('.', '/'))
            if (replacement == null) {
                return null
            }
            val fieldNameReplacement = "class$" + replacement.replace('/', '$')
            return ClassLiteralRemapping(className, replacement, fieldNameReplacement)
        }
        return null
    }

    class ClassLiteralRemapping(val literal: String?, val literalReplacement: String?, val fieldNameReplacement: String?)

    companion object {
        private fun readPrefixes(resource: URL): Trie {
            val builder = Trie.Builder()
            try {
                resource.openStream().use { `is` ->
                    val reader = BufferedReader(InputStreamReader(`is`, Charset.forName("UTF-8")))
                    var line: String?
                    while ((reader.readLine().also { line = it }) != null) {
                        line = line!!.trim { it <= ' ' }
                        if (line.length > 0) {
                            builder.addWord(line)
                        }
                    }
                }
            } catch (e: IOException) {
                throw UncheckedIOException(e)
            }
            return builder.build()
        }

        /**
         * Returns the index of the class name within a JVM type descriptor of the form `[*Lsome/class`,
         * or `-1` if `name` is already a plain class name with no descriptor prefix.
         */
        private fun classNameStart(name: String): Int {
            val len = name.length
            var i = 0
            while (i < len && name.get(i) == '[') {
                i++
            }
            // Must be followed by 'L' and at least one more character to be a descriptor.
            if (i < len && name.get(i) == 'L' && i + 1 < len) {
                return i + 1
            }
            return -1
        }
    }
}
