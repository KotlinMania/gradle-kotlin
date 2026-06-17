/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.language.nativeplatform.internal.incremental

import com.google.common.collect.ImmutableSet
import org.gradle.internal.hash.HashCode
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.language.nativeplatform.internal.IncludeDirectives
import org.gradle.language.nativeplatform.internal.IncludeType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.Boolean
import java.util.function.Function

class IncrementalCompileFilesFactory(
    private val initialIncludeDirectives: IncludeDirectives?,
    private val sourceIncludesParser: SourceIncludesParser,
    private val sourceIncludesResolver: SourceIncludesResolver,
    private val fileSystemAccess: FileSystemAccess
) {
    private val ignoreUnresolvedHeadersInDependencies: Boolean

    init {
        this.ignoreUnresolvedHeadersInDependencies = Boolean.getBoolean(IGNORE_UNRESOLVED_HEADERS_IN_DEPENDENCIES_PROPERTY_NAME)
    }

    fun files(previousCompileState: CompilationState?): IncrementalCompileSourceProcessor {
        return IncrementalCompileFilesFactory.DefaultIncrementalCompileSourceProcessor(previousCompileState)
    }

    private inner class DefaultIncrementalCompileSourceProcessor(previousCompileState: CompilationState?) : IncrementalCompileSourceProcessor {
        private val previous: CompilationState
        private val current = BuildableCompilationState()
        private val toRecompile: MutableList<File?> = ArrayList<File?>()
        private val existingHeaders: MutableSet<File?> = HashSet<File?>()
        private val visitedFiles: MutableMap<File?, FileDetails?> = HashMap<File?, FileDetails?>()
        private var hasUnresolvedHeaders = false

        init {
            this.previous = if (previousCompileState == null) CompilationState() else previousCompileState
        }

        override fun getResult(): IncrementalCompilation {
            return DefaultIncrementalCompilation(current.snapshot(), toRecompile, this.removedSources, existingHeaders, hasUnresolvedHeaders)
        }

        override fun processSource(sourceFile: File) {
            if (visitSourceFile(sourceFile)) {
                toRecompile.add(sourceFile)
            }
        }

        /**
         * @return true if this source file requires recompilation, false otherwise.
         */
        fun visitSourceFile(sourceFile: File): kotlin.Boolean {
            return fileSystemAccess.readRegularFileContentHash(sourceFile.getAbsolutePath())
                .map<kotlin.Boolean?>(Function { fileContent: HashCode? ->
                    val previousState = previous.getState(sourceFile)
                    if (previousState != null) {
                        // Already seen this source file before. See if we can reuse the analysis from last time
                        if (graphHasNotChanged(sourceFile, fileContent!!, previousState, existingHeaders)) {
                            // Include file graph for this source file has not changed, skip this file
                            current.setState(sourceFile, previousState)
                            if (previousState.isHasUnresolved() && !ignoreUnresolvedHeadersInDependencies) {
                                hasUnresolvedHeaders = true
                                return@map true
                            }
                            return@map false
                        }
                        // Else, something has changed in the include file graph for this source file, so analyse again
                    }

                    // Source file has not been compiled before, or its include file graph has changed in some way
                    // Calculate the include file graph for the source file and mark for recompilation
                    val visibleMacros = CollectingMacroLookup(initialIncludeDirectives)
                    val result = visitFile(sourceFile, fileContent, visibleMacros, HashSet<HashCode?>(), existingHeaders)
                    val includedFiles: MutableSet<IncludeFileEdge?> = LinkedHashSet<IncludeFileEdge?>()
                    result.collectFilesInto(includedFiles, HashSet<File?>())
                    val newState = SourceFileState(fileContent, result.result == IncludeFileResolutionResult.UnresolvedMacroIncludes, ImmutableSet.copyOf<IncludeFileEdge?>(includedFiles))
                    current.setState(sourceFile, newState)
                    if (newState.isHasUnresolved()) {
                        hasUnresolvedHeaders = true
                    }
                    true
                }) // Skip things that aren't files
                .orElse(false)
        }

        fun graphHasNotChanged(sourceFile: File?, fileHash: HashCode, previousState: SourceFileState, existingHeaders: MutableSet<File?>): kotlin.Boolean {
            if (fileHash != previousState.getHash()) {
                // Source file has changed
                return false
            }
            if (previousState.getEdges().isEmpty()) {
                // Source file has not changed and no include files
                return true
            }

            // Check each unique edge in the include file graph
            val includes: MutableMap<HashCode?, File?> = HashMap<HashCode?, File?>(previousState.getEdges().size)
            val headers: MutableSet<File?> = HashSet<File?>()
            includes.put(fileHash, sourceFile)
            for (includeFileEdge in previousState.getEdges()) {
                val includedFrom = if (includeFileEdge.getIncludedBy() != null) includes.get(includeFileEdge.getIncludedBy()) else null
                val includeFile = sourceIncludesResolver.resolveInclude(includedFrom, includeFileEdge.getIncludePath())
                if (includeFile == null) {
                    // Include file not found (but previously was found)
                    return false
                }
                val hash = includeFile.getContentHash()
                if (hash != includeFileEdge.getResolvedTo()) {
                    // Include file changed
                    return false
                }
                if (!existingHeaders.contains(includeFile.getFile())) {
                    // Collect for later, do not add until the graph is known to have not changed
                    headers.add(includeFile.getFile())
                }
                includes.put(hash, includeFile.getFile())
            }
            existingHeaders.addAll(headers)
            return true
        }

        fun visitFile(file: File, newHash: HashCode?, visibleMacros: CollectingMacroLookup, visited: MutableSet<HashCode?>, existingHeaders: MutableSet<File?>): FileVisitResult {
            var fileDetails = visitedFiles.get(file)
            if (fileDetails != null && fileDetails.results != null) {
                // A file that we can safely reuse the result for
                visibleMacros.append(fileDetails.results)
                return fileDetails.results!!
            }

            if (!visited.add(newHash)) {
                // A cycle, treat as resolved here
                return FileVisitResult(file)
            }

            if (fileDetails == null) {
                val includeDirectives = sourceIncludesParser.parseIncludes(file)
                fileDetails = FileDetails(includeDirectives)
                visitedFiles.put(file, fileDetails)
            }

            val includedFileDirectives = CollectingMacroLookup()
            visibleMacros.append(file, fileDetails.directives)

            val allIncludes = fileDetails.directives.getAll()
            val included: MutableList<FileVisitResult> = if (allIncludes.isEmpty()) mutableListOf<FileVisitResult?>() else ArrayList<FileVisitResult?>(allIncludes.size)
            val edges = if (allIncludes.isEmpty()) mutableListOf<IncludeFileEdge?>() else ArrayList<IncludeFileEdge?>(allIncludes.size)
            var result = IncludeFileResolutionResult.NoMacroIncludes
            for (include in allIncludes) {
                if (include.getType() == IncludeType.MACRO && result == IncludeFileResolutionResult.NoMacroIncludes) {
                    result = IncludeFileResolutionResult.HasMacroIncludes
                }
                val resolutionResult = sourceIncludesResolver.resolveInclude(file, include, visibleMacros)
                if (!resolutionResult.isComplete()) {
                    LOGGER.info("Cannot locate header file for '{}' in source file '{}'. Assuming changed.", include.getAsSourceText(), file.getName())
                    if (!ignoreUnresolvedHeadersInDependencies) {
                        result = IncludeFileResolutionResult.UnresolvedMacroIncludes
                    }
                }
                for (includeFile in resolutionResult.getFiles()) {
                    existingHeaders.add(includeFile.getFile())
                    val includeVisitResult = visitFile(includeFile.getFile(), includeFile.getContentHash(), visibleMacros, visited, existingHeaders)
                    if (includeVisitResult.result.ordinal > result.ordinal) {
                        result = includeVisitResult.result
                    }
                    includeVisitResult.collectDependencies(includedFileDirectives)
                    included.add(includeVisitResult)
                    edges.add(IncludeFileEdge(includeFile.getPath(), if (includeFile.isQuotedInclude()) newHash else null, includeFile.getContentHash()))
                }
            }

            val visitResult = FileVisitResult(file, result, fileDetails.directives, included, edges, includedFileDirectives)
            if (result == IncludeFileResolutionResult.NoMacroIncludes) {
                // No macro includes were seen in the include graph of this file, so the result can be reused if this file is seen again
                fileDetails.results = visitResult
            }
            return visitResult
        }

        val removedSources: MutableList<File?>
            get() {
                val removed: MutableList<File?> = ArrayList<File?>()
                for (previousSource in previous.getSourceInputs()) {
                    if (!current.getSourceInputs().contains(previousSource)) {
                        removed.add(previousSource)
                    }
                }
                return removed
            }
    }

    private enum class IncludeFileResolutionResult {
        NoMacroIncludes,
        HasMacroIncludes,  // but all resolved ok
        UnresolvedMacroIncludes
    }

    /**
     * Details of a file that are independent of where the file appears in the file include graph.
     */
    private class FileDetails(val directives: IncludeDirectives) {
        // Non-null when the result of visiting this file can be reused
        var results: FileVisitResult? = null
    }

    /**
     * Details of a file included in a specific location in the file include graph.
     */
    private class FileVisitResult : CollectingMacroLookup.MacroSource {
        private val file: File?
        private val result: IncludeFileResolutionResult
        private val includeDirectives: IncludeDirectives?
        private val included: MutableList<FileVisitResult>
        private val edges: MutableList<IncludeFileEdge?>
        private val includeFileDirectives: CollectingMacroLookup?

        internal constructor(
            file: File?,
            result: IncludeFileResolutionResult,
            includeDirectives: IncludeDirectives?,
            included: MutableList<FileVisitResult>,
            edges: MutableList<IncludeFileEdge?>,
            dependentIncludeDirectives: CollectingMacroLookup?
        ) {
            this.file = file
            this.result = result
            this.includeDirectives = includeDirectives
            this.included = included
            this.edges = edges
            this.includeFileDirectives = dependentIncludeDirectives
        }

        internal constructor(file: File?) {
            this.file = file
            result = IncludeFileResolutionResult.NoMacroIncludes
            includeDirectives = null
            included = mutableListOf<FileVisitResult?>()
            edges = mutableListOf<IncludeFileEdge?>()
            includeFileDirectives = null
        }

        fun collectDependencies(directives: CollectingMacroLookup) {
            if (includeDirectives != null) {
                directives.append(this)
            }
        }

        fun collectFilesInto(files: MutableCollection<IncludeFileEdge?>, seen: MutableSet<File?>) {
            if (includeDirectives != null && seen.add(file)) {
                files.addAll(edges)
                for (include in included) {
                    include.collectFilesInto(files, seen)
                }
            }
        }

        override fun collectInto(lookup: CollectingMacroLookup) {
            if (includeDirectives != null) {
                lookup.append(file, includeDirectives)
                includeFileDirectives!!.appendTo(lookup)
            }
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(IncrementalCompileFilesFactory::class.java)
        private const val IGNORE_UNRESOLVED_HEADERS_IN_DEPENDENCIES_PROPERTY_NAME = "org.gradle.internal.native.headers.unresolved.dependencies.ignore"
    }
}
