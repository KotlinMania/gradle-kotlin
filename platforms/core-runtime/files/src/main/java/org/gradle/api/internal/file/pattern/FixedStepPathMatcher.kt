/*
 * Copyright 2014 the original author or authors.
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

class FixedStepPathMatcher(private val step: PatternStep, private val next: PathMatcher) : PathMatcher {
    private val minSegments: Int
    private val maxSegments: Int

    init {
        minSegments = 1 + next.getMinSegments()
        maxSegments = if (next.getMaxSegments() == Int.MAX_VALUE) Int.MAX_VALUE else next.getMaxSegments() + 1
    }

    override fun toString(): String {
        return "{fixed-step: " + step + ", next: " + next + "}"
    }

    override fun getMinSegments(): Int {
        return minSegments
    }

    override fun getMaxSegments(): Int {
        return maxSegments
    }

    override fun matches(segments: Array<String?>, startIndex: Int): Boolean {
        val remaining = segments.size - startIndex
        if (remaining < minSegments || remaining > maxSegments) {
            return false
        }
        if (!step.matches(segments[startIndex])) {
            return false
        }
        return next.matches(segments, startIndex + 1)
    }

    override fun isPrefix(segments: Array<String?>, startIndex: Int): Boolean {
        if (startIndex == segments.size) {
            // Empty path, might match when more elements added
            return true
        }
        if (!step.matches(segments[startIndex])) {
            // Does not match element, will never match when more elements added
            return false
        }
        if (startIndex + 1 == segments.size) {
            // End of path, might match when more elements added
            return true
        }
        return next.isPrefix(segments, startIndex + 1)
    }
}
