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
package org.gradle.api.internal.file.pattern

import java.util.LinkedList

abstract class PatternMatcher {
    abstract fun test(segments: Array<String?>?, isFile: Boolean): Boolean

    open fun and(other: PatternMatcher?): PatternMatcher? {
        return And(this@PatternMatcher, other)
    }

    open fun or(other: PatternMatcher?): PatternMatcher {
        return Or(this@PatternMatcher, other)
    }

    fun negate(): PatternMatcher {
        return object : PatternMatcher() {
            override fun test(segments: Array<String?>?, isFile: Boolean): Boolean {
                return !this@PatternMatcher.test(segments, isFile)
            }
        }
    }

    private class Or(patternMatcher: PatternMatcher?, other: PatternMatcher?) : PatternMatcher() {
        private val parts: MutableList<PatternMatcher> = LinkedList<PatternMatcher>()

        init {
            parts.add(patternMatcher!!)
            parts.add(other!!)
        }

        override fun or(other: PatternMatcher?): PatternMatcher {
            parts.add(other!!)
            return this
        }

        override fun test(segments: Array<String?>?, isFile: Boolean): Boolean {
            for (part in parts) {
                if (part.test(segments, isFile)) {
                    return true
                }
            }
            return false
        }
    }

    private class And(patternMatcher: PatternMatcher?, other: PatternMatcher?) : PatternMatcher() {
        private val parts: MutableList<PatternMatcher> = LinkedList<PatternMatcher>()

        init {
            parts.add(patternMatcher!!)
            parts.add(other!!)
        }

        override fun and(other: PatternMatcher?): PatternMatcher {
            parts.add(other!!)
            return this
        }

        override fun test(segments: Array<String?>?, isFile: Boolean): Boolean {
            for (part in parts) {
                if (!part.test(segments, isFile)) {
                    return false
                }
            }
            return true
        }
    }

    companion object {
        val MATCH_ALL: PatternMatcher = object : PatternMatcher() {
            override fun test(segments: Array<String?>?, isFile: Boolean): Boolean {
                return true
            }

            override fun and(other: PatternMatcher?): PatternMatcher? {
                return other
            }

            override fun or(other: PatternMatcher?): PatternMatcher {
                return this
            }
        }
    }
}
