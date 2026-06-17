/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.api.internal.artifacts.transform

import org.gradle.api.internal.attributes.ImmutableAttributes

/**
 * Default implementation of [VariantDefinition].
 */
class DefaultVariantDefinition(private val previous: DefaultVariantDefinition?, private val attributes: ImmutableAttributes, private val transformStep: TransformStep) : VariantDefinition {
    private val transformChain: TransformChain

    init {
        this.transformChain = TransformChain(if (previous == null) null else previous.getTransformChain(), transformStep)
    }

    override fun getTargetAttributes(): ImmutableAttributes {
        return attributes
    }

    override fun getTransformChain(): TransformChain {
        return transformChain
    }

    override fun getTransformStep(): TransformStep {
        return transformStep
    }

    override fun getPrevious(): VariantDefinition? {
        return previous
    }

    private val depth: Int
        get() = if (previous == null) 1 else previous.getDepth() + 1

    override fun toString(): String {
        if (previous != null) {
            return previous.toString() + " <- (" + this.depth + ") " + transformStep
        } else {
            return "(" + this.depth + ") " + transformStep
        }
    }
}
