/*
 * Copyright 2023 the original author or authors.
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

import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Utility class which operates directly on Java class files.
 */
object JavaClassUtil {
    private const val MAGIC_BYTES = -0x35014542

    /**
     * Get the class file major version from the provided `file`.
     *
     * @throws IOException If the file does not exist or is malformed.
     */
    @Throws(IOException::class)
    fun getClassMajorVersion(file: File): Int {
        return getClassMajorVersion(FileInputStream(file))
    }

    /**
     * Get the class file major version from the provided `javaClass`
     *
     * @throws IOException If there is an error reading the class file contents.
     */
    @Throws(IOException::class)
    fun getClassMajorVersion(javaClass: Class<*>): Int {
        return getClassMajorVersion(javaClass.getName(), javaClass.getClassLoader())!!
    }

    /**
     * Get the class file major version from the class with the given `name` by loading it
     * from the provided `loader`.
     *
     * @return null if the class cannot be loaded.
     *
     * @throws IOException If there is an error reading the class file contents.
     */
    @Throws(IOException::class)
    fun getClassMajorVersion(name: String, loader: ClassLoader): Int? {
        val `is` = loader.getResourceAsStream(name.replace('.', '/') + ".class")
        if (`is` == null) {
            return null
        }
        return getClassMajorVersion(`is`)
    }

    /**
     * Get the class file major version from class file data provided by `is`.
     * This method will close the provided [InputStream].
     *
     * @throws IOException If the stream contents are malformed.
     */
    @Throws(IOException::class)
    fun getClassMajorVersion(`is`: InputStream): Int {
        val data = DataInputStream(`is`)
        try {
            if (MAGIC_BYTES != data.readInt()) {
                throw IOException("Invalid .class file header")
            }
            data.readUnsignedShort() // Minor
            return data.readUnsignedShort() // Major
        } finally {
            data.close()
        }
    }
}
