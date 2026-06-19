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
package org.gradle.internal.nativeintegration.jansi

import org.apache.commons.io.IOUtils
import org.gradle.internal.IoActions
import org.gradle.internal.nativeintegration.NativeIntegrationException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class JansiBootPathConfigurer {
    private val locator = JansiStorageLocator()

    /**
     * Attempts to find the Jansi library and copies it to a specified folder.
     * The copy operation happens only once. Sets the Jansi-related system property.
     *
     * This hackery is to prevent Jansi from creating a shared lib in a tmp dir which is deleted when
     * the Java process finishes. To avoid performance impacts caused by Jansi's default behavior the
     * library is proactively extracted into a known directory and reused by subsequent invocations.
     *
     * @param storageDir where to store the Jansi library
     */
    fun configure(storageDir: File?) {
        val jansiStorage = locator.locate(storageDir)

        if (jansiStorage != null) {
            val libFile = jansiStorage.targetLibFile
            libFile.getParentFile().mkdirs()

            if (!libFile.exists()) {
                val libraryInputStream = javaClass.getResourceAsStream(jansiStorage.jansiLibrary.resourcePath)
                try {
                    if (libraryInputStream != null) {
                        copyLibrary(libraryInputStream, libFile)
                    }
                } finally {
                    IoActions.closeQuietly(libraryInputStream)
                }
            }

            System.setProperty(JANSI_LIBRARY_PATH_SYS_PROP, libFile.getParent())
        }
    }

    private fun copyLibrary(lib: InputStream, libFile: File) {
        try {
            try {
                val outputStream = FileOutputStream(libFile)

                try {
                    IOUtils.copy(lib, outputStream)
                } finally {
                    outputStream.close()
                }
            } finally {
                lib.close()
            }
        } catch (e: IOException) {
            throw NativeIntegrationException(String.format("Could not create Jansi native library '%s'.", libFile), e)
        }
    }

    companion object {
        private const val JANSI_LIBRARY_PATH_SYS_PROP = "library.jansi.path"
    }
}
