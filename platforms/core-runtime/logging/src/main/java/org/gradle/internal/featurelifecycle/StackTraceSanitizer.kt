/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.internal.featurelifecycle

import org.gradle.problems.buildtree.ProblemStream

internal class StackTraceSanitizer(private val calledFrom: Class<*>) : ProblemStream.StackTraceTransformer {
    override fun transform(originalStack: Array<StackTraceElement>): MutableList<StackTraceElement> {
        val result: MutableList<StackTraceElement> = ArrayList<StackTraceElement>()
        val calledFromName = calledFrom.getName()
        var calledFromFound = false
        var caller: Int
        caller = 0
        while (caller < originalStack.size) {
            val current = originalStack[caller]
            if (!calledFromFound) {
                if (current.getClassName().startsWith(calledFromName)) {
                    calledFromFound = true
                }
            } else {
                if (!current.getClassName().startsWith(calledFromName)) {
                    break
                }
            }
            caller++
        }
        while (caller < originalStack.size) {
            val stackTraceElement = originalStack[caller]
            if (!isSystemStackFrame(stackTraceElement.getClassName())) {
                result.add(stackTraceElement)
            }
            caller++
        }
        return result
    }

    companion object {
        private fun isSystemStackFrame(className: String): Boolean {
            return className.startsWith("jdk.internal.") ||
                    className.startsWith("sun.") ||
                    className.startsWith("com.sun.") ||
                    className.startsWith("org.codehaus.groovy.") ||
                    className.startsWith("org.gradle.internal.metaobject.") ||
                    className.startsWith("org.gradle.kotlin.dsl.execution.")
        }
    }
}
