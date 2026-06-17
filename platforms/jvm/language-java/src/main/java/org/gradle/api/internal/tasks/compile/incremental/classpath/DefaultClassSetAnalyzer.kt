/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.classpath

import com.google.common.collect.ImmutableSet
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassAnalysis
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassDependentsAccumulator
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysisData
import org.gradle.internal.FileUtils
import org.gradle.internal.IoActions
import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.StreamHasher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException

class DefaultClassSetAnalyzer(private val fileHasher: FileHasher, private val hasher: StreamHasher, private val analyzer: ClassDependenciesAnalyzer, private val fileOperations: FileOperations) :
    ClassSetAnalyzer {
    override fun analyzeClasspathEntry(classpathEntry: File): ClassSetAnalysisData? {
        return analyze(classpathEntry, true)
    }

    override fun analyzeOutputFolder(outputFolder: File): ClassSetAnalysisData? {
        return analyze(outputFolder, false)
    }

    private fun analyze(classSet: File, abiOnly: Boolean): ClassSetAnalysisData? {
        val accumulator = ClassDependentsAccumulator()
        try {
            visit(classSet, accumulator, abiOnly)
        } catch (e: Exception) {
            accumulator.fullRebuildNeeded(classSet.toString() + " could not be analyzed for incremental compilation. See the debug log for more details")
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Could not analyze " + classSet + " for incremental compilation", e)
            }
        }

        return accumulator.getAnalysis()
    }

    private fun visit(classpathEntry: File, accumulator: ClassDependentsAccumulator, abiOnly: Boolean) {
        if (FileUtils.hasExtension(classpathEntry, ".jar")) {
            fileOperations.zipTreeNoLocking(classpathEntry).visit(DefaultClassSetAnalyzer.JarEntryVisitor(accumulator, abiOnly))
        }
        if (classpathEntry.isDirectory()) {
            fileOperations.fileTree(classpathEntry).visit(DefaultClassSetAnalyzer.DirectoryEntryVisitor(accumulator, abiOnly))
        }
    }

    private abstract inner class EntryVisitor(private val accumulator: ClassDependentsAccumulator, private val abiOnly: Boolean) : FileVisitor {
        override fun visitDir(dirDetails: FileVisitDetails) {
        }

        override fun visitFile(fileDetails: FileVisitDetails) {
            if (!fileDetails.getName().endsWith(".class")) {
                return
            }

            val classFileHash = getHashCode(fileDetails)

            try {
                val analysis = maybeStripToAbi(analyzer.getClassAnalysis(classFileHash, fileDetails))
                accumulator.addClass(analysis, classFileHash)
            } catch (e: Exception) {
                accumulator.fullRebuildNeeded(fileDetails.getName() + " could not be analyzed for incremental compilation. See the debug log for more details")
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Could not analyze " + fileDetails.getName() + " for incremental compilation", e)
                }
            }
        }

        fun maybeStripToAbi(analysis: ClassAnalysis): ClassAnalysis {
            if (abiOnly) {
                return ClassAnalysis(analysis.getClassName(), ImmutableSet.of<String?>(), analysis.getAccessibleClassDependencies(), analysis.getDependencyToAllReason(), analysis.getConstants())
            } else {
                return analysis
            }
        }

        protected abstract fun getHashCode(fileDetails: FileVisitDetails?): HashCode?
    }

    private inner class JarEntryVisitor(accumulator: ClassDependentsAccumulator, abiOnly: Boolean) : EntryVisitor(accumulator, abiOnly) {
        override fun getHashCode(fileDetails: FileVisitDetails): HashCode {
            val inputStream = fileDetails.open()
            try {
                return hasher.hash(inputStream)
            } catch (e: IOException) {
                throw UncheckedIOException("Failed to hash " + fileDetails, e)
            } finally {
                IoActions.closeQuietly(inputStream)
            }
        }
    }

    private inner class DirectoryEntryVisitor(accumulator: ClassDependentsAccumulator, abiOnly: Boolean) : EntryVisitor(accumulator, abiOnly) {
        override fun getHashCode(fileDetails: FileVisitDetails): HashCode {
            return fileHasher.hash(fileDetails.getFile(), fileDetails.getSize(), fileDetails.getLastModified())
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultClassSetAnalyzer::class.java)
    }
}
