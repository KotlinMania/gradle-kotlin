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
package org.gradle.internal.component.model

import com.google.common.collect.Sets
import org.gradle.api.internal.attributes.MultipleCandidatesResult

class DefaultMultipleCandidateResult<T>(consumerValue: T?, candidateValues: MutableSet<T?>) : MultipleCandidatesResult<T?> {
    private val candidateValues: MutableSet<T?>
    private val consumerValue: T?

    // Match recording is optimized for the general case of a single match
    private var singleMatch: T? = null
    private var multipleMatches: MutableSet<T?>? = null

    init {
        require(!(candidateValues.isEmpty() || (consumerValue != null && candidateValues.size == 1))) { "Insufficient number of candidate values: " + candidateValues.size }
        for (candidateValue in candidateValues) {
            requireNotNull(candidateValue) { "candidateValues cannot contain null elements" }
        }

        this.candidateValues = candidateValues
        this.consumerValue = consumerValue
    }

    override fun hasResult(): Boolean {
        return singleMatch != null || multipleMatches != null
    }

    override fun getMatches(): MutableSet<T?> {
        assert(hasResult())
        if (singleMatch != null) {
            return mutableSetOf<T?>(singleMatch)
        }
        return multipleMatches!!
    }

    override fun getConsumerValue(): T? {
        return consumerValue
    }

    override fun getCandidateValues(): MutableSet<T?> {
        return candidateValues
    }

    override fun closestMatch(candidate: T?) {
        if (singleMatch == null) {
            if (multipleMatches == null) {
                singleMatch = candidate
            } else {
                multipleMatches!!.add(candidate)
            }
            return
        }
        if (singleMatch == candidate) {
            return
        }
        multipleMatches = Sets.newHashSetWithExpectedSize<T?>(candidateValues.size)
        multipleMatches!!.add(singleMatch)
        multipleMatches!!.add(candidate)
        singleMatch = null
    }
}
