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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupSetExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdSetExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude
import org.gradle.internal.collect.PersistentSet
import java.util.function.Predicate

internal class Unions(private val factory: ExcludeFactory) {
    /**
     * Tries to compute an union of 2 specs.
     * The result MUST be a simplification, otherwise this method returns null.
     */
    fun tryUnion(left: ExcludeSpec, right: ExcludeSpec?): ExcludeSpec? {
        if (left == right) {
            return left
        }
        if (left is ModuleExclude) {
            return tryModuleUnion(left, right)
        } else if (right is ModuleExclude) {
            return tryModuleUnion(right, left)
        }
        if (left is GroupExclude) {
            return tryGroupUnion(left, right)
        } else if (right is GroupExclude) {
            return tryGroupUnion(right, left)
        }
        if (left is ModuleSetExclude) {
            return tryModuleSetUnion(left, right)
        } else if (right is ModuleSetExclude) {
            return tryModuleSetUnion(right, left)
        }
        if (left is GroupSetExclude) {
            return tryGroupSetUnion(left, right)
        } else if (right is GroupSetExclude) {
            return tryGroupSetUnion(right, left)
        }
        return null
    }

    private fun tryModuleUnion(left: ModuleExclude, right: ExcludeSpec?): ExcludeSpec? {
        val leftModule = left.getModule()
        if (right is ModuleIdExclude) {
            val mie = right
            if (mie.getModuleId().getName() == leftModule) {
                return left
            }
        }
        if (right is ModuleIdSetExclude) {
            val ids = right
            val items: PersistentSet<ModuleIdentifier?> = ids.getModuleIds().filter(Predicate { id: ModuleIdentifier? -> id!!.getName() != leftModule })
            if (items.size() == 1) {
                return factory.anyOf(left, factory.moduleId(items.iterator().next()))
            }
            if (items.isEmpty()) {
                return left
            }
            if (items.size() != ids.getModuleIds().size()) {
                return factory.anyOf(left, factory.moduleIdSet(items))
            }
        }
        return null
    }

    private fun tryGroupUnion(left: GroupExclude, right: ExcludeSpec?): ExcludeSpec? {
        val leftGroup = left.getGroup()
        if (right is ModuleIdExclude) {
            val mie = right
            if (mie.getModuleId().getGroup() == leftGroup) {
                return left
            }
        }
        if (right is ModuleIdSetExclude) {
            val ids = right
            val items: PersistentSet<ModuleIdentifier?> = ids.getModuleIds().filter(Predicate { id: ModuleIdentifier? -> id!!.getGroup() != leftGroup })
            if (items.size() == 1) {
                return factory.anyOf(left, factory.moduleId(items.iterator().next()))
            }
            if (items.isEmpty()) {
                return left
            }
            if (items.size() != ids.getModuleIds().size()) {
                return factory.anyOf(left, factory.moduleIdSet(items))
            }
        }
        return null
    }

    private fun tryModuleSetUnion(left: ModuleSetExclude, right: ExcludeSpec?): ExcludeSpec? {
        val leftModules: PersistentSet<String?> = left.getModules()
        if (right is ModuleIdExclude) {
            val mie = right
            if (leftModules.contains(mie.getModuleId().getName())) {
                return left
            }
        }
        if (right is ModuleIdSetExclude) {
            val ids = right
            val items: PersistentSet<ModuleIdentifier?> = ids.getModuleIds().filter(Predicate { id: ModuleIdentifier? -> !leftModules.contains(id!!.getName()) })
            if (items.size() == 1) {
                return factory.anyOf(left, factory.moduleId(items.iterator().next()))
            }
            if (items.isEmpty()) {
                return left
            }
            if (items.size() != ids.getModuleIds().size()) {
                return factory.anyOf(left, factory.moduleIdSet(items))
            }
        }
        return null
    }

    private fun tryGroupSetUnion(left: GroupSetExclude, right: ExcludeSpec?): ExcludeSpec? {
        val leftGroups: PersistentSet<String?> = left.getGroups()
        if (right is ModuleIdExclude) {
            val mie = right
            if (leftGroups.contains(mie.getModuleId().getGroup())) {
                return left
            }
        }
        if (right is ModuleIdSetExclude) {
            val ids = right
            val items: PersistentSet<ModuleIdentifier?> = ids.getModuleIds().filter(Predicate { id: ModuleIdentifier? -> !leftGroups.contains(id!!.getGroup()) })
            if (items.size() == 1) {
                return factory.anyOf(left, factory.moduleId(items.iterator().next()))
            }
            if (items.isEmpty()) {
                return left
            }
            if (items.size() != ids.getModuleIds().size()) {
                return factory.anyOf(left, factory.moduleIdSet(items))
            }
        }
        return null
    }
}
