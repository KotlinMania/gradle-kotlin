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

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap
import org.gradle.language.nativeplatform.internal.Expression

/**
 * Captures the intermediate states during the resolution of macro #include directives, to short-circuit work that has already been done.
 */
internal class TokenLookup {
    private var broken: MutableSet<Expression?>? = null
    private var tokensFor: Multimap<Expression?, Expression?>? = null

    fun isUnresolved(expression: Expression?): Boolean {
        return broken != null && broken!!.contains(expression)
    }

    fun unresolved(expression: Expression?) {
        if (broken == null) {
            broken = HashSet<Expression?>()
        }
        broken!!.add(expression)
    }

    fun tokensFor(expression: Expression?): MutableCollection<Expression?> {
        if (tokensFor == null) {
            return mutableListOf<Expression?>()
        }
        return tokensFor!!.get(expression)
    }

    fun addTokensFor(expression: Expression?, tokens: Expression?) {
        if (tokensFor == null) {
            tokensFor = LinkedHashMultimap.create<Expression?, Expression?>()
        }
        tokensFor!!.put(expression, tokens)
    }

    fun hasTokensFor(expression: Expression?): Boolean {
        return tokensFor != null && tokensFor!!.containsKey(expression)
    }
}
