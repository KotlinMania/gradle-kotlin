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
package org.gradle.tooling.internal.provider.serialization

import org.gradle.internal.serialize.ExceptionReplacingObjectOutputStream
import org.gradle.internal.serialize.TopLevelExceptionPlaceholder
import java.io.IOException
import java.io.ObjectStreamClass
import java.io.OutputStream

internal class PayloadSerializerObjectOutputStream(outputStream: OutputStream?, private val map: SerializeMap) : ExceptionReplacingObjectOutputStream(outputStream) {
    @Throws(IOException::class)
    override fun createNewInstance(outputStream: OutputStream?): ExceptionReplacingObjectOutputStream {
        return PayloadSerializerObjectOutputStream(outputStream, map)
    }

    @Throws(IOException::class)
    override fun writeClassDescriptor(desc: ObjectStreamClass) {
        val targetClass = desc.forClass()
        writeClass(targetClass)
    }

    @Throws(IOException::class)
    override fun annotateProxyClass(cl: Class<*>) {
        writeInt(cl.getInterfaces().size)
        for (type in cl.getInterfaces()) {
            writeClass(type)
        }
    }

    @Throws(IOException::class)
    private fun writeClass(targetClass: Class<*>) {
        writeClassLoader(targetClass)
        writeUTF(targetClass.getName())
    }

    @Throws(IOException::class)
    private fun writeClassLoader(targetClass: Class<*>) {
        if (TopLevelExceptionPlaceholder::class.java.getPackage() == targetClass.getPackage()) {
            writeShort(SAME_CLASSLOADER_TOKEN)
        } else {
            writeShort(map.visitClass(targetClass).toInt())
        }
    }

    companion object {
        const val SAME_CLASSLOADER_TOKEN: Int = 0
    }
}
