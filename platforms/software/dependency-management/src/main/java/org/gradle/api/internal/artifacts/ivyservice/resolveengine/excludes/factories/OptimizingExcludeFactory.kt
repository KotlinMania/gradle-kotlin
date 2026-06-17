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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.internal.collect.PersistentSet
import java.util.function.BiFunction
import java.util.function.Function

/**
 * This factory is responsible for optimizing in special cases: null parameters,
 * list with 2 elements, ... and should be at the top of the delegation chain.
 */
class OptimizingExcludeFactory(delegate: ExcludeFactory?) : DelegatingExcludeFactory(delegate) {
    override fun anyOf(one: ExcludeSpec?, two: ExcludeSpec?): ExcludeSpec? {
        return Optimizations.optimizeAnyOf(one, two, BiFunction { one: ExcludeSpec?, two: ExcludeSpec? -> super.anyOf(one, two) })
    }

    override fun allOf(one: ExcludeSpec?, two: ExcludeSpec?): ExcludeSpec? {
        return Optimizations.optimizeAllOf(this, one, two, BiFunction { one: ExcludeSpec?, two: ExcludeSpec? -> super.allOf(one, two) })
    }

    override fun anyOf(specs: PersistentSet<ExcludeSpec?>): ExcludeSpec? {
        return Optimizations.optimizeCollection<PersistentSet<ExcludeSpec?>?>(this, specs, Function { set: PersistentSet<ExcludeSpec?>? ->
            if (set!!.size() == 2) {
                val it = set.iterator()
                // TODO: Return anyOf(set) to preserve the original set when it fails to optimize
                return@optimizeCollection delegate.anyOf(it.next(), it.next())
            }
            delegate.anyOf(set)
        })
    }

    override fun allOf(specs: PersistentSet<ExcludeSpec?>): ExcludeSpec? {
        return Optimizations.optimizeCollection<PersistentSet<ExcludeSpec?>?>(this, specs, Function { list: PersistentSet<ExcludeSpec?>? ->
            if (list!!.size() == 2) {
                val it = list.iterator()
                return@optimizeCollection delegate.allOf(it.next(), it.next())
            }
            delegate.allOf(list)
        })
    }
}
