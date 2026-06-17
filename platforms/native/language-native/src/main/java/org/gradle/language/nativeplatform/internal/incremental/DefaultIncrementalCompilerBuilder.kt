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
package org.gradle.language.nativeplatform.internal.incremental

import com.google.common.collect.ImmutableList
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.file.TaskFileVarFactory
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.tasks.properties.LifecycleAwareValue
import org.gradle.api.provider.Provider
import org.gradle.cache.ObjectHolder
import org.gradle.internal.file.Deleter
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.vfs.FileSystemAccess
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.nativeplatform.internal.Expression
import org.gradle.language.nativeplatform.internal.Include
import org.gradle.language.nativeplatform.internal.IncludeDirectives
import org.gradle.language.nativeplatform.internal.Macro
import org.gradle.language.nativeplatform.internal.MacroFunction
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.CSourceParser
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.DefaultIncludeDirectives
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.MacroWithSimpleExpression
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.RegexBackedCSourceParser
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec
import java.io.File
import java.util.TreeSet

class DefaultIncrementalCompilerBuilder(
    private val buildOperationRunner: BuildOperationRunner?,
    private val compilationStateCacheFactory: CompilationStateCacheFactory,
    private val sourceParser: CSourceParser?,
    private val deleter: Deleter,
    private val directoryFileTreeFactory: DirectoryFileTreeFactory,
    private val fileSystemAccess: FileSystemAccess?,
    private val fileVarFactory: TaskFileVarFactory
) : IncrementalCompilerBuilder {
    override fun newCompiler(
        task: TaskInternal,
        sourceFiles: FileCollection,
        includeDirs: FileCollection,
        macros: MutableMap<String?, String?>,
        importAware: Provider<Boolean?>
    ): IncrementalCompilerBuilder.IncrementalCompiler {
        return StateCollectingIncrementalCompiler(
            task,
            includeDirs,
            sourceFiles,
            macros,
            importAware,
            buildOperationRunner,
            compilationStateCacheFactory,
            sourceParser,
            deleter,
            directoryFileTreeFactory,
            fileSystemAccess,
            fileVarFactory
        )
    }

    private class StateCollectingIncrementalCompiler(
        task: TaskInternal,
        private val includeDirs: FileCollection,
        private val sourceFiles: FileCollection,
        private val macros: MutableMap<String?, String?>,
        private val importAware: Provider<Boolean?>,

        private val buildOperationRunner: BuildOperationRunner?,
        private val compilationStateCacheFactory: CompilationStateCacheFactory,
        private val sourceParser: CSourceParser?,
        private val deleter: Deleter,
        private val directoryFileTreeFactory: DirectoryFileTreeFactory,
        private val fileSystemAccess: FileSystemAccess?,
        fileVarFactory: TaskFileVarFactory
    ) : IncrementalCompilerBuilder.IncrementalCompiler, MinimalFileSet, LifecycleAwareValue {
        private val taskOutputs: TaskOutputsInternal
        private val taskPath: String
        private val headerFilesCollection: FileCollection?
        private var compileStateCache: ObjectHolder<CompilationState?>? = null
        private var incrementalCompilation: IncrementalCompilation? = null

        init {
            this.taskOutputs = task.getOutputs()
            this.taskPath = task.getPath()
            this.headerFilesCollection = fileVarFactory.newCalculatedInputFileCollection(task, this, sourceFiles, includeDirs)
        }

        override fun <T : NativeCompileSpec?> createCompiler(compiler: Compiler<T?>): Compiler<T?> {
            checkNotNull(incrementalCompilation) { "Header files should be calculated before compiler is created." }
            return IncrementalNativeCompiler<T?>(taskOutputs, compiler, deleter, compileStateCache, incrementalCompilation)
        }

        override fun getFiles(): MutableSet<File?> {
            val includeRoots: MutableList<File?> = ImmutableList.copyOf<File?>(includeDirs)
            compileStateCache = compilationStateCacheFactory.create(taskPath)
            val sourceIncludesParser = DefaultSourceIncludesParser(sourceParser, importAware.get()!!)
            val dependencyParser = DefaultSourceIncludesResolver(includeRoots, fileSystemAccess)
            val includeDirectives = directivesForMacros(macros)
            val incrementalCompileFilesFactory = IncrementalCompileFilesFactory(includeDirectives, sourceIncludesParser, dependencyParser, fileSystemAccess)
            val incrementalCompileProcessor = IncrementalCompileProcessor(compileStateCache, incrementalCompileFilesFactory, buildOperationRunner)

            incrementalCompilation = incrementalCompileProcessor.processSourceFiles(TreeSet<File?>(sourceFiles.getFiles()))
            val headerDependenciesCollector = DefaultHeaderDependenciesCollector(directoryFileTreeFactory)
            return headerDependenciesCollector.collectExistingHeaderDependencies(taskPath, includeRoots, incrementalCompilation!!)
        }

        fun directivesForMacros(macros: MutableMap<String?, String?>): IncludeDirectives? {
            val builder = ImmutableList.builder<Macro?>()
            for (entry in macros.entries) {
                val expression: Expression = RegexBackedCSourceParser.Companion.parseExpression(entry.value)
                builder.add(MacroWithSimpleExpression(entry.key, expression.getType(), expression.getValue()))
            }
            return DefaultIncludeDirectives.Companion.of(ImmutableList.of<Include?>(), builder.build(), ImmutableList.of<MacroFunction?>())
        }

        override fun prepareValue() {
        }

        override fun cleanupValue() {
            compileStateCache = null
            incrementalCompilation = null
        }

        override fun getDisplayName(): String {
            return "header files for " + taskPath
        }

        override fun getHeaderFiles(): FileCollection? {
            return headerFilesCollection
        }
    }
}
