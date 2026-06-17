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
import java.util.function.Function

/**
 * This factory is responsible for caching merging queries. It delegates computations
 * to another factory, so if the delegate returns the same instances for the same
 * queries, caching will be faster.
 */
class CachingExcludeFactory(delegate: ExcludeFactory?, private val caches: MergeCaches) : DelegatingExcludeFactory(delegate) {
    override fun anyOf(one: ExcludeSpec, two: ExcludeSpec): ExcludeSpec? {
        return cachedAnyPair(one, two)
    }

    private fun cachedAnyPair(left: ExcludeSpec, right: ExcludeSpec): ExcludeSpec? {
        return caches.getAnyPair(ExcludePair.Companion.of(left, right), Function { key: ExcludePair? -> delegate.anyOf(key.left, key.right) })
    }

    override fun allOf(one: ExcludeSpec, two: ExcludeSpec): ExcludeSpec? {
        return caches.getAllPair(ExcludePair.Companion.of(one, two), Function { key: ExcludePair? -> delegate.allOf(key.left, key.right) })
    }

    override fun anyOf(specs: PersistentSet<ExcludeSpec?>?): ExcludeSpec? {
        return caches.getAnyOf(specs, Function { specs: PersistentSet<ExcludeSpec?>? -> delegate.anyOf(specs) })
    }

    override fun allOf(specs: PersistentSet<ExcludeSpec?>?): ExcludeSpec? {
        return caches.getAllOf(specs, Function { specs: PersistentSet<ExcludeSpec?>? -> delegate.allOf(specs) })
    }

    /**
     * A special key which recognizes the fact union and intersection
     * are commutative.
     */
    private class ExcludePair(private val left: ExcludeSpec, private val right: ExcludeSpec) {
        private val hashCode: Int

        init {
            this.hashCode = 31 * left.hashCode() + right.hashCode()
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as ExcludePair

            return left == that.left && right == that.right
        }

        override fun hashCode(): Int {
            return hashCode
        }

        companion object {
            // Optimizes comparisons by making sure that the 2 elements of
            // the pair are "sorted" by hashcode ascending
            private fun of(left: ExcludeSpec, right: ExcludeSpec): ExcludePair {
                if (left.hashCode() > right.hashCode()) {
                    return ExcludePair(right, left)
                }
                return ExcludePair(left, right)
            }
        }
    }

    /**
     * A shareable backing cache for different caching exclude factories.
     * Synchronization is ad-hoc, since `computeIfAbsent` on a concurrent hash map
     * will not allow for recursion, which is the case for us whenever a cache is
     * found at different levels.
     */
    class MergeCaches {
        private val allOfPairCache: ConcurrentCache<ExcludePair?, ExcludeSpec?> = ConcurrentCache.Companion.of<ExcludePair?, ExcludeSpec?>()
        private val anyOfPairCache: ConcurrentCache<ExcludePair?, ExcludeSpec?> = ConcurrentCache.Companion.of<ExcludePair?, ExcludeSpec?>()
        private val allOfListCache: ConcurrentCache<PersistentSet<ExcludeSpec?>?, ExcludeSpec?> = ConcurrentCache.Companion.of<PersistentSet<ExcludeSpec?>?, ExcludeSpec?>()
        private val anyOfListCache: ConcurrentCache<PersistentSet<ExcludeSpec?>?, ExcludeSpec?> = ConcurrentCache.Companion.of<PersistentSet<ExcludeSpec?>?, ExcludeSpec?>()

        fun getAnyPair(pair: ExcludePair?, onMiss: Function<ExcludePair?, ExcludeSpec?>): ExcludeSpec? {
            return anyOfPairCache.computeIfAbsent(pair, onMiss)
        }

        fun getAllPair(pair: ExcludePair?, onMiss: Function<ExcludePair?, ExcludeSpec?>): ExcludeSpec? {
            return allOfPairCache.computeIfAbsent(pair, onMiss)
        }

        fun getAnyOf(list: PersistentSet<ExcludeSpec?>?, onMiss: Function<PersistentSet<ExcludeSpec?>?, ExcludeSpec?>): ExcludeSpec? {
            return anyOfListCache.computeIfAbsent(list, onMiss)
        }

        fun getAllOf(list: PersistentSet<ExcludeSpec?>?, onMiss: Function<PersistentSet<ExcludeSpec?>?, ExcludeSpec?>): ExcludeSpec? {
            return allOfListCache.computeIfAbsent(list, onMiss)
        }
    }

    private class ConcurrentCache<K, V> {
        private val backingMap: MutableMap<K?, V?> = HashMap<K?, V?>()

        fun computeIfAbsent(key: K?, producer: Function<K?, V?>): V? {
            synchronized(backingMap) {
                var value = backingMap.get(key)
                if (value != null) {
                    return value
                }
                value = producer.apply(key)
                backingMap.put(key, value)
                return value
            }
        }

        companion object {
            fun <K, V> of(): ConcurrentCache<K?, V?> {
                return ConcurrentCache<K?, V?>()
            }
        }
    }
}
