/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.attributes

import org.gradle.api.Action
import org.gradle.api.attributes.MultipleCandidatesDetails
import java.lang.Boolean
import kotlin.Any
import kotlin.Comparator
import kotlin.Int

class DefaultOrderedDisambiguationRule<T>(private val comparator: Comparator<in T?>, private val pickFirst: Boolean) : Action<MultipleCandidatesDetails<T?>?> {
    override fun execute(details: MultipleCandidatesDetails<T?>) {
        val candidateValues = details.getCandidateValues()
        var min: T? = null
        var max: T? = null
        for (value in candidateValues) {
            if (min == null || comparator.compare(value, min) < 0) {
                min = value
            }
            if (max == null || comparator.compare(value, max) > 0) {
                max = value
            }
        }
        val cmp = if (pickFirst) min else max
        if (cmp != null) {
            for (value in candidateValues) {
                if (value == cmp) {
                    details.closestMatch(value)
                }
            }
        }
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultOrderedDisambiguationRule<*>
        return pickFirst == that.pickFirst && comparator == that.comparator
    }

    override fun hashCode(): Int {
        var result = comparator.hashCode()
        result = 31 * result + Boolean.hashCode(pickFirst)
        return result
    }
}
