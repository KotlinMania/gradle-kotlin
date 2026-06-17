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
package org.gradle.api.internal.tasks.compile.incremental.deps

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.CompilerApiData
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet.Companion.dependencyToAll
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData
import java.util.ArrayDeque
import java.util.Arrays
import java.util.Deque

/**
 * Combines [ClassSetAnalysisData], [AnnotationProcessingData] and [CompilerApiData] to implement the transitive change detection algorithm.
 */
class ClassSetAnalysis @JvmOverloads constructor(
    private val classAnalysis: ClassSetAnalysisData,
    private val annotationProcessingData: AnnotationProcessingData = AnnotationProcessingData(),
    private val compilerApiData: CompilerApiData = CompilerApiData.Companion.unavailable()
) {
    /**
     * Computes the types affected by the changes since some other class set, including transitively affected classes.
     */
    fun findChangesSince(other: ClassSetAnalysis): ClassSetDiff {
        val directChanges = classAnalysis.getChangedClassesSince(other.classAnalysis)
        if (directChanges.isDependencyToAll) {
            return ClassSetDiff(directChanges, mutableMapOf<String?, IntSet?>())
        }
        val transitiveChanges = other.findTransitiveDependents(directChanges.allDependentClasses!!, mutableMapOf<String?, IntSet?>())
        if (transitiveChanges.isDependencyToAll) {
            return ClassSetDiff(transitiveChanges, mutableMapOf<String?, IntSet?>())
        }
        val allChanges: DependentsSet = DependentsSet.merge(Arrays.asList<T?>(directChanges, transitiveChanges))!!
        val changedConstants = findChangedConstants(other, allChanges)
        return ClassSetDiff(allChanges, changedConstants)
    }

    private fun findChangedConstants(other: ClassSetAnalysis, affectedClasses: DependentsSet): MutableMap<String?, IntSet?> {
        if (affectedClasses.isDependencyToAll) {
            return mutableMapOf<String?, IntSet?>()
        }
        val dependentClasses: MutableSet<String?> = affectedClasses.allDependentClasses!!
        val result: MutableMap<String?, IntSet?> = HashMap<String?, IntSet?>(dependentClasses.size)
        for (affectedClass in dependentClasses) {
            val difference: IntSet = IntOpenHashSet(other.getConstants(affectedClass))
            difference.removeAll(getConstants(affectedClass))
            result.put(affectedClass, difference)
        }
        return result
    }

    /**
     * Computes the transitive dependents of a set of changed classes. If the classes had any changes to inlineable constants, these need to be provided as the second parameter.
     *
     * If incremental annotation processing encountered issues in the previous compilation, a full recompilation is required.
     * If any inlineable constants have changed and the compiler does not support exact constant dependency tracking, then a full recompilation is required.
     * Otherwise follows the below rules for all of the given classes, as well as the classes that were marked as "always recompile" by annotation processing:
     *
     * Starts at this class and capture all classes that reference this class and all classes and resources that were generated from this class.
     * Then does the same analysis for all classes that expose this class on their ABI recursively until no more new classes are discovered.
     */
    fun findTransitiveDependents(classes: MutableCollection<String?>, changedConstantsByClass: MutableMap<String?, IntSet?>): DependentsSet {
        if (classes.isEmpty()) {
            return DependentsSet.empty()
        }
        val fullRebuildCause = annotationProcessingData.getFullRebuildCause()
        if (fullRebuildCause != null) {
            return dependencyToAll(fullRebuildCause)
        }
        if (!compilerApiData.isSupportsConstantsMapping()) {
            for (changedConstantsOfClass in changedConstantsByClass.entries) {
                if (!changedConstantsOfClass.value!!.isEmpty()) {
                    return dependencyToAll("an inlineable constant in '" + changedConstantsOfClass.key + "' has changed")
                }
            }
        }
        val privateDependents: MutableSet<String?> = HashSet<String?>()
        val accessibleDependents: MutableSet<String?> = HashSet<String?>()
        val dependentResources: MutableSet<GeneratedResource?> = HashSet<GeneratedResource?>(annotationProcessingData.getGeneratedResourcesDependingOnAllOthers())
        val visited: MutableSet<String?> = HashSet<String?>()
        val remaining: Deque<String> = ArrayDeque<String>(classes)
        remaining.addAll(annotationProcessingData.getGeneratedTypesDependingOnAllOthers())

        while (!remaining.isEmpty()) {
            val current = remaining.pop()
            if (!visited.add(current)) {
                continue
            }
            accessibleDependents.add(current)
            val dependents = findDirectDependents(current)
            if (dependents.isDependencyToAll) {
                return dependents
            }
            dependentResources.addAll(dependents.dependentResources!!)
            privateDependents.addAll(dependents.privateDependentClasses!!)
            remaining.addAll(dependents.accessibleDependentClasses!!)
        }

        privateDependents.removeAll(classes)
        accessibleDependents.removeAll(classes)
        return DependentsSet.dependents(privateDependents, accessibleDependents, dependentResources)!!
    }

    /**
     * Finds all the classes and resources that are directly affected by the given one. This includes:
     *
     * - Classes that referenced this class in their bytecode
     * - Classes that use a constant declared in this class
     * - Classes and resources that were generated from this class
     */
    private fun findDirectDependents(className: String?): DependentsSet {
        val annotationProcessingDependentsSet = getAnnotationProcessingDependentsSet(className)
        return DependentsSet.merge(
            java.util.Arrays.asList<T?>(
                classAnalysis.getDependents(className),
                compilerApiData.getConstantDependentsForClass(className),
                annotationProcessingDependentsSet
            )
        )!!
    }

    fun getAnnotationProcessingDependentsSet(className: String?): DependentsSet {
        val generatedClasses = annotationProcessingData.getGeneratedTypesByOrigin().getOrDefault(className, mutableSetOf<String?>())
        val generatedResources = annotationProcessingData.getGeneratedResourcesByOrigin().getOrDefault(className, mutableSetOf<GeneratedResource?>())
        return DependentsSet.dependents(mutableSetOf<T?>(), generatedClasses, generatedResources)!!
    }

    /**
     * Returns the types that need to be reprocessed based on which classes are due to be recompiled. This includes:
     *
     * - types which are annotated with aggregating annotations, as aggregating processors need to see them regardless of what has changed
     * - the originating types of generated classes that need to be recompiled, since they wouldn't exist if the originating type is not reprocessed
     */
    fun getTypesToReprocess(compiledClasses: MutableSet<String?>): MutableSet<String?> {
        if (compiledClasses.isEmpty()) {
            return mutableSetOf<String?>()
        }
        val typesToReprocess: MutableSet<String?> = HashSet<String?>(annotationProcessingData.getAggregatedTypes())
        for (entry in annotationProcessingData.getGeneratedTypesByOrigin().entries) {
            if (entry.value.stream().anyMatch { o: String? -> compiledClasses.contains(o) }) {
                typesToReprocess.add(entry.key)
            }
        }
        for (toReprocess in ArrayList<String?>(typesToReprocess)) {
            typesToReprocess.removeAll(annotationProcessingData.getGeneratedTypesByOrigin().getOrDefault(toReprocess, mutableSetOf<String?>()))
        }
        return typesToReprocess
    }

    fun getConstants(className: String?): IntSet? {
        return classAnalysis.getConstants(className)
    }

    /**
     * Provides the difference between two class sets, including which types are affected and which constants have changed.
     */
    class ClassSetDiff(val dependents: DependentsSet?, val constants: MutableMap<String?, IntSet?>?)
}
