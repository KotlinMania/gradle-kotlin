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
package org.gradle.internal.serialize

import org.gradle.internal.UncheckedException
import java.io.IOException
import java.io.InputStream
import java.util.function.Function

open class ExceptionReplacingObjectInputStream(inputSteam: InputStream?, classLoader: ClassLoader) : ClassLoaderObjectInputStream(inputSteam, classLoader) {
    var objectTransformer: java.util.function.Function<Any?, Any?>? = Function { o: Any? ->
        try {
            return@Function doResolveObject(o)
        } catch (e: IOException) {
            throw UncheckedException.throwAsUncheckedException(e)
        }
    }

    init {
        enableResolveObject(true)
    }

    val objectInputStreamCreator: Function<InputStream?, ExceptionReplacingObjectInputStream?>
        get() = Function { inputStream: InputStream? ->
            try {
                return@Function createNewInstance(inputStream)
            } catch (e: IOException) {
                throw UncheckedException.throwAsUncheckedException(e)
            }
        }

    @Throws(IOException::class)
    protected open fun createNewInstance(inputStream: InputStream?): ExceptionReplacingObjectInputStream? {
        return ExceptionReplacingObjectInputStream(inputStream, classLoader)
    }

    @Throws(IOException::class)
    override fun resolveObject(obj: Any?): Any? {
        return this.objectTransformer!!.apply(obj)
    }

    @Throws(IOException::class)
    protected fun doResolveObject(obj: Any?): Any? {
        if (obj is TopLevelExceptionPlaceholder) {
            return (obj as ExceptionPlaceholder).read(this.classNameTransformer, this.objectInputStreamCreator)
        }
        return obj
    }

    protected val classNameTransformer: Function<String?, Class<*>?>
        get() = Function { type: String? ->
            try {
                return@Function lookupClass(type)
            } catch (e: ClassNotFoundException) {
                throw UncheckedException.throwAsUncheckedException(e)
            }
        }

    @Throws(ClassNotFoundException::class)
    protected open fun lookupClass(type: String?): Class<*>? {
        return classLoader.loadClass(type)
    }
}
