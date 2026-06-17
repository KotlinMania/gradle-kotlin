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
package org.gradle.internal.dispatch

import org.gradle.internal.UncheckedException
import org.gradle.util.internal.CollectionUtils
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

class MethodInvocation(@JvmField val method: Method, args: Array<Any?>?) {
    @JvmField
    val arguments: Array<Any?>

    init {
        arguments = if (args == null) ZERO_ARGS else args
    }

    val methodName: String
        get() = method.getName()

    override fun equals(obj: Any?): Boolean {
        if (obj === this) {
            return true
        }
        if (obj == null || obj.javaClass != javaClass) {
            return false
        }

        val other = obj as MethodInvocation
        if (method != other.method) {
            return false
        }

        return arguments.contentEquals(other.arguments)
    }

    override fun hashCode(): Int {
        return method.hashCode()
    }

    override fun toString(): String {
        return String.format("[MethodInvocation method: %s(%s)]", method.getName(), CollectionUtils.join(", ", arguments))
    }

    fun invokeOn(target: Any?) {
        try {
            method.setAccessible(true)
            method.invoke(target, *arguments)
        } catch (e: InvocationTargetException) {
            throw UncheckedException.throwAsUncheckedException(e.cause)
        } catch (throwable: Throwable) {
            throw UncheckedException.throwAsUncheckedException(throwable)
        }
    }

    companion object {
        private val ZERO_ARGS = arrayOfNulls<Any>(0)
    }
}

