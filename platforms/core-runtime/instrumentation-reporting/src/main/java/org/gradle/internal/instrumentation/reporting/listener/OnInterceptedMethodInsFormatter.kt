/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.instrumentation.reporting.listener

import java.io.File

class OnInterceptedMethodInsFormatter {
    @Suppress("unused")
    fun format(source: File?, sourceFileName: String, className: String, methodCallOwner: String, methodName: String, methodDescriptor: String, lineNumber: Int): String {
        var sourceFileName = sourceFileName
        var className = className
        val methodCallOwnerClassName = methodCallOwner.replace("/", ".")
        // Gradle Kotlin scripts have a weird class name so IntelliJ stacktrace parser doesn't parse them well
        className = if (sourceFileName.endsWith("gradle.kts"))
            sourceFileName.replace(".kts", "")
        else
            className.replace("/", ".")
        // Build scripts are all named the same, so IntelliJ jump-to-source functionality is not that useful, so let's use the path.
        // Note: For other dependencies a source could be a jar, so using absolute path directly might not be useful.
        sourceFileName = if (source != null && isAnyBuildScript(sourceFileName))
            "file://" + source.getAbsolutePath()
        else
            sourceFileName
        return String.format("%s.%s(): at %s(%s:%d)", methodCallOwnerClassName, methodName, className, sourceFileName, lineNumber)
    }

    companion object {
        private fun isAnyBuildScript(sourceFileName: String): Boolean {
            when (sourceFileName) {
                "init.gradle.kts", "settings.gradle.kts", "build.gradle.kts", "init.gradle", "settings.gradle", "build.gradle" -> return true
                else -> return false
            }
        }
    }
}
