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
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.util.function.Function

open class ExceptionReplacingObjectOutputStream(outputSteam: OutputStream?) : ObjectOutputStream(outputSteam) {
    var objectTransformer: java.util.function.Function<Any?, Any?>? = Function { obj: Any? ->
        try {
            return@Function doReplaceObject(obj)
        } catch (e: IOException) {
            throw UncheckedException.throwAsUncheckedException(e)
        }
    }

    init {
        enableReplaceObject(true)
    }

    val objectOutputStreamCreator: Function<OutputStream?, ExceptionReplacingObjectOutputStream?>
        get() = Function { outputStream: OutputStream? ->
            try {
                return@Function createNewInstance(outputStream)
            } catch (e: IOException) {
                throw UncheckedException.throwAsUncheckedException(e)
            }
        }

    @Throws(IOException::class)
    protected open fun createNewInstance(outputStream: OutputStream?): ExceptionReplacingObjectOutputStream? {
        return ExceptionReplacingObjectOutputStream(outputStream)
    }

    @Throws(IOException::class)
    override fun replaceObject(obj: Any?): Any? {
        return this.objectTransformer!!.apply(obj)
    }

    @Throws(IOException::class)
    protected fun doReplaceObject(obj: Any?): Any? {
        if (obj is Throwable) {
            return TopLevelExceptionPlaceholder(obj, this.objectOutputStreamCreator)
        }
        return obj
    }
}
