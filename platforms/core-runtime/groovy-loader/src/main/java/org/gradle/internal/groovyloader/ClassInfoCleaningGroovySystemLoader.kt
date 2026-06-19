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
package org.gradle.internal.groovyloader

import org.gradle.api.GradleException
import org.gradle.internal.Cast
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Objects

class ClassInfoCleaningGroovySystemLoader(private val leakingLoader: ClassLoader) : GroovySystemLoader {
    private val removeFromGlobalClassValue: Method
    private val globalClassSetIteratorMethod: Method
    private val globalClassValue: Any
    private val globalClassSetItems: Any
    private var clazzField: Field? = null
    private var classRefField: Field? = null

    init {
        // this work has to be done before classes are loaded, otherwise there are risks that
        // the PermGen space is full before we create the reflection methods
        val classInfoClass = leakingLoader.loadClass("org.codehaus.groovy.reflection.ClassInfo")
        val globalClassValueField = classInfoClass.getDeclaredField("globalClassValue")
        globalClassValueField.setAccessible(true)
        globalClassValue = globalClassValueField.get(null)
        removeFromGlobalClassValue = globalClassValueField.getType().getDeclaredMethod("remove", Class::class.java)
        removeFromGlobalClassValue.setAccessible(true)

        var globalClassSetField = classInfoClass.getDeclaredField("globalClassSet")
        globalClassSetField.setAccessible(true)
        val globalClassSet = globalClassSetField.get(null)
        globalClassSetField = globalClassSet.javaClass.getDeclaredField("items")
        globalClassSetField.setAccessible(true)
        globalClassSetItems = globalClassSetField.get(globalClassSet)
        globalClassSetIteratorMethod = globalClassSetItems.javaClass.getDeclaredMethod("iterator")

        try {
            classRefField = classInfoClass.getDeclaredField("classRef")
            classRefField!!.setAccessible(true)
        } catch (e: Exception) {
            clazzField = classInfoClass.getDeclaredField("klazz")
            clazzField!!.setAccessible(true)
        }
    }

    override fun shutdown() {
        check(leakingLoader !== javaClass.getClassLoader()) { "Cannot shut down the main Groovy loader." }
        try {
            val it = globalClassSetIterator()
            while (it.hasNext()) {
                val classInfo = it.next()
                if (classInfo != null) {
                    val clazz = getClazz(classInfo)
                    if (clazz != null) {
                        removeFromGlobalClassValue.invoke(globalClassValue, clazz)
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Removed ClassInfo from {} loaded by {}", clazz.getName(), clazz.getClassLoader())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw GradleException("Could not shut down the Groovy system for " + leakingLoader, e)
        }
    }

    override fun discardTypesFrom(classLoader: ClassLoader) {
        check(classLoader !== leakingLoader) { "Cannot remove own types from Groovy loader." }
        try {
            val it = globalClassSetIterator()
            while (it.hasNext()) {
                val classInfo = it.next()
                if (classInfo != null) {
                    val clazz = getClazz(classInfo)
                    if (clazz != null && clazz.getClassLoader() === classLoader) {
                        removeFromGlobalClassValue.invoke(globalClassValue, clazz)
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Removed ClassInfo from {} loaded by {}", clazz.getName(), clazz.getClassLoader())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw GradleException("Could not remove types for ClassLoader " + classLoader + " from the Groovy system " + leakingLoader, e)
        }
    }

    @Throws(IllegalAccessException::class)
    private fun getClazz(classInfo: Any): Class<*>? {
        val classRefField = classRefField
        if (classRefField != null) {
            return Cast.uncheckedNonnullCast<WeakReference<Class<*>>>(classRefField.get(classInfo)).get()
        } else {
            return requireNotNull(clazzField).get(classInfo) as Class<*>?
        }
    }

    @Throws(IllegalAccessException::class, InvocationTargetException::class)
    private fun globalClassSetIterator(): MutableIterator<*> {
        return (globalClassSetIteratorMethod.invoke(globalClassSetItems) as kotlin.collections.MutableIterator<*>?)!!
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(ClassInfoCleaningGroovySystemLoader::class.java)
    }
}
