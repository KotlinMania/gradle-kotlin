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

import com.google.common.collect.ImmutableSet
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.internal.file.Deleter
import org.gradle.work.FileChange

class JavaRecompilationSpecProvider(
    deleter: Deleter?,
    fileOperations: FileOperations?,
    sourceTree: FileTree?,
    incremental: Boolean,
    sourceFileChanges: Iterable<FileChange?>?
) : AbstractRecompilationSpecProvider(deleter, fileOperations, sourceTree, sourceFileChanges, incremental) {
    override fun getFileExtensions(): MutableSet<String?> {
        return SUPPORTED_FILE_EXTENSIONS
    }

    override fun processCompilerSpecificDependencies(
        spec: JavaCompileSpec?,
        recompilationSpec: RecompilationSpec?,
        sourceFileChangeProcessor: SourceFileChangeProcessor?,
        sourceFileClassNameConverter: SourceFileClassNameConverter?
    ) {
        // Nothing to do for Java compiler here
    }

    override fun isIncrementalOnResourceChanges(currentCompilation: CurrentCompilation): Boolean {
        return currentCompilation.getAnnotationProcessorPath().isEmpty()
    }

    companion object {
        private val SUPPORTED_FILE_EXTENSIONS: MutableSet<String?> = ImmutableSet.of<String?>(".java")
    }
}
