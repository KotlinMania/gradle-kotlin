/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental

import org.gradle.api.file.FileTree
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.incremental.classpath.ClassSetAnalyzer
import org.gradle.api.internal.tasks.compile.incremental.recomp.CurrentCompilationAccess
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationAccess
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpecProvider
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.language.base.internal.compile.Compiler

@ServiceScope(Scope.Build::class)
class IncrementalCompilerFactory(private val buildOperationExecutor: BuildOperationExecutor?, private val interner: StringInterner?, private val classSetAnalyzer: ClassSetAnalyzer?) {
    fun <T : JavaCompileSpec?> makeIncremental(cleaningJavaCompiler: CleaningJavaCompiler<T?>, sources: FileTree?, recompilationSpecProvider: RecompilationSpecProvider?): Compiler<T?> {
        val rebuildAllCompiler = createRebuildAllCompiler<T?>(cleaningJavaCompiler, sources)
        val currentCompilationAccess = CurrentCompilationAccess(classSetAnalyzer, buildOperationExecutor)
        val previousCompilationAccess = PreviousCompilationAccess(interner)
        val compiler: Compiler<T?> = SelectiveCompiler<T?>(cleaningJavaCompiler, rebuildAllCompiler, recompilationSpecProvider, currentCompilationAccess, previousCompilationAccess)
        return IncrementalResultStoringCompiler<T?>(compiler, currentCompilationAccess, previousCompilationAccess)
    }

    private fun <T : JavaCompileSpec?> createRebuildAllCompiler(cleaningJavaCompiler: CleaningJavaCompiler<T?>, sourceFiles: FileTree?): Compiler<T?> {
        return Compiler { spec: T? ->
            spec!!.setSourceFiles(sourceFiles)
            cleaningJavaCompiler.execute(spec)
        }
    }
}
