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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.CompositeExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeAllOf
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeAnyOf
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeEverything
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeNothing
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupSetExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdSetExclude
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleSetExclude
import org.gradle.internal.collect.PersistentMap
import org.gradle.internal.collect.PersistentSet
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Predicate

/**
 * This factory performs normalization of exclude rules. This is the smartest
 * of all factories and is responsible for doing some basic algebra computations.
 * It shouldn't be too slow, or the whole chain will pay the price.
 */
class NormalizingExcludeFactory(delegate: ExcludeFactory?) : DelegatingExcludeFactory(delegate) {
    private val intersections: Intersections
    private val unions: Unions

    init {
        this.intersections = Intersections(this)
        this.unions = Unions(this)
    }

    override fun anyOf(one: ExcludeSpec, two: ExcludeSpec): ExcludeSpec? {
        return simplify(ExcludeAllOf::class.java, one, two, BiFunction { left: ExcludeSpec?, right: ExcludeSpec? -> doUnion(PersistentSet.of<ExcludeSpec?>(left, right)) })
    }

    override fun allOf(one: ExcludeSpec, two: ExcludeSpec): ExcludeSpec? {
        return simplify(ExcludeAnyOf::class.java, one, two, BiFunction { left: ExcludeSpec?, right: ExcludeSpec? -> doIntersect(PersistentSet.of<ExcludeSpec?>(left, right)) })
    }

    override fun anyOf(specs: PersistentSet<ExcludeSpec?>): ExcludeSpec? {
        return doUnion(specs)
    }

    override fun allOf(specs: PersistentSet<ExcludeSpec?>): ExcludeSpec? {
        return doIntersect(specs)
    }

    private fun doUnion(specs: PersistentSet<ExcludeSpec?>): ExcludeSpec? {
        var specs = specs
        specs = simplifySet(ExcludeAllOf::class.java, specs)
        val flattened: FlattenOperationResult
        ExcludeAnyOf > Companion.flatten<ExcludeAnyOf?>(
            ExcludeAnyOf::class.java,
            specs,
            Predicate { obj: ExcludeSpec? -> ExcludeEverything::class.java.isInstance(obj) },
            Predicate { obj: ExcludeSpec? -> ExcludeNothing::class.java.isInstance(obj) })
        if (flattened.fastExit) {
            return everything()
        }
        if (flattened.result.isEmpty()) {
            return nothing()
        }
        val byType: PersistentMap<UnionOf?, PersistentSet<ExcludeSpec?>?> = flattened.result.groupBy<UnionOf?>(Function { spec: ExcludeSpec? -> UnionOf.Companion.typeOf(spec) })
        val moduleIdExcludes = UnionOf.MODULE_ID.fromMap<ModuleIdExclude?>(byType)
        val moduleIdSetsExcludes = UnionOf.MODULE_ID_SET.fromMap<ModuleIdSetExclude?>(byType)
        val groupExcludes = UnionOf.GROUP.fromMap<GroupExclude?>(byType)
        val groupSetExcludes = UnionOf.GROUP_SET.fromMap<GroupSetExclude?>(byType)
        val moduleExcludes = UnionOf.MODULE.fromMap<ModuleExclude?>(byType)
        val moduleSetExcludes = UnionOf.MODULE_SET.fromMap<ModuleSetExclude?>(byType)
        val other = UnionOf.NOT_JOINABLE.fromMap<ExcludeSpec?>(byType)
        if (!moduleIdExcludes.isEmpty()) {
            // If there's more than one module id, merge them into a module id set
            if (moduleIdExcludes.size() > 1 || !moduleIdSetsExcludes.isEmpty()) {
                val excludeSpec = delegate.moduleIdSet(moduleIdExcludes.map<ModuleIdentifier?>(Function { obj: ModuleIdExclude? -> obj!!.getModuleId() }))
                if (moduleIdSetsExcludes.isEmpty()) {
                    moduleIdSetsExcludes = PersistentSet.of<ModuleIdSetExclude?>(excludeSpec)
                } else {
                    moduleIdSetsExcludes = moduleIdSetsExcludes.plus(excludeSpec)
                }
                moduleIdExcludes = PersistentSet.of<ModuleIdExclude?>()
            }
        }
        if (!groupExcludes.isEmpty()) {
            // If there's more than a group, merge them into a group set
            if (groupExcludes.size() > 1 || !groupSetExcludes.isEmpty()) {
                val excludeSpec = delegate.groupSet(groupExcludes.map<String?>(Function { obj: GroupExclude? -> obj!!.getGroup() }))
                if (groupSetExcludes.isEmpty()) {
                    groupSetExcludes = PersistentSet.of<GroupSetExclude?>(excludeSpec)
                } else {
                    groupSetExcludes = groupSetExcludes.plus(excludeSpec)
                }
                groupExcludes = PersistentSet.of<GroupExclude?>()
            }
        }
        if (!moduleExcludes.isEmpty()) {
            // If there's more than one module, merge them into a module set
            if (moduleExcludes.size() > 1 || !moduleSetExcludes.isEmpty()) {
                val excludeSpec = delegate.moduleSet(moduleExcludes.map<String?>(Function { obj: ModuleExclude? -> obj!!.getModule() }))
                if (moduleSetExcludes.isEmpty()) {
                    moduleSetExcludes = PersistentSet.of<ModuleSetExclude?>(excludeSpec)
                } else {
                    moduleSetExcludes = moduleSetExcludes.plus(excludeSpec)
                }
                moduleExcludes = PersistentSet.of<ModuleExclude?>()
            }
        }
        if (moduleIdSetsExcludes.size() > 1) {
            moduleIdSetsExcludes =
                PersistentSet.of<ModuleIdSetExclude?>(delegate.moduleIdSet(moduleIdSetsExcludes.flatMap<ModuleIdentifier?>(Function { obj: ModuleIdSetExclude? -> obj!!.getModuleIds() })))
        }
        if (groupSetExcludes.size() > 1) {
            groupSetExcludes = PersistentSet.of<GroupSetExclude?>(delegate.groupSet(groupSetExcludes.flatMap<String?>(Function { obj: GroupSetExclude? -> obj!!.getGroups() })))
        }
        if (moduleSetExcludes.size() > 1) {
            moduleSetExcludes = PersistentSet.of<ModuleSetExclude?>(delegate.moduleSet(moduleSetExcludes.flatMap<String?>(Function { obj: ModuleSetExclude? -> obj!!.getModules() })))
        }

        val elements = PersistentSet.of<ExcludeSpec?>()
            .union<ModuleIdExclude?>(moduleIdExcludes)
            .union<GroupExclude?>(groupExcludes)
            .union<ModuleExclude?>(moduleExcludes)
            .union<ModuleIdSetExclude?>(moduleIdSetsExcludes)
            .union<GroupSetExclude?>(groupSetExcludes)
            .union<ModuleSetExclude?>(moduleSetExcludes)
            .union<ExcludeSpec?>(other)

        elements = fixedPointOf(Simplification { left: ExcludeSpec?, right: ExcludeSpec?, specs: PersistentSet<ExcludeSpec?>? -> this.simplifyUnion(left!!, right!!, specs!!) }, elements)
        return Optimizations.optimizeCollection<PersistentSet<ExcludeSpec?>?>(this, elements, Function { specs: PersistentSet<ExcludeSpec?>? -> delegate.anyOf(specs) })
    }

    internal fun interface Simplification {
        fun apply(left: ExcludeSpec?, right: ExcludeSpec?, specs: PersistentSet<ExcludeSpec?>?): PersistentSet<ExcludeSpec?>
    }

    private fun simplifyUnion(left: ExcludeSpec, right: ExcludeSpec, specs: PersistentSet<ExcludeSpec?>): PersistentSet<ExcludeSpec?> {
        val merged = unions.tryUnion(left, right)
        if (merged != null) {
            if (merged is ExcludeEverything) {
                return PersistentSet.of<ExcludeSpec?>(merged)
            }
            val simplified = specs.minus(left).minus(right)
            if (merged is ExcludeAnyOf) {
                // Flatten it to its members, since we are building a union
                return simplified
                    .union<ExcludeSpec?>(merged.getComponents())
            }
            return simplified
                .plus(merged)
        }
        return specs
    }

    private fun doIntersect(specs: PersistentSet<ExcludeSpec?>): ExcludeSpec? {
        var specs = specs
        specs = simplifySet(ExcludeAnyOf::class.java, specs)
        val flattened: FlattenOperationResult
        ExcludeAllOf > Companion.flatten<ExcludeAllOf?>(
            ExcludeAllOf::class.java,
            specs,
            Predicate { obj: ExcludeSpec? -> ExcludeNothing::class.java.isInstance(obj) },
            Predicate { obj: ExcludeSpec? -> ExcludeEverything::class.java.isInstance(obj) })
        if (flattened.fastExit) {
            return nothing()
        }
        val result: PersistentSet<ExcludeSpec?> = flattened.result
        if (result.isEmpty()) {
            return everything()
        }
        result = fixedPointOf(Simplification { left: ExcludeSpec?, right: ExcludeSpec?, specs: PersistentSet<ExcludeSpec?>? -> this.simplifyIntersect(left!!, right!!, specs!!) }, result)
        return Optimizations.optimizeCollection<PersistentSet<ExcludeSpec?>?>(this, result, Function { specs: PersistentSet<ExcludeSpec?>? -> delegate.allOf(specs) })
    }

    private fun simplifyIntersect(left: ExcludeSpec, right: ExcludeSpec, specs: PersistentSet<ExcludeSpec?>): PersistentSet<ExcludeSpec?> {
        val merged = intersections.tryIntersect(left, right)
        if (merged != null) {
            if (merged is ExcludeNothing) {
                return PersistentSet.of<ExcludeSpec?>(merged)
            }
            return specs
                .minus(left)
                .minus(right)
                .plus(merged)
        }
        return specs
    }

    private enum class UnionOf(private val excludeClass: Class<out ExcludeSpec?>) {
        MODULE_ID(ModuleIdExclude::class.java),
        GROUP(GroupExclude::class.java),
        MODULE(ModuleExclude::class.java),
        MODULE_ID_SET(ModuleIdSetExclude::class.java),
        GROUP_SET(GroupSetExclude::class.java),
        MODULE_SET(ModuleSetExclude::class.java),
        NOT_JOINABLE(ExcludeSpec::class.java);

        fun <T : ExcludeSpec?> fromMap(from: PersistentMap<UnionOf?, PersistentSet<ExcludeSpec?>?>): PersistentSet<T?> {
            return org.gradle.internal.Cast.uncheckedCast<PersistentSet<T?>?>(
                from.getOrDefault(
                    this,
                    org.gradle.internal.collect.PersistentSet.of<org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec?>()
                )
            )!!
        }

        companion object {
            fun typeOf(spec: ExcludeSpec?): UnionOf? {
                for (unionOf in entries) {
                    if (unionOf.excludeClass.isInstance(spec)) {
                        return unionOf
                    }
                }
                return null
            }
        }
    }

    private class FlattenOperationResult(private val result: PersistentSet<ExcludeSpec?>, private val fastExit: Boolean) {
        companion object {
            private val FAST_EXIT = NormalizingExcludeFactory.FlattenOperationResult(null, true)
            fun of(specs: PersistentSet<ExcludeSpec?>): FlattenOperationResult {
                return FlattenOperationResult(specs, false)
            }
        }
    }

    companion object {
        // Simplifies (A ∪ ...) ∩ A = A
        // and  (A ∩ ...) ∪ A = A
        private fun simplify(clazz: Class<out CompositeExclude?>, one: ExcludeSpec, two: ExcludeSpec, orElse: BiFunction<ExcludeSpec?, ExcludeSpec?, ExcludeSpec?>): ExcludeSpec? {
            if (clazz.isInstance(one)) {
                if (componentsOf(one)!!.contains(two)) {
                    return two
                }
            }
            if (clazz.isInstance(two)) {
                if (componentsOf(two)!!.contains(one)) {
                    return one
                }
            }
            return orElse.apply(one, two)
        }

        // A ∩ (A ∪ B) ∩ (A ∪ C) -> A
        // A ∪ (A ∩ B) ∪ (A ∩ C) -> A
        private fun simplifySet(clazz: Class<out CompositeExclude?>, specs: PersistentSet<ExcludeSpec?>): PersistentSet<ExcludeSpec?> {
            return specs.filter(Predicate { left: ExcludeSpec? ->
                !clazz.isInstance(left)
                        || specs.noneMatch(Predicate { right: ExcludeSpec? -> Companion.componentsOf(left!!)!!.contains(right) })
            }
            )
        }

        private fun componentsOf(spec: ExcludeSpec): PersistentSet<ExcludeSpec?>? {
            return (spec as CompositeExclude).getComponents()
        }

        /**
         * Computes the fixed point of a simplification over a set of [ExcludeSpec] elements.
         *
         *
         * The supplied `function` takes two candidate elements and the current set of
         * elements, and may return either the unchanged set (no simplification possible) or a
         * new set where a simplification has been applied (for example, by replacing both inputs
         * with a simplified/merged element, or by removing redundant elements).
         *
         *
         * This method repeatedly applies the simplification until no further changes occur. In
         * other words, it searches for a set `S*` such that
         * `simplify(S*) = S*`, where `simplify` denotes one full iteration of trying
         * to simplify all pairs. This mirrors the notion of a fixed point of a function on sets:
         * given a set-transforming function `F`, a fixed point is a set `S` for which
         * `F(S) = S`. Here, `F` is the "one-pass attempt" to simplify the current set
         * by examining element pairs; we keep applying `F` until the set stops changing.
         *
         *
         * Implementation details:
         *
         *  * The algorithm iterates over pairs (`left`, `right`) from the snapshot of the
         * current set. If the simplification function returns a new set instance, iteration restarts
         * on that updated set. If a full pass causes no change, the current set is the fixed point
         * and is returned.
         *  * Termination is guaranteed because each successful simplification must either reduce the
         * number of elements or replace elements with a canonical merged element, and we only loop
         * while changes occur. Once no change is produced, the set equals its image under the
         * one-pass simplification function.
         *
         *
         * @param function the pairwise simplification to apply over the set
         * @param specs the initial set of elements to simplify
         * @return the fixed point set where another full simplification pass yields the same set
         */
        private fun fixedPointOf(function: Simplification, specs: PersistentSet<ExcludeSpec?>): PersistentSet<ExcludeSpec?> {
            var current = specs
            while (current.size() > 1) {
                val simplified: PersistentSet<ExcludeSpec?> = simplifyOnce(function, current)
                if (simplified === current) {
                    break
                }
                current = simplified
            }
            return current
        }

        private fun simplifyOnce(function: Simplification, specs: PersistentSet<ExcludeSpec?>): PersistentSet<ExcludeSpec?> {
            for (left in specs) {
                for (right in specs) {
                    if (left === right) {
                        continue
                    }
                    val simplified: PersistentSet<ExcludeSpec?> = function.apply(left, right, specs)
                    if (simplified !== specs) {
                        return simplified
                    }
                }
            }
            return specs
        }

        /**
         * Flattens a collection of elements that are going to be joined or intersected. There
         * are 3 possible outcomes:
         * - Null means that the fast exit condition was reached, meaning that the caller knows it's not worth computing more
         * - empty list meaning that an easy simplification was reached, and we directly know the result
         * - flattened unions/intersections
         */
        private fun <T : ExcludeSpec?> flatten(
            flattenType: Class<T?>,
            specs: PersistentSet<ExcludeSpec?>,
            fastExit: Predicate<ExcludeSpec?>,
            ignoreSpec: Predicate<ExcludeSpec?>
        ): FlattenOperationResult {
            val filtered = false
            val flatten = false
            for (spec in specs) {
                if (fastExit.test(spec)) {
                    return FlattenOperationResult.Companion.FAST_EXIT
                }
                if (ignoreSpec.test(spec)) {
                    filtered = true
                } else if (flattenType.isInstance(spec)) {
                    flatten = true
                }
            }
            if (!filtered && !flatten) {
                return FlattenOperationResult.Companion.of(specs)
            }
            if (filtered && !flatten) {
                return filterOnly(specs, ignoreSpec)
            }
            // slowest path
            return
            T > expensiveFlatten<T?>(flattenType, maybeFilter(specs, ignoreSpec, filtered))
        }

        private fun filterOnly(specs: PersistentSet<ExcludeSpec?>, ignoreSpec: Predicate<ExcludeSpec?>): FlattenOperationResult {
            return FlattenOperationResult.Companion.of(specs.filter(Predicate { e: ExcludeSpec? -> !ignoreSpec.test(e) }))
        }

        private fun maybeFilter(specs: PersistentSet<ExcludeSpec?>, ignoreSpec: Predicate<ExcludeSpec?>, filtered: Boolean): PersistentSet<ExcludeSpec?> {
            var stream = specs
            if (filtered) {
                stream = stream.filter(Predicate { e: ExcludeSpec? -> !ignoreSpec.test(e) })
            }
            return stream
        }

        private fun <T : ExcludeSpec?> expensiveFlatten(flattenType: Class<T?>, specs: PersistentSet<ExcludeSpec?>): FlattenOperationResult {
            return FlattenOperationResult.Companion.of(
                specs.flatMap<ExcludeSpec?>(Function { e: ExcludeSpec? ->
                    if (flattenType.isInstance(e)) {
                        val compositeExclude = e as CompositeExclude
                        return@flatMap compositeExclude.getComponents()
                    }
                    PersistentSet.of<ExcludeSpec?>(e)
                })
            )
        }
    }
}
