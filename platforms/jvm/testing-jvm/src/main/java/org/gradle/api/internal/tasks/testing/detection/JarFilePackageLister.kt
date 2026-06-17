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
package org.gradle.api.internal.tasks.testing.detection

import org.gradle.api.GradleException
import org.gradle.internal.FileUtils
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class JarFilePackageLister {
    fun listJarPackages(jarFile: File, listener: JarFilePackageListener) {
        requireNotNull(jarFile) { "jarFile is null!" }

        val jarFileAbsolutePath = jarFile.getAbsolutePath()

        require(jarFile.exists()) { "jarFile doesn't exists! (" + jarFileAbsolutePath + ")" }
        require(jarFile.isFile()) { "jarFile is not a file! (" + jarFileAbsolutePath + ")" }
        require(FileUtils.hasExtension(jarFile, ".jar")) { "jarFile is not a jarFile! (" + jarFileAbsolutePath + ")" }

        try {
            val zipFile = ZipFile(jarFile)
            try {
                val zipFileEntries = zipFile.entries()

                while (zipFileEntries.hasMoreElements()) {
                    val zipEntry: ZipEntry = zipFileEntries.nextElement()
                    val entryName = zipEntry.getName()
                    val packageName = if (zipEntry.isDirectory()) entryName else entryName.substring(0, entryName.lastIndexOf("/") + 1)

                    if (!packageName.startsWith("META-INF")) {
                        listener.receivePackage(packageName)
                    }
                }
            } finally {
                zipFile.close()
            }
        } catch (e: IOException) {
            throw GradleException("failed to scan jar file for packages (" + jarFileAbsolutePath + ")", e)
        }
    }
}
