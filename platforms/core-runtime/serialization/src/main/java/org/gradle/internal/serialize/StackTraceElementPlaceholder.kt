/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.serialize

import org.gradle.api.JavaVersion
import java.io.Serializable

class StackTraceElementPlaceholder(ste: StackTraceElement) : Serializable {
    private val classLoaderName: String?
    private val moduleName: String?
    private val moduleVersion: String?
    private val declaringClass: String
    private val methodName: String
    private val fileName: String?
    private val lineNumber: Int

    init {
        if (JavaVersion.current().isJava9Compatible) {
            // FUTURE-STDLIB: StackTraceElement.getClassLoaderName/getModuleName/getModuleVersion are JDK 9+; gated above.
            classLoaderName = ste.getClassLoaderName()
            moduleName = ste.getModuleName()
            moduleVersion = ste.getModuleVersion()
        } else {
            classLoaderName = null
            moduleName = null
            moduleVersion = null
        }
        declaringClass = ste.getClassName()
        methodName = ste.getMethodName()
        fileName = ste.getFileName()
        lineNumber = ste.getLineNumber()
    }

    fun toStackTraceElement(): StackTraceElement {
        if (JavaVersion.current().isJava9Compatible) {
            // FUTURE-STDLIB: the 7-arg StackTraceElement constructor is JDK 9+; gated above.
            return StackTraceElement(classLoaderName, moduleName, moduleVersion, declaringClass, methodName, fileName, lineNumber)
        } else {
            return StackTraceElement(declaringClass, methodName, fileName, lineNumber)
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
