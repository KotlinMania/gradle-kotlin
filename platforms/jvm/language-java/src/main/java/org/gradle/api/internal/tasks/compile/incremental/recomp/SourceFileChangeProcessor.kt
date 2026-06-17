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

internal class SourceFileChangeProcessor(private val previousCompilation: PreviousCompilation) {
    fun processChange(classNames: MutableSet<String?>?, spec: RecompilationSpec) {
        spec.addClassesToCompile(classNames)
        val actualDependents = previousCompilation.findDependentsOfSourceChanges(classNames)
        if (actualDependents.isDependencyToAll) {
            spec.setFullRebuildCause(actualDependents.description)
            return
        }
        spec.addClassesToCompile(actualDependents.allDependentClasses)
        spec.addResourcesToGenerate(actualDependents.dependentResources)
    }

    fun processOnlyAccessibleChangeOfClasses(classNames: MutableSet<String?>?, spec: RecompilationSpec) {
        val actualDependents = previousCompilation.findDependentsOfSourceChanges(classNames)
        if (actualDependents.isDependencyToAll) {
            spec.setFullRebuildCause(actualDependents.description)
            return
        }
        spec.addClassesToCompile(actualDependents.accessibleDependentClasses)
        spec.addResourcesToGenerate(actualDependents.dependentResources)
    }

    fun processAnnotationDependenciesOfIndependentClasses(classNames: MutableSet<String?>, spec: RecompilationSpec): MutableSet<String?> {
        val newAdded: MutableSet<String?> = LinkedHashSet<String?>()
        for (className in classNames) {
            val annotationProcessingDependentsSet = previousCompilation.getAnnotationProcessingDependentsSet(className)
            for (classToCompile in annotationProcessingDependentsSet.allDependentClasses!!) {
                if (spec.addClassToCompile(classToCompile)) {
                    newAdded.add(classToCompile)
                }
            }
            spec.addResourcesToGenerate(annotationProcessingDependentsSet.dependentResources)
        }
        return newAdded
    }
}
