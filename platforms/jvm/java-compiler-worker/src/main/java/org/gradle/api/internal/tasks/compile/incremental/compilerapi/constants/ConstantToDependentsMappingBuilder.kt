/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants

import com.google.common.collect.Iterables
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet
import java.io.Serializable
import java.util.function.Consumer

/**
 * A builder helper class to construct the ConstantToDependentsMapping
 */
class ConstantToDependentsMappingBuilder : Serializable {
    private val privateDependents: MutableMap<String?, MutableSet<String?>> = HashMap<String?, MutableSet<String?>>()
    private val accessibleDependents: MutableMap<String?, MutableSet<String?>> = HashMap<String?, MutableSet<String?>>()

    fun addAccessibleDependents(constantOrigin: String?, dependents: MutableCollection<String?>): ConstantToDependentsMappingBuilder {
        dependents.forEach(Consumer { dependent: String? -> addAccessibleDependent(constantOrigin, dependent) })
        return this
    }

    fun addPrivateDependents(constantOrigin: String?, dependents: MutableCollection<String?>): ConstantToDependentsMappingBuilder {
        dependents.forEach(Consumer { dependent: String? -> addPrivateDependent(constantOrigin, dependent) })
        return this
    }

    fun addPrivateDependent(constantOrigin: String?, dependent: String?): ConstantToDependentsMappingBuilder {
        val accessibleDependents = this.accessibleDependents.computeIfAbsent(constantOrigin) { k: String? -> HashSet<String?>() }
        val privateDependents = this.privateDependents.computeIfAbsent(constantOrigin) { k: String? -> HashSet<String?>() }
        if (!accessibleDependents.contains(dependent)) {
            privateDependents.add(dependent)
        }
        return this
    }

    fun addAccessibleDependent(constantOrigin: String?, dependent: String?): ConstantToDependentsMappingBuilder {
        val accessibleDependents = this.accessibleDependents.computeIfAbsent(constantOrigin) { k: String? -> HashSet<String?>() }
        val privateDependents = this.privateDependents.computeIfAbsent(constantOrigin) { k: String? -> HashSet<String?>() }
        accessibleDependents.add(dependent)
        privateDependents.remove(dependent)
        return this
    }

    fun build(): ConstantToDependentsMapping {
        val constantDependents: MutableMap<String?, DependentsSet?> = HashMap<String?, DependentsSet?>()
        for (constantOrigin in Iterables.concat<String?>(privateDependents.keys, accessibleDependents.keys)) {
            val privateDependents = this.privateDependents.getOrDefault(constantOrigin, mutableSetOf<String?>())
            val accessibleDependents = this.accessibleDependents.getOrDefault(constantOrigin, mutableSetOf<String?>())
            constantDependents.put(constantOrigin, DependentsSet.Companion.dependentClasses(privateDependents, accessibleDependents))
        }
        return ConstantToDependentsMapping(constantDependents)
    }
}
