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
package org.gradle.api.internal.tasks.compile.incremental.recomp

import org.apache.commons.lang3.StringUtils
import java.util.stream.Collectors

/**
 * A converter which infers the class names from the file name.
 */
class FileNameDerivingClassNameConverter(private val delegate: SourceFileClassNameConverter, private val fileExtensions: MutableSet<String>) : SourceFileClassNameConverter {
    override fun getClassNames(sourceFileRelativePath: String): MutableSet<String?> {
        val classNames = delegate.getClassNames(sourceFileRelativePath)
        if (!classNames.isEmpty()) {
            return classNames
        }

        for (fileExtension in fileExtensions) {
            if (sourceFileRelativePath.endsWith(fileExtension)) {
                return mutableSetOf<String?>(StringUtils.removeEnd(sourceFileRelativePath.replace('/', '.'), fileExtension))
            }
        }

        return mutableSetOf<String?>()
    }

    override fun getRelativeSourcePaths(className: String): MutableSet<String?> {
        val sourcePaths = delegate.getRelativeSourcePaths(className)
        if (!sourcePaths.isEmpty()) {
            return sourcePaths
        }

        val paths = fileExtensions.stream()
            .map<String?> { fileExtension: String? -> classNameToRelativePath(className, fileExtension!!) }
            .collect(Collectors.toSet())

        // Classes with $ may be inner classes
        val innerClassIdx = className.indexOf("$")
        if (innerClassIdx > 0) {
            val baseName = className.substring(0, innerClassIdx)
            fileExtensions.stream()
                .map<String?> { fileExtension: String? -> classNameToRelativePath(baseName, fileExtension!!) }
                .forEach { e: String? -> paths.add(e) }
        }

        return paths
    }

    override fun getRelativeSourcePathsThatExist(className: String?): MutableSet<String?> {
        return delegate.getRelativeSourcePaths(className)
    }

    private fun classNameToRelativePath(className: String, fileExtension: String): String {
        return className.replace('.', '/') + fileExtension
    }
}
