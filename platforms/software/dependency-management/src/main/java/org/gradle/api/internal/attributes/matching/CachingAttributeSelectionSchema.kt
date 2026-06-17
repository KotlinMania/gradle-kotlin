/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.api.internal.attributes.matching

import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.model.InMemoryCacheFactory
import org.gradle.internal.model.InMemoryLoadingCache
import java.util.function.Function

/**
 * Caches results of a delegate [AttributeSelectionSchema]. Not all methods
 * are cached, as we only want to add caching to methods that have been proven
 * to be expensive.
 */
class CachingAttributeSelectionSchema(
    private val delegate: AttributeSelectionSchema,
    cacheFactory: InMemoryCacheFactory
) : AttributeSelectionSchema {
    private val extraAttributesCache: InMemoryLoadingCache<ExtraAttributesKey, Array<Attribute<*>>>
    private val matchValueCache: InMemoryLoadingCache<MatchValueKey<*>, Boolean>

    init {
        this.extraAttributesCache = cacheFactory.create<ExtraAttributesKey, Array<Attribute<*>>>(Function { key: ExtraAttributesKey -> this.doCollectExtraAttributes(key) })
        this.matchValueCache = cacheFactory.create<MatchValueKey<*>, Boolean>(Function { key: MatchValueKey<*> -> this.doMatchValue(key) })
    }

    override fun hasAttribute(attribute: Attribute<*>): Boolean {
        return delegate.hasAttribute(attribute)
    }

    override fun <T> disambiguate(attribute: Attribute<T?>, requested: T?, candidates: MutableSet<T?>): MutableSet<T?>? {
        return delegate.disambiguate<T?>(attribute, requested, candidates)
    }

    override fun <T> matchValue(attribute: Attribute<T?>, requested: T?, candidate: T?): Boolean {
        return matchValueCache.get(MatchValueKey<T?>(attribute, requested, candidate))
    }

    private fun <T> doMatchValue(key: MatchValueKey<T?>): Boolean {
        return delegate.matchValue<T?>(key.attribute, key.requested, key.candidate)
    }

    private class MatchValueKey<T>(private val attribute: Attribute<T?>, private val requested: T?, private val candidate: T?) {
        private val hashCode: Int

        init {
            this.hashCode = computeHashCode()
        }

        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as MatchValueKey<*>
            return attribute == that.attribute &&
                    requested == that.requested &&
                    candidate == that.candidate
        }

        override fun hashCode(): Int {
            return hashCode
        }

        fun computeHashCode(): Int {
            var result = attribute.hashCode()
            result = 31 * result + requested.hashCode()
            result = 31 * result + candidate.hashCode()
            return result
        }
    }

    override fun getAttribute(name: String): Attribute<*> {
        return delegate.getAttribute(name)!!
    }

    override fun collectExtraAttributes(candidateAttributeSets: Array<ImmutableAttributes>, requested: ImmutableAttributes): Array<Attribute<*>> {
        // TODO: Evaluate whether we still need this cache
        val entry = ExtraAttributesKey(candidateAttributeSets, requested)
        return extraAttributesCache.get(entry)
    }

    private fun doCollectExtraAttributes(key: ExtraAttributesKey): Array<Attribute<*>> {
        return delegate.collectExtraAttributes(key.candidates, key.requested)
    }

    private class ExtraAttributesKey(private val candidates: Array<ImmutableAttributes>, private val requested: ImmutableAttributes) {
        private val hashCode: Int

        init {
            this.hashCode = computeHashCode(candidates, requested)
        }

        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            // We leverage identity here, as we intern ImmutableAttributes instances.
            // In some cases this may lead to false negatives e.g. for attribute sets created
            // in different orders. a->foo,b->bar and b->bar,a->foo will .equals each other but
            // will not == each other.
            val that = o as ExtraAttributesKey
            if (requested !== that.requested) {
                return false
            }
            if (candidates.size != that.candidates.size) {
                return false
            }
            for (i in candidates.indices) {
                if (candidates[i] !== that.candidates[i]) {
                    return false
                }
            }
            return true
        }

        override fun hashCode(): Int {
            return hashCode
        }

        companion object {
            private fun computeHashCode(candidates: Array<ImmutableAttributes>, requested: ImmutableAttributes): Int {
                var hash = requested.hashCode()
                for (candidate in candidates) {
                    hash = 31 * hash + candidate.hashCode()
                }
                return hash
            }
        }
    }

    override fun orderByPrecedence(requested: MutableCollection<Attribute<*>>): AttributeSelectionSchema.PrecedenceResult {
        return delegate.orderByPrecedence(requested)
    }
}
