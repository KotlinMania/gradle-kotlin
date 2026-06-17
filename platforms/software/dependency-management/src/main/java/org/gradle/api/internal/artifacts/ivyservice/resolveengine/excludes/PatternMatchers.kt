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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes

import org.apache.ivy.plugins.matcher.ExactOrRegexpPatternMatcher
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import org.apache.ivy.plugins.matcher.GlobPatternMatcher
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.apache.ivy.plugins.matcher.RegexpPatternMatcher

class PatternMatchers private constructor() {
    private val matchers: MutableMap<String?, PatternMatcher?> = HashMap<String?, PatternMatcher?>()

    init {
        addMatcher(ExactPatternMatcher.INSTANCE)
        addMatcher(RegexpPatternMatcher.INSTANCE)
        addMatcher(ExactOrRegexpPatternMatcher.INSTANCE)
        addMatcher(GlobPatternMatcher.INSTANCE)
    }

    private fun addMatcher(instance: PatternMatcher) {
        matchers.put(instance.getName(), instance)
    }

    fun getMatcher(name: String?): PatternMatcher? {
        return matchers.get(name)
    }

    companion object {
        /**
         * 'exact' pattern matcher name
         */
        val EXACT: String = PatternMatcher.EXACT

        /**
         * Any expression string: '*'
         */
        const val ANY_EXPRESSION: String = "*"

        @get:Synchronized
        var instance: PatternMatchers? = null
            get() {
                if (field == null) {
                    field = PatternMatchers()
                }
                return field
            }
            private set

        fun isExactMatcher(name: String?): Boolean {
            return name == null || EXACT == name
        }
    }
}
