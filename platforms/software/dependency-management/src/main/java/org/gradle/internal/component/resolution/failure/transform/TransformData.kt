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

import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.internal.attributes.ImmutableAttributes

/**
 * A lightweight replacement for [TransformStep][org.gradle.api.internal.artifacts.transform.TransformStep]
 * that contains the data in each ArtifactTransform step that comprises an artifact transformation chain.
 *
 *
 * Immutable data class.  Meant to be easily serialized as part of build operation recording and tracing.
 *
 *
 * This type is also used as a part of a [TransformationChainData.TransformationChainFingerprint], and must
 * properly implement [.equals] and [.hashCode].
 */
class TransformData(
    private val transformActionClass: Class<out TransformAction<*>>,
    private val transformName: String,
    private val fromAttributes: ImmutableAttributes,
    private val toAttributes: ImmutableAttributes
) {
    fun getTransformActionClass(): Class<out TransformAction<*>> {
        return transformActionClass
    }

    fun getTransformName(): String {
        return transformName
    }

    /**
     * The set of attributes that will be modified by this Artifact Transform, with their original values.
     *
     * @return attributes as described
     */
    fun getFromAttributes(): ImmutableAttributes {
        return fromAttributes
    }

    /**
     * The set of attributes that will be modified by this Artifact Transform, with their resulting values.
     *
     * @return attributes as described
     */
    fun getToAttributes(): ImmutableAttributes {
        return toAttributes
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as TransformData
        return transformActionClass == that.transformActionClass && fromAttributes == that.fromAttributes && toAttributes == that.toAttributes
    }

    override fun hashCode(): Int {
        var result = transformActionClass.hashCode()
        result = 31 * result + fromAttributes.hashCode()
        result = 31 * result + toAttributes.hashCode()
        return result
    }
}
