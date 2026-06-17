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
package org.gradle.internal.locking

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.dsl.dependencies.LockEntryFilter

internal object LockEntryFilterFactory {
    val FILTERS_NONE: LockEntryFilter = LockEntryFilter { element: ModuleComponentIdentifier? -> false }
    private val FILTERS_ALL = LockEntryFilter { element: ModuleComponentIdentifier? -> true }

    private const val WILDCARD_SUFFIX = "*"
    const val MODULE_SEPARATOR: String = ":"

    fun forParameter(dependencyNotations: MutableList<String>, context: String, allowFullWildcard: Boolean): LockEntryFilter {
        if (dependencyNotations.isEmpty()) {
            return FILTERS_NONE
        }
        val lockEntryFilters = HashSet<LockEntryFilter>()
        for (lockExcludes in dependencyNotations) {
            for (lockExclude in lockExcludes.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
                val split = lockExclude.split(MODULE_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                validateNotation(lockExclude, split, allowFullWildcard, context)

                lockEntryFilters.add(createFilter(split[0], split[1]))
            }
            if (lockEntryFilters.isEmpty()) {
                throwInvalid(lockExcludes, context)
            }
        }

        if (lockEntryFilters.size == 1) {
            return lockEntryFilters.iterator().next()
        } else {
            return AggregateLockEntryFilter(lockEntryFilters)
        }
    }

    private fun throwInvalid(lockExclude: String, context: String) {
        throw IllegalArgumentException(context + " format must be <group>:<artifact> but '" + lockExclude + "' is invalid.")
    }

    private fun createFilter(group: String, module: String): LockEntryFilter {
        if (group == WILDCARD_SUFFIX && module == WILDCARD_SUFFIX) {
            return FILTERS_ALL
        }

        return GroupModuleLockEntryFilter(group, module)
    }

    private fun validateNotation(lockExclude: String, split: Array<String>, allowFullWildcard: Boolean, context: String) {
        if (split.size != 2) {
            throwInvalid(lockExclude, context)
        }
        val group = split[0]
        val module = split[1]

        if ((group.contains(WILDCARD_SUFFIX) && !group.endsWith(WILDCARD_SUFFIX)) || (module.contains(WILDCARD_SUFFIX) && !module.endsWith(WILDCARD_SUFFIX))) {
            throwInvalid(lockExclude, context)
        }

        if (!allowFullWildcard && group == WILDCARD_SUFFIX && module == WILDCARD_SUFFIX) {
            throwInvalid(lockExclude, context)
        }
    }

    fun combine(firstFilter: LockEntryFilter, secondFilter: LockEntryFilter): LockEntryFilter {
        if (firstFilter === FILTERS_NONE) {
            return secondFilter
        }
        if (secondFilter === FILTERS_NONE) {
            return firstFilter
        }
        if (firstFilter === FILTERS_ALL) {
            return firstFilter
        }
        if (secondFilter === FILTERS_ALL) {
            return secondFilter
        }

        return AggregateLockEntryFilter(ImmutableSet.of<LockEntryFilter>(firstFilter, secondFilter))
    }

    private class AggregateLockEntryFilter(private val filters: MutableSet<LockEntryFilter>) : LockEntryFilter {
        override fun isSatisfiedBy(moduleComponentIdentifier: ModuleComponentIdentifier): Boolean {
            for (filter in filters) {
                if (filter.isSatisfiedBy(moduleComponentIdentifier)) {
                    return true
                }
            }
            return false
        }
    }

    private class GroupModuleLockEntryFilter(private val group: String, private val module: String) : LockEntryFilter {
        override fun isSatisfiedBy(id: ModuleComponentIdentifier): Boolean {
            return matches(group, id.getGroup()) && matches(module, id.getModule())
        }

        fun matches(test: String, candidate: String): Boolean {
            if (test.endsWith(WILDCARD_SUFFIX)) {
                return candidate.startsWith(test.substring(0, test.length - 1))
            }
            return candidate == test
        }
    }
}
