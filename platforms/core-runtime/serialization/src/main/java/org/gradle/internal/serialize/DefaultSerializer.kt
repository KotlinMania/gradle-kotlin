/*
 * Copyright 2009 the original author or authors.
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

import com.google.common.base.Objects
import org.gradle.internal.Cast
import java.io.IOException
import java.io.ObjectOutputStream
import java.io.StreamCorruptedException

class DefaultSerializer<T> : AbstractSerializer<T?> {
    var classLoader: ClassLoader

    constructor() {
        classLoader = requireNotNull(javaClass.getClassLoader())
    }

    constructor(classLoader: ClassLoader?) {
        this.classLoader = classLoader ?: requireNotNull(javaClass.getClassLoader())
    }

    @Throws(Exception::class)
    override fun read(decoder: Decoder): T? {
        try {
            return Cast.uncheckedCast<T>(ClassLoaderObjectInputStream(decoder.inputStream, classLoader).readObject())
        } catch (e: StreamCorruptedException) {
            return null
        }
    }

    @Throws(IOException::class)
    override fun write(encoder: Encoder, value: T?) {
        val objectStr = ObjectOutputStream(encoder.outputStream)
        objectStr.writeObject(value)
        objectStr.flush()
    }

    override fun equals(obj: Any?): Boolean {
        if (!super.equals(obj)) {
            return false
        }

        val rhs = obj as DefaultSerializer<*>
        return Objects.equal(classLoader, rhs.classLoader)
    }

    override fun hashCode(): Int {
        return Objects.hashCode(super.hashCode(), classLoader)
    }
}
