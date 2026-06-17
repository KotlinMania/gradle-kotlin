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
package org.gradle.internal.reflect

import org.gradle.api.GradleException
import org.gradle.internal.UncheckedException
import org.gradle.util.internal.CollectionUtils
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class JavaMethod<T, R>(private val returnType: Class<R?>, @JvmField val method: Method) {
    constructor(target: Class<T?>, returnType: Class<R?>, name: String?, allowStatic: Boolean, vararg paramTypes: Class<*>?) : this(
        returnType,
        Companion.findMethod(target, name, allowStatic, paramTypes)
    )

    constructor(target: Class<T?>, returnType: Class<R?>, name: String?, vararg paramTypes: Class<*>?) : this(target, returnType, name, false, *paramTypes)

    init {
        method.setAccessible(true)
    }

    val isStatic: Boolean
        get() = Modifier.isStatic(method.getModifiers())

    fun invokeStatic(vararg args: Any?): R? {
        return invoke(null, *args)
    }

    fun invoke(target: T?, vararg args: Any?): R? {
        try {
            val result = method.invoke(target, *args)
            return returnType.cast(result)
        } catch (e: InvocationTargetException) {
            throw UncheckedException.throwAsUncheckedException((e.cause ?: e))
        } catch (e: Exception) {
            throw GradleException(String.format("Could not call %s.%s() on %s", method.getDeclaringClass().getSimpleName(), method.getName(), target), e)
        }
    }

    val parameterTypes: Array<Class<*>?>
        get() = method.getParameterTypes()

    override fun toString(): String {
        return method.toString()
    }

    companion object {
        /**
         * Locates the given method. Searches all methods, including private methods.
         */
        @Throws(NoSuchMethodException::class)
        fun <T, R> of(target: Class<T?>, returnType: Class<R?>, name: String?, vararg paramTypes: Class<*>?): JavaMethod<T?, R?> {
            return JavaMethod<T?, R?>(target, returnType, name, *paramTypes)
        }

        /**
         * Locates the given static method. Searches all methods, including private methods.
         */
        @Throws(NoSuchMethodException::class)
        fun <T, R> ofStatic(target: Class<T?>, returnType: Class<R?>, name: String?, vararg paramTypes: Class<*>?): JavaMethod<T?, R?> {
            return JavaMethod<T?, R?>(target, returnType, name, true, *paramTypes)
        }

        /**
         * Locates the given method. Searches all methods, including private methods.
         */
        @Throws(NoSuchMethodException::class)
        fun <T, R> of(target: T?, returnType: Class<R?>, name: String?, vararg paramTypes: Class<*>?): JavaMethod<T?, R?> {
            val targetClass = target!!.javaClass as Class<T?>
            return of<T?, R?>(targetClass, returnType, name, *paramTypes)
        }

        /**
         * Locates the given method. Searches all methods, including private methods.
         */
        @Throws(NoSuchMethodException::class)
        fun <T, R> of(returnType: Class<R?>, method: Method): JavaMethod<T?, R?> {
            return JavaMethod<T?, R?>(returnType, method)
        }

        private fun findMethod(target: Class<*>, name: String?, allowStatic: Boolean, paramTypes: Array<out Class<*>?>): Method {
            // First try to find a method from all public methods
            var method: Method? = findMethodFrom(target.getMethods(), name, allowStatic, paramTypes)
            if (method == null) {
                // Else search declared methods recursively
                method = findDeclaredMethod(target, name, allowStatic, paramTypes)
            }
            if (method != null) {
                return method
            }
            throw NoSuchMethodException(String.format("Could not find method %s(%s) on %s.", name, CollectionUtils.join(", ", paramTypes.asList()), target.getSimpleName()))
        }

        private fun findDeclaredMethod(origTarget: Class<*>?, name: String?, allowStatic: Boolean, paramTypes: Array<out Class<*>?>): Method? {
            var target = origTarget
            while (target != null) {
                val method: Method? = findMethodFrom(target.getDeclaredMethods(), name, allowStatic, paramTypes)
                if (method != null) {
                    return method
                }
                target = target.getSuperclass()
            }
            return null
        }

        private fun findMethodFrom(methods: Array<Method>, name: String?, allowStatic: Boolean, paramTypes: Array<out Class<*>?>): Method? {
            for (method in methods) {
                if (!allowStatic && Modifier.isStatic(method.getModifiers())) {
                    continue
                }
                if (method.getName() == name && method.getParameterTypes().contentEquals(paramTypes)) {
                    return method
                }
            }
            return null
        }
    }
}
