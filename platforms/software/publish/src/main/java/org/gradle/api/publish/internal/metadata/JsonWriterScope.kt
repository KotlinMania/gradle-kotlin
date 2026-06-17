/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.publish.internal.metadata

import com.google.gson.stream.JsonWriter
import java.io.IOException

/**
 * Simplifies the task of writing to a JsonWriter.
 */
internal abstract class JsonWriterScope protected constructor(private val jsonWriter: JsonWriter) {
    protected interface Contents {
        @Throws(IOException::class)
        fun write()
    }

    @Throws(IOException::class)
    protected fun writeArray(name: String?, elements: MutableList<String?>) {
        writeArray(name, JsonWriterScope.Contents {
            for (element in elements) {
                jsonWriter.value(element)
            }
        })
    }

    @Throws(IOException::class)
    protected fun writeArray(name: String?, contents: Contents) {
        jsonWriter.name(name)
        writeArray(contents)
    }

    @Throws(IOException::class)
    protected fun writeArray(contents: Contents) {
        jsonWriter.beginArray()
        contents.write()
        endArray()
    }

    @Throws(IOException::class)
    protected fun beginArray(name: String?) {
        jsonWriter.name(name)
        jsonWriter.beginArray()
    }

    @Throws(IOException::class)
    protected fun endArray() {
        jsonWriter.endArray()
    }

    @Throws(IOException::class)
    protected fun writeObject(name: String?, contents: Contents) {
        jsonWriter.name(name)
        writeObject(contents)
    }

    @Throws(IOException::class)
    protected fun writeObject(contents: Contents) {
        jsonWriter.beginObject()
        contents.write()
        jsonWriter.endObject()
    }

    @Throws(IOException::class)
    protected fun write(name: String?, number: Number?) {
        jsonWriter.name(name).value(number)
    }

    @Throws(IOException::class)
    protected fun write(name: String?, length: Long) {
        jsonWriter.name(name).value(length)
    }

    @Throws(IOException::class)
    protected fun write(name: String?, value: Boolean) {
        jsonWriter.name(name).value(value)
    }

    @Throws(IOException::class)
    protected fun write(name: String?, value: String?) {
        jsonWriter.name(name).value(value)
    }
}
