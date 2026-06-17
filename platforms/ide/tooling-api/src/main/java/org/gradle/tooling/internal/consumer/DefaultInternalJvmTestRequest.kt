/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.internal.consumer

import org.gradle.tooling.internal.protocol.test.InternalJvmTestRequest

class DefaultInternalJvmTestRequest(private val className: String?, private val methodName: String?, private val testPattern: String?) : InternalJvmTestRequest {
    override fun getClassName(): String? {
        return className
    }

    override fun getMethodName(): String? {
        return methodName
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultInternalJvmTestRequest

        if (if (className != null) (className != that.className) else that.className != null) {
            return false
        }

        if (if (methodName != null) (methodName != that.methodName) else that.methodName != null) {
            return false
        }

        return !(if (testPattern != null) (testPattern != that.testPattern) else that.testPattern != null)
    }

    override fun hashCode(): Int {
        var result = if (className != null) className.hashCode() else 0
        result = 31 * result + (if (methodName != null) methodName.hashCode() else 0)
        result = 31 * result + (if (testPattern != null) testPattern.hashCode() else 0)
        return result
    }
}
