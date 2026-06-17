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
package org.gradle.api.internal.tasks.testing.detection

import org.apache.commons.lang3.text.StrBuilder
import org.gradle.api.GradleException
import org.gradle.api.internal.file.temp.DefaultTemporaryFileProvider
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.internal.Factory
import org.gradle.util.internal.JarUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.TreeSet

/**
 * This class manages class file extraction from library jar files.
 */
class ClassFileExtractionManager(tempDirFactory: Factory<File?>) {
    private val packageJarFilesMappings: MutableMap<String?, MutableSet<File?>?>
    private val extractedJarClasses: MutableMap<String?, File?>
    private val unextractableClasses: MutableSet<String?>
    private val tempDirProvider: TemporaryFileProvider

    init {
        checkNotNull(tempDirFactory)
        tempDirProvider = DefaultTemporaryFileProvider(tempDirFactory)
        packageJarFilesMappings = HashMap<String?, MutableSet<File?>?>()
        extractedJarClasses = HashMap<String?, File?>()
        unextractableClasses = TreeSet<String?>()
    }

    /**
     * Add all packages found in the jar file to the package &lt;&gt; jar(s) index.
     *
     * @param libraryJar Jar file to add to the index.
     */
    fun addLibraryJar(libraryJar: File?) {
        JarFilePackageLister().listJarPackages(libraryJar!!, object : JarFilePackageListener {
            override fun receivePackage(packageName: String?) {
                var jarFiles: MutableSet<File?>? = packageJarFilesMappings.get(packageName)
                if (jarFiles == null) {
                    jarFiles = TreeSet<File?>()
                    packageJarFilesMappings.put(packageName, jarFiles)
                }
                jarFiles.add(libraryJar)
            }
        })
    }

    /**
     * Retrieve the file that contains the extracted class file.
     *
     *
     *
     * This method will extract the class file if it is
     * not extracted yet. Extracted class files are deleted on exit of the Gradle process. The same class is only
     * extracted once.
     *
     *
     * @param className Name of the class to extract.
     * @return File that contains the extracted class file.
     */
    fun getLibraryClassFile(className: String): File? {
        if (unextractableClasses.contains(className)) {
            return null
        } else {
            if (!extractedJarClasses.containsKey(className)) {
                if (!extractClassFile(className)) {
                    unextractableClasses.add(className)
                }
            }

            return extractedJarClasses.get(className)
        }
    }

    private fun extractClassFile(className: String): Boolean {
        var classFileExtracted = false

        val extractedClassFile: File? = tempFile()
        val classFileName = StrBuilder().append(className).append(".class").toString()
        val classNamePackage: String? = classNamePackage(className)
        val packageJarFiles: MutableSet<File?>? = packageJarFilesMappings.get(classNamePackage)

        var classFileSourceJar: File? = null

        if (packageJarFiles != null && !packageJarFiles.isEmpty()) {
            val packageJarFilesIt = packageJarFiles.iterator()

            while (!classFileExtracted && packageJarFilesIt.hasNext()) {
                val jarFile = packageJarFilesIt.next()

                try {
                    classFileExtracted = JarUtil.extractZipEntry(jarFile, classFileName, extractedClassFile)

                    if (classFileExtracted) {
                        classFileSourceJar = jarFile
                    }
                } catch (e: IOException) {
                    throw GradleException("failed to extract class file from jar (" + jarFile + ")", e)
                }
            }

            if (classFileExtracted) {
                LOGGER.debug("extracted class {} from {}", className, classFileSourceJar!!.getName())

                extractedJarClasses.put(className, extractedClassFile)
            }
        } // super class not on the classpath - unable to scan parent class


        return classFileExtracted
    }

    private fun classNamePackage(className: String): String? {
        val lastSlashIndex = className.lastIndexOf('/')

        if (lastSlashIndex == -1) {
            return null // class in root package - should not happen
        } else {
            return className.substring(0, lastSlashIndex + 1)
        }
    }

    private fun tempFile(): File? {
        return tempDirProvider.createTemporaryFile("jar_extract_", "_tmp") // Could throw UncheckedIOException
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(ClassFileExtractionManager::class.java)
    }
}
