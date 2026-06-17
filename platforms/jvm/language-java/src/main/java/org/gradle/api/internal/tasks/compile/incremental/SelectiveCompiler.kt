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
package org.gradle.api.internal.tasks.compile.incremental

import com.google.common.collect.Iterables
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.incremental.recomp.CurrentCompilation
import org.gradle.api.internal.tasks.compile.incremental.recomp.CurrentCompilationAccess
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilation
import org.gradle.api.internal.tasks.compile.incremental.recomp.PreviousCompilationAccess
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpecProvider
import org.gradle.api.tasks.WorkResult
import org.gradle.internal.time.Time.startTimer
import org.gradle.language.base.internal.compile.Compiler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Objects
import java.util.function.Function

/**
 * A compiler that selects classes for compilation. It also handles restore of output state in case of a compile failure.
 */
internal class SelectiveCompiler<T : JavaCompileSpec?>(
    private val cleaningCompiler: CleaningJavaCompiler<T?>,
    private val rebuildAllCompiler: Compiler<T?>,
    private val recompilationSpecProvider: RecompilationSpecProvider,
    private val classpathSnapshotter: CurrentCompilationAccess?,
    private val previousCompilationAccess: PreviousCompilationAccess
) : Compiler<T?> {
    override fun execute(spec: T?): WorkResult? {
        if (!recompilationSpecProvider.isIncremental()) {
            LOG.info("Full recompilation is required because no incremental change information is available. This is usually caused by clean builds or changing compiler arguments.")
            return rebuildAllCompiler.execute(spec)
        }
        val previousCompilationDataFile = Objects.requireNonNull<File>(spec!!.compileOptions!!.previousCompilationDataFile)
        if (!previousCompilationDataFile.exists()) {
            LOG.info("Full recompilation is required because no previous compilation result is available.")
            return rebuildAllCompiler.execute(spec)
        }
        if (spec.sourceRoots!!.isEmpty()) {
            LOG.info("Full recompilation is required because the source roots could not be inferred.")
            return rebuildAllCompiler.execute(spec)
        }

        val clock = startTimer()
        val currentCompilation = CurrentCompilation(spec, classpathSnapshotter)

        val previousCompilationData = previousCompilationAccess.readPreviousCompilationData(previousCompilationDataFile)
        val previousCompilation = PreviousCompilation(previousCompilationData)
        val recompilationSpec = recompilationSpecProvider.provideRecompilationSpec(spec, currentCompilation, previousCompilation)

        if (recompilationSpec.isFullRebuildNeeded()) {
            LOG.info("Full recompilation is required because {}. Analysis took {}.", recompilationSpec.getFullRebuildCause(), clock.elapsed)
            return rebuildAllCompiler.execute(spec)
        }

        val transaction = recompilationSpecProvider.initCompilationSpecAndTransaction(spec, recompilationSpec)
        return transaction.execute<WorkResult?>(Function { workResult: WorkResult? ->
            if (Iterables.isEmpty(spec.getSourceFiles()!!) && spec.classesToProcess!!.isEmpty()) {
                LOG.info("None of the classes needs to be compiled! Analysis took {}. ", clock.elapsed)
                return@execute RecompilationNotNecessary(previousCompilationData, recompilationSpec)
            }
            try {
                var result = cleaningCompiler.getCompiler().execute(spec)
                result = recompilationSpecProvider.decorateResult(recompilationSpec, previousCompilationData, result)
                return@execute result.or(workResult!!)
            } finally {
                val classesToCompile: MutableCollection<String?> = recompilationSpec.getClassesToCompile()
                LOG.info("Incremental compilation of {} classes completed in {}.", classesToCompile.size, clock.elapsed)
                LOG.debug("Recompiled classes {}", classesToCompile)
            }
        })
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(SelectiveCompiler::class.java)
    }
}
