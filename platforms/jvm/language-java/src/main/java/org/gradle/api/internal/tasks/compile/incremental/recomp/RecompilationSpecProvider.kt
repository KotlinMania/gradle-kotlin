/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.recomp

import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.incremental.transaction.CompileTransaction
import org.gradle.api.tasks.WorkResult

/**
 * In a typical incremental recompilation, there're three steps:
 * First, examine the incremental change files to get the classes to be recompiled: [.provideRecompilationSpec]
 * Second, initialize the recompilation (e.g. delete stale class files and narrow down the source files to be recompiled): [.initCompilationSpecAndTransaction]
 * Third, decorate the compilation result if necessary: [.decorateResult], for example, notify whether current recompilation is full recompilation.
 */
interface RecompilationSpecProvider {
    val isIncremental: Boolean

    fun provideRecompilationSpec(spec: JavaCompileSpec?, current: CurrentCompilation?, previous: PreviousCompilation?): RecompilationSpec?

    fun initCompilationSpecAndTransaction(spec: JavaCompileSpec?, recompilationSpec: RecompilationSpec?): CompileTransaction?

    fun decorateResult(recompilationSpec: RecompilationSpec, previousCompilationData: PreviousCompilationData?, workResult: WorkResult): WorkResult? {
        if (!recompilationSpec.isFullRebuildNeeded()) {
            return DefaultIncrementalCompileResult(previousCompilationData, recompilationSpec, workResult)
        }
        return workResult
    }
}
