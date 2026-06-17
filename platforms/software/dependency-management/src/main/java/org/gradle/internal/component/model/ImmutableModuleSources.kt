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
package org.gradle.internal.component.model

import org.gradle.internal.Cast.uncheckedCast
import java.util.Optional
import java.util.function.Consumer

class ImmutableModuleSources private constructor(previous: ImmutableModuleSources?, value: ModuleSource?) : ModuleSources {
    private val previous: ImmutableModuleSources?
    private val value: ModuleSource?
    private val hashCode: Int
    private val size: Int

    init {
        if (previous != null && value == null) {
            throw AssertionError("value must not be null")
        }
        this.previous = previous
        this.value = value
        this.hashCode = 31 * (if (previous == null) 0 else previous.hashCode) + (if (value == null) 0 else value.hashCode())
        this.size = if (previous == null) 0 else 1 + previous.size
    }

    override fun <T : ModuleSource?> getSource(sourceType: Class<T?>): Optional<T?> {
        if (previous == null) {
            return Optional.empty<T?>()
        }
        if (sourceType.isAssignableFrom(value!!.javaClass)) {
            val src = uncheckedCast<T?>(value)
            return Optional.of<T?>(src!!)
        }
        return previous.getSource<T?>(sourceType)
    }

    override fun withSources(consumer: Consumer<ModuleSource>) {
        var cur: ImmutableModuleSources? = this
        while (cur!!.value != null) {
            consumer.accept(cur.value)
            cur = cur.previous
        }
    }

    override fun size(): Int {
        return size
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as ImmutableModuleSources

        if (previous != that.previous) {
            return false
        }
        return value == that.value
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("{Sources = [")
        withSources(Consumer { src: ModuleSource -> sb.append(src).append(",") })
        sb.append("]}")
        return sb.toString()
    }

    companion object {
        private val EMPTY = ImmutableModuleSources(null, null)

        @JvmStatic
        fun of(): ImmutableModuleSources {
            return EMPTY
        }

        fun of(vararg sources: ModuleSource): ImmutableModuleSources {
            var cur: ImmutableModuleSources = EMPTY
            for (source in sources) {
                cur = ImmutableModuleSources(cur, source)
            }
            return cur
        }

        @JvmStatic
        fun of(sources: ModuleSources, source: ModuleSource): ImmutableModuleSources {
            if (sources is ImmutableModuleSources) {
                return ImmutableModuleSources(sources, source)
            }
            val all: MutableList<ModuleSource> = ArrayList<ModuleSource>()
            sources.withSources(Consumer { e: ModuleSource? -> all.add(e!!) })
            all.add(source)
            return of(*all.toTypedArray<ModuleSource>())
        }

        @JvmStatic
        fun of(sources: ModuleSources): ImmutableModuleSources {
            if (sources is ImmutableModuleSources) {
                return sources
            }
            return (sources as MutableModuleSources).asImmutable()
        }
    }
}
