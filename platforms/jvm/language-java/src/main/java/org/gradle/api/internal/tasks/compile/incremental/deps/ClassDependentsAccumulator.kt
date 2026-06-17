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

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableMap
import it.unimi.dsi.fastutil.ints.IntSet
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet.Companion.dependencyToAll
import org.gradle.internal.hash.HashCode

class ClassDependentsAccumulator {
    private val dependenciesToAll: MutableMap<String?, String?> = HashMap<String?, String?>()
    private val privateDependents: MutableMap<String?, MutableSet<String?>?> = HashMap<String?, MutableSet<String?>?>()
    private val accessibleDependents: MutableMap<String?, MutableSet<String?>?> = HashMap<String?, MutableSet<String?>?>()
    private val classesToConstants = ImmutableMap.builder<String?, IntSet?>()
    private val seenClasses: MutableMap<String?, HashCode?> = HashMap<String?, HashCode?>()
    private var fullRebuildCause: String? = null

    fun addClass(classAnalysis: ClassAnalysis, hashCode: HashCode?) {
        addClass(
            classAnalysis.getClassName(),
            hashCode,
            classAnalysis.getDependencyToAllReason(),
            classAnalysis.getPrivateClassDependencies(),
            classAnalysis.getAccessibleClassDependencies(),
            classAnalysis.getConstants()
        )
    }

    fun addClass(className: String, hash: HashCode?, dependencyToAllReason: String?, privateClassDependencies: Iterable<String>, accessibleClassDependencies: Iterable<String>, constants: IntSet) {
        if (seenClasses.containsKey(className)) {
            // same classes may be found in different classpath trees/jars
            // and we keep only the first one
            return
        }
        seenClasses.put(className, hash)
        if (!constants.isEmpty()) {
            classesToConstants.put(className, constants)
        }
        if (dependencyToAllReason != null) {
            dependenciesToAll.put(className, dependencyToAllReason)
            privateDependents.remove(className)
            accessibleDependents.remove(className)
        }
        for (dependency in privateClassDependencies) {
            if (dependency != className && !dependenciesToAll.containsKey(dependency)) {
                addDependency(privateDependents, dependency, className)
            }
        }
        for (dependency in accessibleClassDependencies) {
            if (dependency != className && !dependenciesToAll.containsKey(dependency)) {
                addDependency(accessibleDependents, dependency, className)
            }
        }
    }

    private fun rememberClass(dependents: MutableMap<String?, MutableSet<String?>?>, className: String?): MutableSet<String?> {
        var d = dependents.get(className)
        if (d == null) {
            d = HashSet<String?>()
            dependents.put(className, d)
        }
        return d
    }

    @get:VisibleForTesting
    val dependentsMap: MutableMap<String?, DependentsSet?>
        get() {
            if (dependenciesToAll.isEmpty() && privateDependents.isEmpty() && accessibleDependents.isEmpty()) {
                return mutableMapOf<String?, DependentsSet?>()
            }
            val builder =
                ImmutableMap.builder<String?, DependentsSet?>()
            for (entry in dependenciesToAll.entries) {
                builder.put(entry.key, dependencyToAll(entry.value))
            }
            val collected: MutableSet<String?> = HashSet<String?>()
            for (entry in accessibleDependents.entries) {
                if (collected.add(entry.key)) {
                    builder.put(
                        entry.key,
                        DependentsSet.dependentClasses(
                            privateDependents.getOrDefault(
                                entry.key,
                                kotlin.collections.mutableSetOf<kotlin.String?>()
                            )!!, entry.value!!
                        )
                    )
                }
            }
            for (entry in privateDependents.entries) {
                if (collected.add(entry.key)) {
                    builder.put(
                        entry.key,
                        DependentsSet.dependentClasses(
                            entry.value!!,
                            accessibleDependents.getOrDefault(entry.key, mutableSetOf<String?>())!!
                        )
                    )
                }
            }
            return builder.build()
        }

    @VisibleForTesting
    fun getClassesToConstants(): MutableMap<String?, IntSet?> {
        return classesToConstants.build()
    }

    private fun addDependency(dependentsMap: MutableMap<String?, MutableSet<String?>?>, dependency: String?, dependent: String?) {
        val dependents = rememberClass(dependentsMap, dependency)
        dependents.add(dependent)
    }

    fun fullRebuildNeeded(fullRebuildCause: String?) {
        this.fullRebuildCause = fullRebuildCause
    }

    val analysis: ClassSetAnalysisData
        get() {
            if (fullRebuildCause == null) {
                return ClassSetAnalysisData(
                    ImmutableMap.copyOf<String?, HashCode?>(
                        seenClasses
                    ), this.dependentsMap, getClassesToConstants(), null
                )
            } else {
                return ClassSetAnalysisData(
                    ImmutableMap.of<String?, HashCode?>(),
                    ImmutableMap.of<String?, DependentsSet?>(),
                    ImmutableMap.of<String?, IntSet?>(),
                    fullRebuildCause
                )
            }
        }
}
