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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeEverything
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeNothing
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupSetExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdSetExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude
import org.gradle.internal.collect.PersistentSet
import org.gradle.internal.component.model.IvyArtifactName

interface ExcludeFactory {
    fun nothing(): ExcludeNothing?

    fun everything(): ExcludeEverything?

    fun group(group: String?): GroupExclude?

    fun module(module: String?): ModuleExclude?

    fun moduleId(id: ModuleIdentifier?): ModuleIdExclude?

    fun anyOf(one: ExcludeSpec?, two: ExcludeSpec?): ExcludeSpec?

    fun allOf(one: ExcludeSpec?, two: ExcludeSpec?): ExcludeSpec?

    fun anyOf(specs: PersistentSet<ExcludeSpec?>?): ExcludeSpec?

    fun allOf(specs: PersistentSet<ExcludeSpec?>?): ExcludeSpec?

    fun ivyPatternExclude(moduleId: ModuleIdentifier?, artifact: IvyArtifactName?, matcher: String?): ExcludeSpec?

    fun moduleIdSet(modules: PersistentSet<ModuleIdentifier?>?): ModuleIdSetExclude?

    fun groupSet(groups: PersistentSet<String?>?): GroupSetExclude?

    fun moduleSet(modules: PersistentSet<String?>?): ModuleSetExclude?

    fun fromUnion(remainder: PersistentSet<ExcludeSpec?>): ExcludeSpec? {
        if (remainder.isEmpty()) {
            // It's an intersection, and this method is always called on the remainder
            // of a reduction operation. If the remainder is empty then it means that
            // the intersection is empty
            return nothing()
        }
        return if (remainder.size() == 1) remainder.iterator().next() else anyOf(remainder)
    }

    fun fromModuleIds(common: PersistentSet<ModuleIdentifier?>): ExcludeSpec? {
        if (common.isEmpty()) {
            return nothing()
        }
        if (common.size() == 1) {
            return moduleId(common.iterator().next())
        }
        return moduleIdSet(common)
    }

    fun fromModules(moduleIds: PersistentSet<ModuleIdentifier?>): ExcludeSpec? {
        if (moduleIds.isEmpty()) {
            return nothing()
        }
        if (moduleIds.size() == 1) {
            return moduleId(moduleIds.iterator().next())
        }
        return moduleIdSet(moduleIds)
    }

    fun fromGroups(common: PersistentSet<String?>): ExcludeSpec? {
        if (common.isEmpty()) {
            return nothing()
        }
        if (common.size() == 1) {
            return group(common.iterator().next())
        }
        return groupSet(common)
    }
}
