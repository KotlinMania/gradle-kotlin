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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeEverything
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeNothing
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.internal.collect.PersistentSet
import java.util.function.BiFunction
import java.util.function.Function

object Optimizations {
    fun optimizeAnyOf(one: ExcludeSpec?, two: ExcludeSpec?, onMiss: BiFunction<ExcludeSpec?, ExcludeSpec?, ExcludeSpec?>): ExcludeSpec? {
        // fast path for two
        if (one == null) {
            return two
        }
        if (two == null) {
            return one
        }
        if (one == two) {
            return one
        }
        if (one is ExcludeEverything) {
            return one
        }
        if (one is ExcludeNothing) {
            return two
        }
        if (two is ExcludeEverything) {
            return two
        }
        if (two is ExcludeNothing) {
            return one
        }
        return onMiss.apply(one, two)
    }

    fun optimizeAllOf(factory: ExcludeFactory, one: ExcludeSpec?, two: ExcludeSpec?, onMiss: BiFunction<ExcludeSpec?, ExcludeSpec?, ExcludeSpec?>): ExcludeSpec? {
        // fast path for two
        if (one == null) {
            return two
        }
        if (two == null) {
            return one
        }
        if (one == two) {
            return one
        }
        if (one is ExcludeEverything) {
            return two
        }
        if (one is ExcludeNothing) {
            return factory.nothing()
        }
        if (two is ExcludeEverything) {
            return one
        }
        if (two is ExcludeNothing) {
            return factory.nothing()
        }
        return onMiss.apply(one, two)
    }

    fun <T : PersistentSet<ExcludeSpec?>?> optimizeCollection(factory: ExcludeFactory, specs: T?, onMiss: Function<T?, ExcludeSpec?>): ExcludeSpec? {
        if (specs!!.isEmpty()) {
            return factory.nothing()
        }
        if (specs.size() == 1) {
            return specs.iterator().next()
        }
        return onMiss.apply(specs)
    }
}
