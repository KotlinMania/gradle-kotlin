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

import org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants.ConstantToDependentsMapping.Companion.builder
import java.util.function.Consumer

/**
 * Class used to merge new constants mapping from compiler api results with old results
 */
class ConstantToDependentsMappingMerger {
    fun merge(newMapping: ConstantToDependentsMapping, oldMapping: ConstantToDependentsMapping?, changedClasses: MutableSet<String?>): ConstantToDependentsMapping {
        var oldMapping = oldMapping
        if (oldMapping == null) {
            oldMapping = ConstantToDependentsMapping.empty()
        }
        return updateClassToConstantsMapping(newMapping, oldMapping, changedClasses)
    }

    private fun updateClassToConstantsMapping(newMapping: ConstantToDependentsMapping, oldMapping: ConstantToDependentsMapping, changedClasses: MutableSet<String?>): ConstantToDependentsMapping {
        val builder = builder()
        oldMapping.constantDependents.keys.stream()
            .filter { constantOrigin: String? -> !changedClasses.contains(constantOrigin) }
            .forEach { constantOrigin: String? ->
                val dependents = oldMapping.getConstantDependentsForClass(constantOrigin)
                val accessibleDependents: MutableSet<String?> = HashSet<String?>(dependents!!.accessibleDependentClasses)
                accessibleDependents.removeIf { o: String? -> changedClasses.contains(o) }
                builder.addAccessibleDependents(constantOrigin, accessibleDependents)
                val privateDependents: MutableSet<String?> = HashSet<String?>(dependents.privateDependentClasses)
                privateDependents.removeIf { o: String? -> changedClasses.contains(o) }
                builder.addPrivateDependents(constantOrigin, privateDependents)
            }
        newMapping.constantDependents.keys
            .forEach(Consumer { constantOrigin: String? ->
                val dependents = newMapping.getConstantDependentsForClass(constantOrigin)
                builder.addAccessibleDependents(constantOrigin, dependents!!.accessibleDependentClasses!!)
                builder.addPrivateDependents(constantOrigin, dependents.privateDependentClasses!!)
            })
        return builder.build()
    }
}
