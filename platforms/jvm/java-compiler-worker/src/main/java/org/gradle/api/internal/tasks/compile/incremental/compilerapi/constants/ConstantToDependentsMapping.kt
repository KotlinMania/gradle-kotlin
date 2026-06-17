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

import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet

class ConstantToDependentsMapping(@JvmField val constantDependents: MutableMap<String?, DependentsSet?>) {
    fun getConstantDependentsForClass(constantOrigin: String?): DependentsSet? {
        return constantDependents.getOrDefault(constantOrigin, DependentsSet.Companion.empty())
    }

    companion object {
        @JvmStatic
        fun empty(): ConstantToDependentsMapping {
            return ConstantToDependentsMapping(mutableMapOf<String?, DependentsSet?>())
        }

        @JvmStatic
        fun builder(): ConstantToDependentsMappingBuilder {
            return ConstantToDependentsMappingBuilder()
        }
    }
}
