/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.language.nativeplatform.internal.incremental

import com.google.common.collect.Iterators
import org.gradle.language.nativeplatform.internal.IncludeDirectives
import java.io.File
import java.util.ArrayDeque
import java.util.Deque

class CollectingMacroLookup @JvmOverloads constructor(private val includeDirectives: IncludeDirectives? = IncludeDirectives.EMPTY) : MacroLookup {
    private val uncollected: Deque<MacroSource> = ArrayDeque<MacroSource>()
    private var visible: MutableMap<File?, IncludeDirectives?>? = null

    /**
     * Appends a single file.
     */
    fun append(file: File?, includeDirectives: IncludeDirectives) {
        if (!includeDirectives.hasMacros() && !includeDirectives.hasMacroFunctions()) {
            // Ignore
            return
        }
        if (visible == null) {
            visible = LinkedHashMap<File?, IncludeDirectives?>()
            visible!!.put(file, includeDirectives)
        } else if (!visible!!.containsKey(file)) {
            visible!!.put(file, includeDirectives)
        }
    }

    /**
     * Appends a source of macros
     */
    fun append(source: MacroSource?) {
        uncollected.add(source)
    }

    override fun iterator(): MutableIterator<IncludeDirectives?> {
        collectAll()

        val initialDirectives: MutableIterator<IncludeDirectives?> = Iterators.singletonIterator<IncludeDirectives?>(includeDirectives)
        if (visible == null || visible!!.isEmpty()) {
            return initialDirectives
        }
        return Iterators.concat<IncludeDirectives?>(initialDirectives, visible!!.values.iterator())
    }

    fun appendTo(lookup: CollectingMacroLookup) {
        collectAll()
        if (visible != null) {
            for (entry in visible!!.entries) {
                lookup.append(entry.key, entry.value!!)
            }
        }
    }

    private fun collectAll() {
        while (!uncollected.isEmpty()) {
            val source = uncollected.removeFirst()
            source.collectInto(this)
        }
    }

    internal interface MacroSource {
        fun collectInto(lookup: CollectingMacroLookup?)
    }
}
