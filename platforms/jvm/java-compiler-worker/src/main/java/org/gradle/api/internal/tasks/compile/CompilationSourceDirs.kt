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
package org.gradle.api.internal.tasks.compile

import com.google.common.annotations.VisibleForTesting
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.internal.RelativePathUtil
import org.jspecify.annotations.NullMarked
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Optional

/**
 * Relativizes paths relative to a set of source directories in order to create a platform-independent mapping
 * from source file to class file.
 */
@NullMarked
class CompilationSourceDirs {
    private val sourceRoots: MutableList<File>

    constructor(spec: JavaCompileSpec) {
        this.sourceRoots = ArrayList<File>(spec.sourceRoots)
        val headerOutputDirectory = spec.getCompileOptions().getHeaderOutputDirectory()
        if (headerOutputDirectory != null) {
            sourceRoots.add(headerOutputDirectory)
        }
        val generatedSourcesDirectory = spec.getCompileOptions().getAnnotationProcessorGeneratedSourcesDirectory()
        if (generatedSourcesDirectory != null) {
            sourceRoots.add(generatedSourcesDirectory)
        }
    }

    @VisibleForTesting
    internal constructor(sourceRoots: MutableList<File>) {
        this.sourceRoots = sourceRoots
    }

    /**
     * Calculate the relative path to the source root.
     */
    fun relativize(sourceFile: File): Optional<String> {
        return sourceRoots.stream()
            .filter { sourceDir: File? -> sourceFile.getAbsolutePath().startsWith(sourceDir!!.getAbsolutePath()) }
            .map<String> { sourceDir: File? -> RelativePathUtil.relativePath(sourceDir, sourceFile) }
            .filter { relativePath: String? -> !relativePath!!.startsWith("..") }
            .findFirst()
    }

    private class SourceRoots : FileCollectionStructureVisitor {
        private var canInferSourceRoots = true
        private val sourceRoots: MutableList<File> = ArrayList<File>()

        override fun visitCollection(source: FileCollectionInternal.Source, contents: Iterable<File>) {
            cannotInferSourceRoots(contents)
        }

        override fun visitFileTreeBackedByFile(file: File, fileTree: FileTreeInternal, sourceTree: FileSystemMirroringFileTree) {
            cannotInferSourceRoots(fileTree)
        }

        override fun visitFileTree(root: File, patterns: PatternSet, fileTree: FileTreeInternal) {
            // We need to add missing files as source roots, since the package name for deleted files provided by InputChanges also need to be determined.
            if (!root.exists() || root.isDirectory()) {
                sourceRoots.add(root)
            } else {
                cannotInferSourceRoots("file '" + root + "'")
            }
        }

        fun cannotInferSourceRoots(fileCollection: Any) {
            canInferSourceRoots = false
            LOG.info("Cannot infer source root(s) for source `{}`. Supported types are `File` (directories only), `DirectoryTree` and `SourceDirectorySet`.", fileCollection)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(CompilationSourceDirs::class.java)
        @JvmStatic
        fun inferSourceRoots(sources: FileTreeInternal): MutableList<File> {
            val visitor = SourceRoots()
            sources.visitStructure(visitor)
            return if (visitor.canInferSourceRoots) visitor.sourceRoots else mutableListOf<File>()
        }
    }
}
