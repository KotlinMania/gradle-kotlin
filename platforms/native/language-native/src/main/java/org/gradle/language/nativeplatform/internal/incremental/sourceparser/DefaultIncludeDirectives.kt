/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.nativeplatform.internal.incremental.sourceparser

import com.google.common.base.Function
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.Multimaps
import org.gradle.language.nativeplatform.internal.Include
import org.gradle.language.nativeplatform.internal.IncludeDirectives
import org.gradle.language.nativeplatform.internal.IncludeType
import org.gradle.language.nativeplatform.internal.Macro
import org.gradle.language.nativeplatform.internal.MacroFunction
import org.gradle.util.internal.CollectionUtils

class DefaultIncludeDirectives private constructor(
    private val allIncludes: ImmutableList<Include?>,
    private val macros: ImmutableListMultimap<String?, Macro?>,
    private val macroFunctions: ImmutableListMultimap<String?, MacroFunction?>
) : IncludeDirectives {
    override fun getQuotedIncludes(): MutableList<Include?> {
        return CollectionUtils.filter<Include?>(allIncludes, org.gradle.api.specs.Spec { element: Include? -> element!!.getType() == IncludeType.QUOTED })
    }

    override fun getSystemIncludes(): MutableList<Include?> {
        return CollectionUtils.filter<Include?>(allIncludes, org.gradle.api.specs.Spec { element: Include? -> element!!.getType() == IncludeType.SYSTEM })
    }

    override fun getMacroIncludes(): MutableList<Include?> {
        return CollectionUtils.filter<Include?>(allIncludes, org.gradle.api.specs.Spec { element: Include? -> element!!.getType() == IncludeType.MACRO })
    }

    override fun getAll(): MutableList<Include?> {
        return allIncludes
    }

    override fun getIncludesOnly(): MutableList<Include?> {
        return CollectionUtils.filter<Include?>(allIncludes, org.gradle.api.specs.Spec { element: Include? -> !element!!.isImport() })
    }

    override fun getMacros(name: String): Iterable<Macro?> {
        return macros.get(name)
    }

    override fun getAllMacros(): MutableCollection<Macro?> {
        return macros.values()
    }

    override fun getMacroFunctions(name: String): Iterable<MacroFunction?> {
        return macroFunctions.get(name)
    }

    override fun getAllMacroFunctions(): MutableCollection<MacroFunction?> {
        return macroFunctions.values()
    }

    override fun hasMacros(): Boolean {
        return !macros.isEmpty()
    }

    override fun hasMacroFunctions(): Boolean {
        return !macroFunctions.isEmpty()
    }

    override fun discardImports(): IncludeDirectives {
        return DefaultIncludeDirectives(ImmutableList.copyOf<Include?>(getIncludesOnly()), macros, macroFunctions)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultIncludeDirectives

        return allIncludes == that.allIncludes && macros == that.macros && macroFunctions == that.macroFunctions
    }

    override fun hashCode(): Int {
        return allIncludes.hashCode() xor macros.hashCode() xor macroFunctions.hashCode()
    }

    companion object {
        fun of(allIncludes: ImmutableList<Include?>, macros: ImmutableList<Macro?>, macroFunctions: ImmutableList<MacroFunction?>): IncludeDirectives {
            if (allIncludes.isEmpty() && macros.isEmpty() && macroFunctions.isEmpty()) {
                return IncludeDirectives.EMPTY
            }
            return DefaultIncludeDirectives(
                allIncludes,
                Multimaps.index<String?, Macro?>(macros, object : Function<Macro?, String?> {
                    override fun apply(input: Macro?): String? {
                        return input!!.getName()
                    }
                }),
                Multimaps.index<String?, MacroFunction?>(macroFunctions, object : Function<MacroFunction?, String?> {
                    override fun apply(input: MacroFunction?): String? {
                        return input!!.getName()
                    }
                })
            )
        }
    }
}
