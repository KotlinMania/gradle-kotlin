/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.compile

import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.WorkResults
import org.gradle.internal.file.Deleter
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.base.internal.tasks.StaleOutputCleaner
import java.io.File

/**
 * Deletes stale classes before invoking the actual compiler.
 */
class CleaningJavaCompiler<T : JavaCompileSpec?>(val compiler: Compiler<T?>, private val taskOutputs: TaskOutputsInternal, private val deleter: Deleter?) : Compiler<T?> {
    override fun execute(spec: T?): WorkResult {
        val outputDirs = ImmutableSet.builderWithExpectedSize<File?>(3)
        val compileOptions: MinimalJavaCompileOptions = spec!!.compileOptions!!
        addDirectoryIfNotNull(outputDirs, spec.getDestinationDir())
        addDirectoryIfNotNull(outputDirs, compileOptions.annotationProcessorGeneratedSourcesDirectory)
        addDirectoryIfNotNull(outputDirs, compileOptions.headerOutputDirectory)
        val cleanedOutputs: Boolean = StaleOutputCleaner.cleanOutputs(deleter, taskOutputs.getPreviousOutputFiles(), outputDirs.build())

        val compiler: Compiler<in T?> = this.compiler
        return compiler.execute(spec)
            .or(WorkResults.didWork(cleanedOutputs))
    }

    private fun addDirectoryIfNotNull(outputDirs: ImmutableSet.Builder<File?>, dir: File?) {
        if (dir != null) {
            outputDirs.add(dir)
        }
    }
}
