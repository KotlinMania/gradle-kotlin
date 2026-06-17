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
package org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableSet

/**
 * Provides a set of classes that depend on some other class.
 * If [.isDependencyToAll] returns true, then the dependent classes can't be enumerated.
 * In this case a description of the problem is available via [.getDescription].
 */
abstract class DependentsSet private constructor() {
    abstract val isEmpty: Boolean

    abstract fun hasDependentClasses(): Boolean

    @JvmField
    abstract val privateDependentClasses: MutableSet<String?>?

    @JvmField
    abstract val accessibleDependentClasses: MutableSet<String?>?

    @JvmField
    abstract val dependentResources: MutableSet<GeneratedResource?>?

    @JvmField
    abstract val isDependencyToAll: Boolean

    @JvmField
    abstract val description: String?

    @JvmField
    abstract val allDependentClasses: MutableSet<String?>?

    private class EmptyDependentsSet : DependentsSet() {
        override fun isEmpty(): Boolean {
            return true
        }

        override fun hasDependentClasses(): Boolean {
            return false
        }

        override fun getPrivateDependentClasses(): MutableSet<String?> {
            return mutableSetOf<String?>()
        }

        override fun getAccessibleDependentClasses(): MutableSet<String?> {
            return mutableSetOf<String?>()
        }

        override fun getAllDependentClasses(): MutableSet<String?> {
            return mutableSetOf<String?>()
        }

        override fun getDependentResources(): MutableSet<GeneratedResource?> {
            return mutableSetOf<GeneratedResource?>()
        }

        override fun isDependencyToAll(): Boolean {
            return false
        }

        override fun getDescription(): String? {
            throw UnsupportedOperationException("This dependents does not have a problem description.")
        }

        companion object {
            private val INSTANCE = EmptyDependentsSet()
        }
    }

    private class DefaultDependentsSet(
        private val privateDependentClasses: MutableSet<String?>,
        private val accessibleDependentClasses: MutableSet<String?>,
        private val dependentResources: MutableSet<GeneratedResource?>
    ) : DependentsSet() {
        override fun isEmpty(): Boolean {
            return !hasDependentClasses() && dependentResources.isEmpty()
        }

        override fun hasDependentClasses(): Boolean {
            return !privateDependentClasses.isEmpty() || !accessibleDependentClasses.isEmpty()
        }

        override fun getPrivateDependentClasses(): MutableSet<String?> {
            return privateDependentClasses
        }

        override fun getAccessibleDependentClasses(): MutableSet<String?> {
            return accessibleDependentClasses
        }

        override fun getAllDependentClasses(): MutableSet<String?> {
            if (privateDependentClasses.isEmpty()) {
                return accessibleDependentClasses
            }
            if (accessibleDependentClasses.isEmpty()) {
                return privateDependentClasses
            }
            val r: MutableSet<String?> = HashSet<String?>(accessibleDependentClasses)
            r.addAll(privateDependentClasses)
            return r
        }

        override fun getDependentResources(): MutableSet<GeneratedResource?> {
            return dependentResources
        }

        override fun isDependencyToAll(): Boolean {
            return false
        }

        override fun getDescription(): String? {
            throw UnsupportedOperationException("This dependents does not have a problem description.")
        }
    }

    private class DependencyToAll(reason: String? = null) : DependentsSet() {
        private val reason: String

        init {
            this.reason = Preconditions.checkNotNull<String>(reason)
        }

        override fun isEmpty(): Boolean {
            throw UnsupportedOperationException("This dependents set does not have dependent classes information.")
        }

        override fun hasDependentClasses(): Boolean {
            throw UnsupportedOperationException("This dependents set does not have dependent classes information.")
        }

        override fun getPrivateDependentClasses(): MutableSet<String?>? {
            throw UnsupportedOperationException("This dependents set does not have dependent classes information.")
        }

        override fun getAccessibleDependentClasses(): MutableSet<String?>? {
            throw UnsupportedOperationException("This dependents set does not have dependent classes information.")
        }

        override fun getAllDependentClasses(): MutableSet<String?>? {
            throw UnsupportedOperationException("This dependents set does not have dependent classes information.")
        }

        override fun getDependentResources(): MutableSet<GeneratedResource?>? {
            throw UnsupportedOperationException("This dependents set does not have dependent resources information.")
        }

        override fun isDependencyToAll(): Boolean {
            return true
        }

        override fun getDescription(): String {
            return reason
        }
    }

    companion object {
        fun dependentClasses(privateDependentClasses: MutableSet<String?>, accessibleDependentClasses: MutableSet<String?>): DependentsSet? {
            return dependents(privateDependentClasses, accessibleDependentClasses, mutableSetOf<GeneratedResource?>())
        }

        fun dependents(privateDependentClasses: MutableSet<String?>, accessibleDependentClasses: MutableSet<String?>, dependentResources: MutableSet<GeneratedResource?>): DependentsSet? {
            if (privateDependentClasses.isEmpty() && accessibleDependentClasses.isEmpty() && dependentResources.isEmpty()) {
                return empty()
            } else {
                return DefaultDependentsSet(
                    ImmutableSet.copyOf<String?>(privateDependentClasses),
                    ImmutableSet.copyOf<String?>(accessibleDependentClasses),
                    ImmutableSet.copyOf<GeneratedResource?>(dependentResources)
                )
            }
        }

        @JvmStatic
        fun dependencyToAll(reason: String?): DependentsSet {
            return DependencyToAll(reason)
        }

        @JvmStatic
        fun empty(): DependentsSet {
            return EmptyDependentsSet.Companion.INSTANCE
        }

        fun merge(sets: MutableCollection<DependentsSet>): DependentsSet? {
            if (sets.isEmpty()) {
                return empty()
            }
            if (sets.size == 1) {
                return sets.iterator().next()
            }
            var privateCount = 0
            var accessibleCount = 0
            var resourceCount = 0
            for (set in sets) {
                if (set.isDependencyToAll) {
                    return set
                }
                privateCount += set.privateDependentClasses!!.size
                accessibleCount += set.accessibleDependentClasses!!.size
                resourceCount += set.dependentResources!!.size
            }

            val privateDependentClasses = ImmutableSet.builderWithExpectedSize<String?>(privateCount)
            val accessibleDependentClasses = ImmutableSet.builderWithExpectedSize<String?>(accessibleCount)
            val dependentResources = ImmutableSet.builderWithExpectedSize<GeneratedResource?>(resourceCount)

            for (set in sets) {
                privateDependentClasses.addAll(set.privateDependentClasses!!)
                accessibleDependentClasses.addAll(set.accessibleDependentClasses!!)
                dependentResources.addAll(set.dependentResources!!)
            }
            return dependents(privateDependentClasses.build(), accessibleDependentClasses.build(), dependentResources.build())
        }
    }
}
