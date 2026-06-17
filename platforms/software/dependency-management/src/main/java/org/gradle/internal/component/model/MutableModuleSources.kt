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

import org.gradle.internal.lazy.Lazy.Factory.of
import java.util.Optional
import java.util.function.Consumer

class MutableModuleSources : ModuleSources {
    private var moduleSources: MutableList<ModuleSource>? = null

    override fun <T : ModuleSource?> getSource(sourceType: Class<T?>): Optional<T?> {
        if (moduleSources == null) {
            return Optional.empty<T?>()
        }
        return org.gradle.internal.Cast.uncheckedCast<Optional<T?>?>(
            moduleSources.stream()
                .filter { src: org.gradle.internal.component.model.ModuleSource? -> sourceType.isAssignableFrom(src.javaClass) }
                .findFirst())!!
    }

    override fun withSources(consumer: Consumer<ModuleSource>) {
        if (moduleSources != null) {
            moduleSources.forEach(consumer)
        }
    }

    override fun size(): Int {
        return if (moduleSources == null) 0 else moduleSources!!.size
    }

    fun add(source: ModuleSource) {
        checkNotNull(source)
        maybeCreateStore()
        moduleSources!!.add(source)
    }

    private fun maybeCreateStore() {
        if (moduleSources == null) {
            moduleSources = ArrayList<ModuleSource>(2)
        }
    }

    fun asImmutable(): ImmutableModuleSources {
        if (moduleSources == null) {
            return ImmutableModuleSources.Companion.of()
        }
        return ImmutableModuleSources.Companion.of(*moduleSources.toTypedArray<ModuleSource>())
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as MutableModuleSources

        return moduleSources == that.moduleSources
    }

    override fun hashCode(): Int {
        return if (moduleSources != null) moduleSources.hashCode() else 0
    }

    companion object {
        @JvmStatic
        fun of(source: ModuleSource?): MutableModuleSources {
            val sources = MutableModuleSources()
            if (source != null) {
                sources.add(source)
            }
            return sources
        }

        @JvmStatic
        fun of(sources: ModuleSources): MutableModuleSources {
            if (sources is MutableModuleSources) {
                return sources
            }
            val mutableModuleSources = MutableModuleSources()
            if (sources == null) {
                return mutableModuleSources
            }
            sources.withSources(Consumer { source: ModuleSource? -> mutableModuleSources.add(source!!) })
            return mutableModuleSources
        }
    }
}
