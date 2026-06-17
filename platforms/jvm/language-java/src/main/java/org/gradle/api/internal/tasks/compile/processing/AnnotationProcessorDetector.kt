/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.processing

import com.google.common.base.Enums
import com.google.common.base.Splitter
import com.google.common.collect.ImmutableList
import com.google.common.io.CharStreams
import com.google.common.io.Files
import com.google.common.io.LineProcessor
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType
import org.gradle.cache.internal.FileContentCache
import org.gradle.cache.internal.FileContentCacheFactory
import org.gradle.internal.FileUtils
import org.gradle.internal.serialize.ListSerializer
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.slf4j.Logger
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Inspects a classpath to find annotation processors contained in it. If several versions of the same annotation processor are found,
 * the first one is returned, mimicking the behavior of [java.util.ServiceLoader].
 */
@ServiceScope(Scope.BuildTree::class)
class AnnotationProcessorDetector(cacheFactory: FileContentCacheFactory, private val logger: Logger, private val logStackTraces: Boolean) {
    private val cache: FileContentCache<MutableList<AnnotationProcessorDeclaration>?>

    init {
        this.cache = cacheFactory.newCache<MutableList<AnnotationProcessorDeclaration?>?>(
            "annotation-processors", 20000, AnnotationProcessorDetector.ProcessorServiceLocator(), ListSerializer<AnnotationProcessorDeclaration?>(
                AnnotationProcessorDeclarationSerializer.Companion.INSTANCE
            )
        )
    }

    fun detectProcessors(processorPath: Iterable<File?>): MutableMap<String?, AnnotationProcessorDeclaration?> {
        val processors: MutableMap<String?, AnnotationProcessorDeclaration?> = LinkedHashMap<String?, AnnotationProcessorDeclaration?>()
        for (jarOrClassesDir in processorPath) {
            for (declaration in cache.get(jarOrClassesDir)!!) {
                val className = declaration.className
                if (!processors.containsKey(className)) {
                    processors.put(className, declaration)
                }
            }
        }
        return processors
    }

    /*
     * TODO once source compatibility is raised to 1.7, this should be rewritten using the java.nio.FileSystem API,
     * which can deal with jars and folders the same way instead of duplicating code.
     */
    private inner class ProcessorServiceLocator : FileContentCacheFactory.Calculator<MutableList<AnnotationProcessorDeclaration?>?> {
        override fun calculate(file: File, isRegularFile: Boolean): MutableList<AnnotationProcessorDeclaration?> {
            if (!isRegularFile) {
                return detectProcessorsInClassesDir(file)
            } else if (FileUtils.hasExtensionIgnoresCase(file.getName(), ".jar")) {
                return detectProcessorsInJar(file)
            }
            return mutableListOf<AnnotationProcessorDeclaration?>()
        }

        fun detectProcessorsInClassesDir(classesDir: File?): MutableList<AnnotationProcessorDeclaration?> {
            try {
                val processorClassNames = getProcessorClassNames(classesDir)
                try {
                    val processorTypes = getProcessorTypes(classesDir)
                    return toProcessorDeclarations(processorClassNames, processorTypes)
                } catch (e: Exception) {
                    logger.warn(
                        "Could not read annotation processor declarations from " + classesDir + ". Gradle will assume that all processors in this directory are non-incremental.",
                        if (logStackTraces) e else null
                    )
                    return toProcessorDeclarations(processorClassNames, mutableMapOf<String?, IncrementalAnnotationProcessorType?>())
                }
            } catch (e: Exception) {
                logger.warn(
                    "Could not read annotation processor declarations from " + classesDir + ". Gradle will assume that this directory contains no annotation processors.",
                    if (logStackTraces) e else null
                )
                return mutableListOf<AnnotationProcessorDeclaration?>()
            }
        }

        @Throws(IOException::class)
        fun getProcessorClassNames(classesDir: File?): MutableList<String> {
            val processorDeclaration = File(classesDir, PROCESSOR_DECLARATION)
            if (!processorDeclaration.isFile()) {
                return mutableListOf<String?>()
            }
            return readLines(processorDeclaration)
        }

        @Throws(IOException::class)
        fun getProcessorTypes(classesDir: File?): MutableMap<String?, IncrementalAnnotationProcessorType?> {
            val incrementalProcessorDeclaration = File(classesDir, INCREMENTAL_PROCESSOR_DECLARATION)
            if (!incrementalProcessorDeclaration.isFile()) {
                return mutableMapOf<String?, IncrementalAnnotationProcessorType?>()
            }
            val lines = readLines(incrementalProcessorDeclaration)
            return parseIncrementalProcessors(lines)
        }

        @Throws(IOException::class)
        fun readLines(file: File): MutableList<String> {
            return Files.asCharSource(file, StandardCharsets.UTF_8).readLines<MutableList<String?>>(ProcessorServiceLocator.MetadataLineProcessor())
        }

        fun detectProcessorsInJar(jar: File): MutableList<AnnotationProcessorDeclaration?> {
            try {
                val zipFile = ZipFile(jar)
                try {
                    val processorClassNames = getProcessorClassNames(zipFile)
                    try {
                        val processorTypes = getProcessorTypes(zipFile)
                        return toProcessorDeclarations(processorClassNames, processorTypes)
                    } catch (e: Exception) {
                        logger.warn(
                            "Could not read annotation processor declarations from " + jar + ". Gradle will assume that all processors in this jar are non-incremental.",
                            if (logStackTraces) e else null
                        )
                        return toProcessorDeclarations(processorClassNames, mutableMapOf<String?, IncrementalAnnotationProcessorType?>())
                    }
                } finally {
                    zipFile.close()
                }
            } catch (e: Exception) {
                logger.warn("Could not read annotation processor declarations from " + jar + ". Gradle will assume that this jar contains no annotation processors.", if (logStackTraces) e else null)
                return mutableListOf<AnnotationProcessorDeclaration?>()
            }
        }

        @Throws(IOException::class)
        fun getProcessorClassNames(zipFile: ZipFile): MutableList<String> {
            val processorDeclaration = zipFile.getEntry(PROCESSOR_DECLARATION)
            if (processorDeclaration == null) {
                return mutableListOf<String?>()
            }
            return readLines(zipFile, processorDeclaration)
        }

        @Throws(IOException::class)
        fun getProcessorTypes(zipFile: ZipFile): MutableMap<String?, IncrementalAnnotationProcessorType?> {
            val incrementalProcessorDeclaration = zipFile.getEntry(INCREMENTAL_PROCESSOR_DECLARATION)
            if (incrementalProcessorDeclaration == null) {
                return mutableMapOf<String?, IncrementalAnnotationProcessorType?>()
            }
            val lines = readLines(zipFile, incrementalProcessorDeclaration)
            return parseIncrementalProcessors(lines)
        }

        @Throws(IOException::class)
        fun readLines(zipFile: ZipFile, zipEntry: ZipArchiveEntry?): MutableList<String> {
            val `in` = zipFile.getInputStream(zipEntry)
            try {
                return CharStreams.readLines<MutableList<String?>>(InputStreamReader(`in`, StandardCharsets.UTF_8), ProcessorServiceLocator.MetadataLineProcessor())
            } finally {
                `in`.close()
            }
        }

        fun parseIncrementalProcessors(lines: MutableList<String>): MutableMap<String?, IncrementalAnnotationProcessorType?> {
            val types: MutableMap<String?, IncrementalAnnotationProcessorType?> = HashMap<String?, IncrementalAnnotationProcessorType?>()
            for (line in lines) {
                val parts: MutableList<String?> = Splitter.on(',').splitToList(line)
                val type = parseProcessorType(parts)
                types.put(parts.get(0), type)
            }
            return types
        }

        fun parseProcessorType(parts: MutableList<String?>): IncrementalAnnotationProcessorType {
            return Enums.getIfPresent<IncrementalAnnotationProcessorType>(IncrementalAnnotationProcessorType::class.java, parts.get(1)!!.uppercase()).or(IncrementalAnnotationProcessorType.UNKNOWN)
        }

        fun toProcessorDeclarations(processorNames: MutableList<String>, processorTypes: MutableMap<String?, IncrementalAnnotationProcessorType?>): MutableList<AnnotationProcessorDeclaration?> {
            if (processorNames.isEmpty()) {
                return mutableListOf<AnnotationProcessorDeclaration?>()
            }
            val processors = ImmutableList.builder<AnnotationProcessorDeclaration?>()
            for (name in processorNames) {
                var type = processorTypes.get(name)
                type = if (type != null) type else IncrementalAnnotationProcessorType.UNKNOWN
                processors.add(AnnotationProcessorDeclaration(name, type))
            }
            return processors.build()
        }

        private inner class MetadataLineProcessor : LineProcessor<MutableList<String?>?> {
            private val lines: MutableList<String?> = ArrayList<String?>()

            override fun processLine(line: String): Boolean {
                var line = line
                val commentStart = line.indexOf('#')
                if (commentStart >= 0) {
                    line = line.substring(0, commentStart)
                }
                line = line.trim { it <= ' ' }
                if (!line.isEmpty()) {
                    lines.add(line)
                }
                return true
            }

            override fun getResult(): MutableList<String?> {
                return lines
            }
        }
    }

    companion object {
        const val PROCESSOR_DECLARATION: String = "META-INF/services/javax.annotation.processing.Processor"
        const val INCREMENTAL_PROCESSOR_DECLARATION: String = "META-INF/gradle/incremental.annotation.processors"
    }
}
