/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.Iterables
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet.Companion.dependencyToAll
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysis

class CurrentCompilation(private val spec: JavaCompileSpec, private val classpathSnapshotter: CurrentCompilationAccess) {
    val annotationProcessorPath: MutableCollection<File?>
        get() = spec.annotationProcessorPath

    fun findDependentsOfClasspathChanges(previous: PreviousCompilation): DependentsSet? {
        val currentClasspath = this.classpath
        val previousClasspath = previous.getClasspath()
        if (previousClasspath == null) {
            return dependencyToAll("classpath data of previous compilation is incomplete")
        }
        val classpathChanges = currentClasspath.findChangesSince(previousClasspath)
        return previous.findDependentsOfClasspathChanges(classpathChanges)
    }

    private val classpath: ClassSetAnalysis
        get() = ClassSetAnalysis(
            classpathSnapshotter.getClasspathSnapshot(
                Iterables.< File > concat < java . io . File ? > (spec.compileClasspath,
                spec.modulePath
            )
        )
}
