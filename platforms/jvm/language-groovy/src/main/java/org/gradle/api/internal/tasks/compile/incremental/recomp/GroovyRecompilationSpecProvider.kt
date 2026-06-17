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
package org.gradle.api.internal.tasks.compile.incremental.recomp

import com.google.common.collect.ImmutableSet
import org.gradle.api.file.FileTree
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.internal.file.Deleter
import org.gradle.work.FileChange
import java.util.stream.Collectors

class GroovyRecompilationSpecProvider(
    deleter: Deleter?,
    fileOperations: FileOperations?,
    sources: FileTree?,
    incremental: Boolean,
    sourceChanges: Iterable<FileChange?>?
) : AbstractRecompilationSpecProvider(deleter, fileOperations, sources, sourceChanges, incremental) {
    /**
     * For all classes with Java source that we will be recompiled due to some change, we need to recompile all subclasses.
     * This is because Groovy might try to load some subclass when analysing Groovy classes before Java compilation, but if parent class was stale,
     * it has been deleted, so class loading of a subclass will fail.
     *
     * Fix for issue [#22531](https://github.com/gradle/gradle/issues/22531).
     */
    override fun processCompilerSpecificDependencies(
        spec: JavaCompileSpec?,
        recompilationSpec: RecompilationSpec,
        sourceFileChangeProcessor: SourceFileChangeProcessor,
        sourceFileClassNameConverter: SourceFileClassNameConverter
    ) {
        if (!supportsGroovyJavaJointCompilation(spec)) {
            return
        }
        val classesWithJavaSource = recompilationSpec.getClassesToCompile().stream()
            .flatMap<String?> { classToCompile: String? -> sourceFileClassNameConverter.getRelativeSourcePaths(classToCompile).stream() }
            .filter { sourcePath: String? -> sourcePath!!.endsWith(".java") }
            .flatMap<String?> { sourcePath: String? -> sourceFileClassNameConverter.getClassNames(sourcePath).stream() }
            .collect(Collectors.toSet())
        if (!classesWithJavaSource.isEmpty()) {
            // We need to collect just accessible dependents, since it seems
            // private references to classes are not problematic when Groovy compiler loads a class
            sourceFileChangeProcessor.processOnlyAccessibleChangeOfClasses(classesWithJavaSource, recompilationSpec)
        }
    }

    private fun supportsGroovyJavaJointCompilation(spec: JavaCompileSpec?): Boolean {
        return spec is GroovyJavaJointCompileSpec && spec.groovyCompileOptions!!.fileExtensions!!.contains("java")
    }

    override fun getFileExtensions(): MutableSet<String?> {
        return SUPPORTED_FILE_EXTENSIONS
    }

    override fun isIncrementalOnResourceChanges(currentCompilation: CurrentCompilation?): Boolean {
        return false
    }

    companion object {
        private val SUPPORTED_FILE_EXTENSIONS: MutableSet<String?> = ImmutableSet.of<String?>(".java", ".groovy")
    }
}
