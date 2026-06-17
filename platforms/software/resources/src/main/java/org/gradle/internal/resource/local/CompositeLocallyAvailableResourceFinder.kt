/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal.resource.local

import org.gradle.internal.hash.HashCode
import java.util.LinkedList

class CompositeLocallyAvailableResourceFinder<C>(private val composites: MutableList<LocallyAvailableResourceFinder<C?>>) : LocallyAvailableResourceFinder<C?> {
    override fun findCandidates(criterion: C?): LocallyAvailableResourceCandidates {
        val allCandidates: MutableList<LocallyAvailableResourceCandidates> = LinkedList<LocallyAvailableResourceCandidates>()
        for (finder in composites) {
            allCandidates.add(finder.findCandidates(criterion))
        }

        return CompositeLocallyAvailableResourceCandidates(allCandidates)
    }

    private class CompositeLocallyAvailableResourceCandidates(private val allCandidates: MutableList<LocallyAvailableResourceCandidates>) : LocallyAvailableResourceCandidates {
        override fun isNone(): Boolean {
            for (candidates in allCandidates) {
                if (!candidates.isNone()) {
                    return false
                }
            }

            return true
        }

        override fun findByHashValue(hashValue: HashCode?): LocallyAvailableResource? {
            for (candidates in allCandidates) {
                val match = candidates.findByHashValue(hashValue)
                if (match != null) {
                    return match
                }
            }

            return null
        }
    }
}
