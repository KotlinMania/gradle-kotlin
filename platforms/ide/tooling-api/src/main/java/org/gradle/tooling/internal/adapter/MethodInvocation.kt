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
package org.gradle.tooling.internal.adapter

import java.lang.reflect.Type

internal class MethodInvocation(
    val name: String,
    val returnType: Class<*>,
    val genericReturnType: Type,
    val parameterTypes: Array<Class<*>>,
    val view: Any,
    val viewType: Class<*>,
    val delegate: Any,
    val parameters: Array<Any>
) {
    var result: Any? = null
        /**
         * Marks the method as handled.
         */
        set(result) {
            found = true
            field = result
        }
    private var found = false

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(returnType.getTypeName())
        sb.append(' ')
        sb.append(delegate.javaClass.getTypeName())
        sb.append('.')
        sb.append(name)
        sb.append('(')
        for (parameter in parameterTypes) {
            sb.append(parameter.getTypeName()).append(',')
        }
        sb.append(')')
        return sb.toString()
    }

    val isGetter: Boolean
        get() = parameterTypes.size == 0 && this.isIsOrGet

    val isIsOrGet: Boolean
        get() = (name.startsWith("get") && name.length > 3) || (name.startsWith("is") && name.length > 2)

    fun found(): Boolean {
        return found
    }
}
