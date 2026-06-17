/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.component.resolution.failure.transform

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.attributes.ImmutableAttributes
import java.util.Objects
import java.util.stream.Collectors

/**
 * Represents a variant which is produced as the result of applying an artifact transform chain
 * to a root producer variant.
 *
 *
 * Immutable data class.  Meant to be easily serialized as part of build operation recording and tracing.
 */
class TransformationChainData(private val startingVariant: SourceVariantData, private val steps: ImmutableList<TransformData>, private val finalAttributes: ImmutableAttributes) {
    /**
     * The variant that was used as the starting point for this chain of transformations.
     *
     * @return initial variant
     */
    fun getInitialVariant(): SourceVariantData {
        return startingVariant
    }

    fun summarizeTransformations(): String {
        return steps.stream()
            .map<String> { t: TransformData? -> "'" + t!!.getTransformName() + "'" }
            .collect(Collectors.joining(" -> "))
    }

    fun getSteps(): ImmutableList<TransformData> {
        return steps
    }

    /**
     * The complete resulting set of attributes on the "virtual variant" created by processing the source variant
     * completely through this transformation chain.
     *
     *
     * This explicitly includes attributes of the source variant that were not modified by any transformations.
     *
     * @return attributes as described
     */
    fun getFinalAttributes(): ImmutableAttributes {
        return finalAttributes
    }

    /**
     * Obtain an object that represents this chain's distinct set of transformations such that it is equal to
     * any other chain containing the same set (**not sequence** - the
     * transforms can be in any order) of transforms from the same source variant.
     *
     *
     * Immutable data class.
     */
    fun fingerprint(): TransformationChainFingerprint {
        return TransformationChainFingerprint(this)
    }

    /**
     * Immutable data class representing a unique set (**not sequence** - the
     * transforms can be in any order) of transforms from a given source variant in a transformation chain.
     *
     *
     * This type must properly implement [.equals] and [.hashCode].
     */
    class TransformationChainFingerprint(chain: TransformationChainData) {
        private val startingVariant: SourceVariantData
        private val steps: ImmutableSet<TransformData>

        init {
            startingVariant = chain.startingVariant
            steps = ImmutableSet.copyOf<TransformData>(chain.steps)
        }

        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as TransformationChainFingerprint
            return startingVariant == that.startingVariant && steps == that.steps
        }

        override fun hashCode(): Int {
            var result = Objects.hashCode(startingVariant)
            result = 31 * result + steps.hashCode()
            return result
        }
    }
}
