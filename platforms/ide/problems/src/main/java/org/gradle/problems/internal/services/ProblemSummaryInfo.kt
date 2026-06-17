/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.problems.internal.services

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet

internal class ProblemSummaryInfo {
    var count: Int = 0
        private set

    private val hashes: IntSet = IntOpenHashSet()

    fun increaseCount() {
        count++
    }

    fun addHash(hash: Int): Boolean {
        return hashes.add(hash)
    }

    fun shouldEmit(hash: Int, threshold: Int): Boolean {
        if (addHash(hash)) {
            increaseCount()
            //if we haven't seen this exact problem before, we should emit it if the count is below the threshold
            return this.count <= threshold
        }
        return false
    }
}
