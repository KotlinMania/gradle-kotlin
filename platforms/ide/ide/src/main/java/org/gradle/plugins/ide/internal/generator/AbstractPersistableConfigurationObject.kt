/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.internal.generator

import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

abstract class AbstractPersistableConfigurationObject : PersistableConfigurationObject {
    override fun load(inputFile: File) {
        try {
            val inputStream: InputStream = BufferedInputStream(FileInputStream(inputFile))
            try {
                load(inputStream)
            } finally {
                inputStream.close()
            }
        } catch (e: Exception) {
            throw throwAsUncheckedException(e)
        }
    }

    override fun loadDefaults() {
        try {
            val defaultResourceName = this.defaultResourceName
            val inputStream = javaClass.getResourceAsStream(defaultResourceName)
            checkNotNull(inputStream) {
                String.format(
                    "Failed to load default resource '%s' of persistable configuration object of type '%s' (resource not found)",
                    defaultResourceName,
                    javaClass.getName()
                )
            }
            try {
                load(inputStream)
            } finally {
                inputStream.close()
            }
        } catch (e: Exception) {
            throw throwAsUncheckedException(e)
        }
    }

    @Throws(Exception::class)
    abstract fun load(inputStream: InputStream?)

    override fun store(outputFile: File) {
        try {
            val outputStream: OutputStream = BufferedOutputStream(FileOutputStream(outputFile))
            try {
                store(outputStream)
            } finally {
                outputStream.close()
            }
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    abstract fun store(outputStream: OutputStream?)

    protected abstract val defaultResourceName: String
}
