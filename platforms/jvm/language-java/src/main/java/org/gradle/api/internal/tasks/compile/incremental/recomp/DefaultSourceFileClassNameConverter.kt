/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.recomp

class DefaultSourceFileClassNameConverter(private val sourceClassesMapping: MutableMap<String?, MutableSet<String?>?>) : SourceFileClassNameConverter {
    private val classSourceMapping: MutableMap<String?, MutableSet<String?>?>

    init {
        this.classSourceMapping = constructReverseMapping(sourceClassesMapping)
    }

    private fun constructReverseMapping(sourceClassesMapping: MutableMap<String?, MutableSet<String?>?>): MutableMap<String?, MutableSet<String?>?> {
        val reverse: MutableMap<String?, MutableSet<String?>?> = HashMap<String?, MutableSet<String?>?>()
        for (entry in sourceClassesMapping.entries) {
            for (cls in entry.value!!) {
                reverse.computeIfAbsent(cls) { key: kotlin.String? -> java.util.HashSet<kotlin.String?>() }!!.add(entry.key)
            }
        }
        return reverse
    }

    override fun getClassNames(sourceFileRelativePath: String?): MutableSet<String?>? {
        return sourceClassesMapping.getOrDefault(sourceFileRelativePath, mutableSetOf<String?>())
    }

    override fun getRelativeSourcePaths(fqcn: String?): MutableSet<String?>? {
        return classSourceMapping.getOrDefault(fqcn, mutableSetOf<String?>())
    }

    override fun getRelativeSourcePathsThatExist(className: String?): MutableSet<String?>? {
        return getRelativeSourcePaths(className)
    }
}
