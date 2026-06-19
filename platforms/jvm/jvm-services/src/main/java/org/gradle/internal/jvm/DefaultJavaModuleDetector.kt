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
package org.gradle.internal.jvm

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.cache.internal.FileContentCache
import org.gradle.cache.internal.FileContentCacheFactory
import org.gradle.internal.serialize.BaseSerializerFactory
import java.io.File
import java.io.IOException
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.regex.Pattern

class DefaultJavaModuleDetector(cacheFactory: FileContentCacheFactory, private val fileCollectionFactory: FileCollectionFactory) : JavaModuleDetector {
    private val cache: FileContentCache<Boolean>

    init {
        this.cache = cacheFactory.newCache<Boolean>("java-modules", 20000, ModuleInfoLocator(), BaseSerializerFactory().getSerializerFor<Boolean>(Boolean::class.java))
    }

    public override fun inferClasspath(inferModulePath: Boolean, classpath: MutableCollection<File?>): FileCollection {
        return inferClasspath(inferModulePath, fileCollectionFactory.fixed(classpath.filterNotNull()))
    }

    public override fun inferClasspath(inferModulePath: Boolean, classpath: FileCollection?): FileCollection {
        if (classpath == null) {
            return FileCollectionFactory.empty()
        }
        if (!inferModulePath) {
            return classpath
        }
        return classpath.filter(object : org.gradle.api.specs.Spec<File> {
            override fun isSatisfiedBy(file: File): Boolean {
                return isNotModule(file)
            }
        })
    }

    public override fun inferModulePath(inferModulePath: Boolean, classpath: MutableCollection<File?>): FileCollection {
        return inferModulePath(inferModulePath, fileCollectionFactory.fixed(classpath.filterNotNull()))
    }

    public override fun inferModulePath(inferModulePath: Boolean, classpath: FileCollection?): FileCollection {
        if (classpath == null) {
            return FileCollectionFactory.empty()
        }
        if (!inferModulePath) {
            return FileCollectionFactory.empty()
        }
        return classpath.filter(object : org.gradle.api.specs.Spec<File> {
            override fun isSatisfiedBy(file: File): Boolean {
                return isModule(file)
            }
        })
    }

    public override fun isModule(inferModulePath: Boolean, files: FileCollection): Boolean {
        if (!inferModulePath) {
            return false
        }
        for (file in files.getFiles()) {
            if (isModule(file)) {
                return true
            }
        }
        return false
    }

    public override fun isModule(inferModulePath: Boolean, file: File): Boolean {
        if (!inferModulePath) {
            return false
        }
        return isModule(file)
    }

    private fun isModule(file: File): Boolean {
        if (!file.exists()) {
            return false
        }
        return cache.get(file)!!
    }

    private fun isNotModule(file: File): Boolean {
        if (!file.exists()) {
            return false
        }
        return !isModule(file)
    }

    private class ModuleInfoLocator : FileContentCacheFactory.Calculator<Boolean?> {
        override fun calculate(file: File, isRegularFile: Boolean): Boolean {
            if (isRegularFile) {
                return isJarFile(file) && isModuleJar(file)
            } else {
                return isModuleFolder(file)
            }
        }

        companion object {
            private fun isJarFile(file: File): Boolean {
                return file.getName().endsWith(".jar")
            }

            private fun isModuleFolder(folder: File): Boolean {
                return File(folder, MODULE_INFO_CLASS_FILE).exists()
            }

            private fun isModuleJar(jarFile: File): Boolean {
                try {
                    JarFile(jarFile).use { openedJar ->
                        if (containsAutomaticModuleName(openedJar)) {
                            return true
                        }
                        val isMultiReleaseJar: Boolean = containsMultiReleaseJarEntry(openedJar)
                        val jarEntries = openedJar.entries()
                        while (jarEntries.hasMoreElements()) {
                            val entry = jarEntries.nextElement()
                            if (MODULE_INFO_CLASS_FILE == entry.getName()) {
                                return true
                            }
                            if (isMultiReleaseJar && MODULE_INFO_CLASS_MRJAR_PATH.matcher(entry.getName()).matches()) {
                                return true
                            }
                        }
                    }
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
                return false
            }

            @Throws(IOException::class)
            private fun containsMultiReleaseJarEntry(jarFile: JarFile): Boolean {
                val manifest = jarFile.getManifest()
                return manifest != null && manifest.getMainAttributes().getValue(MULTI_RELEASE_ATTRIBUTE).toBoolean()
            }

            @Throws(IOException::class)
            private fun containsAutomaticModuleName(jarFile: JarFile): Boolean {
                return getAutomaticModuleName(jarFile.getManifest()) != null
            }

            private fun getAutomaticModuleName(manifest: Manifest?): String? {
                if (manifest == null) {
                    return null
                }
                return manifest.getMainAttributes().getValue(AUTOMATIC_MODULE_NAME_ATTRIBUTE)
            }
        }
    }

    companion object {
        private const val MODULE_INFO_CLASS_FILE = "module-info.class"
        private const val AUTOMATIC_MODULE_NAME_ATTRIBUTE = "Automatic-Module-Name"
        private const val MULTI_RELEASE_ATTRIBUTE = "Multi-Release"

        private val MODULE_INFO_CLASS_MRJAR_PATH: Pattern = Pattern.compile("META-INF/versions/\\d+/module-info.class")
    }
}
