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

class GreedyPathMatcher(private val next: PathMatcher) : PathMatcher {
    override fun toString(): String {
        return "{greedy next: " + next + "}"
    }

    override val maxSegments: Int
        get() = Int.MAX_VALUE

    override val minSegments: Int
        get() {
        return next.minSegments
    }

    override fun matches(segments: Array<String?>?, startIndex: Int): Boolean {
        val pathSegments = segments ?: return false
        var pos = pathSegments.size - next.minSegments
        val minPos = if (next.maxSegments == Int.MAX_VALUE) startIndex else maxOf(startIndex, pathSegments.size - next.maxSegments)
        while (pos >= minPos) {
            if (next.matches(pathSegments, pos)) {
                return true
            }
            pos--
        }
        return false
    }

    override fun isPrefix(segments: Array<String?>?, startIndex: Int): Boolean {
        return true
    }
}
