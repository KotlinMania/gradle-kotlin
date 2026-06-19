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

import org.gradle.internal.serialize.ExceptionReplacingObjectInputStream
import java.io.IOException
import java.io.InputStream
import java.io.ObjectStreamClass
import java.lang.reflect.Proxy

internal class PayloadSerializerObjectInputStream(
    inputStream: InputStream?,
    classLoader: ClassLoader,
    private val classLoaderDetails: MutableMap<Short, ClassLoaderDetails>,
    private val map: DeserializeMap
) : ExceptionReplacingObjectInputStream(inputStream, classLoader) {
    @Throws(IOException::class)
    override fun createNewInstance(inputStream: InputStream?): ExceptionReplacingObjectInputStream {
        return PayloadSerializerObjectInputStream(inputStream, classLoader, classLoaderDetails, map)
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    override fun readClassDescriptor(): ObjectStreamClass {
        val aClass = readClass()
        val descriptor = ObjectStreamClass.lookupAny(aClass)
        if (descriptor == null) {
            throw ClassNotFoundException(aClass.getName())
        }
        return descriptor
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    protected override fun resolveClass(desc: ObjectStreamClass): Class<*> {
        return desc.forClass() ?: throw ClassNotFoundException(desc.name)
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    private fun readClass(): Class<*> {
        val id = readShort()
        val className = readUTF()
        if (id.toInt() == PayloadSerializerObjectOutputStream.Companion.SAME_CLASSLOADER_TOKEN) {
            return super.lookupClass(className)!!
        }
        val classLoader = classLoaderDetails[id] ?: throw ClassNotFoundException(className)
        return map.resolveClass(classLoader, className)
    }

    @Throws(IOException::class, ClassNotFoundException::class)
    override fun resolveProxyClass(interfaces: Array<String?>?): Class<*> {
        val count = readInt()
        val actualInterfaces = Array(count) { readClass() }
        @Suppress("deprecation") return Proxy.getProxyClass(actualInterfaces[0].classLoader, *actualInterfaces)
    }

    @Throws(ClassNotFoundException::class)
    override fun lookupClass(type: String): Class<*> {
        try {
            return super.lookupClass(type)!!
        } catch (e: ClassNotFoundException) {
            // lookup class in all classloaders
            for (details in classLoaderDetails.values) {
                try {
                    return map.resolveClass(details, type)
                } catch (ignored: ClassNotFoundException) {
                    // ignore
                }
            }
            throw e
        }
    }
}
