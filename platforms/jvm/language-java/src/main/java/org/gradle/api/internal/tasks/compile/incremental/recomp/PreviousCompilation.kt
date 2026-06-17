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

import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet
import org.gradle.api.internal.tasks.compile.incremental.deps.ClassSetAnalysis
import java.util.function.Function
import java.util.stream.Collectors

class PreviousCompilation(private val data: PreviousCompilationData) {
    private val classAnalysis: ClassSetAnalysis

    init {
        this.classAnalysis = ClassSetAnalysis(data.getOutputSnapshot(), data.getAnnotationProcessingData(), data.getCompilerApiData())
    }

    val classpath: ClassSetAnalysis?
        get() = ClassSetAnalysis(data.getClasspathSnapshot())

    fun findDependentsOfClasspathChanges(diff: ClassSetAnalysis.ClassSetDiff): DependentsSet? {
        if (diff.getDependents().isDependencyToAll) {
            return diff.getDependents()
        }
        return classAnalysis.findTransitiveDependents(diff.getDependents().allDependentClasses, diff.getConstants())
    }

    fun findDependentsOfSourceChanges(classNames: MutableSet<String?>): DependentsSet? {
        return classAnalysis.findTransitiveDependents(
            classNames,
            classNames.stream().collect(Collectors.toMap(Function.identity<String?>(), Function { className: String? -> classAnalysis.getConstants(className) }))
        )
    }

    fun getAnnotationProcessingDependentsSet(className: String?): DependentsSet? {
        return classAnalysis.getAnnotationProcessingDependentsSet(className)
    }

    fun getTypesToReprocess(compiledClasses: MutableSet<String?>): MutableSet<String?>? {
        return classAnalysis.getTypesToReprocess(compiledClasses)
    }

    val sourceToClassConverter: SourceFileClassNameConverter
        get() = DefaultSourceFileClassNameConverter(data.getCompilerApiData().getSourceToClassMapping())
}
