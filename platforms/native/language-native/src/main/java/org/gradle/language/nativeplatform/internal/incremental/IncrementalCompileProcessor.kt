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

import org.gradle.cache.ObjectHolder
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.CallableBuildOperation
import java.io.File

class IncrementalCompileProcessor(
    private val previousCompileStateCache: ObjectHolder<CompilationState?>,
    private val incrementalCompileFilesFactory: IncrementalCompileFilesFactory,
    private val buildOperationExecutor: BuildOperationRunner
) {
    fun processSourceFiles(sourceFiles: MutableCollection<File?>): IncrementalCompilation? {
        return buildOperationExecutor.call<IncrementalCompilation?>(object : CallableBuildOperation<IncrementalCompilation?> {
            override fun call(context: BuildOperationContext): IncrementalCompilation? {
                val processor = incrementalCompileFilesFactory.files(previousCompileStateCache.get())
                for (sourceFile in sourceFiles) {
                    processor.processSource(sourceFile)
                }
                return processor.getResult()
            }

            override fun description(): BuildOperationDescriptor.Builder {
                return BuildOperationDescriptor
                    .displayName("Processing source files")
                    .details(ProcessSourceFilesDetails(sourceFiles.size))
            }

            inner class ProcessSourceFilesDetails(// public API
                @get:Suppress("unused") val sourceFileCount: Int
            )
        })
    }
}
