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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.attributes.matching.AttributeMatchingCandidate

/**
 * Represents a variant which is produced as the result of applying an artifact transform chain
 * to a root producer variant.
 */
class TransformedVariant(
    /**
     * @return The root producer variant which the transform chain is applied to.
     */
    val root: ResolvedVariant,
    /**
     * @return The transformed variant which results from applying the transform chain to the root variant.
     */
    val transformedVariantDefinition: VariantDefinition
) : AttributeMatchingCandidate {
    val transformChain: TransformChain
        /**
         * @return The transform chain to apply to the root producer variant.
         */
        get() = transformedVariantDefinition.getTransformChain()

    val attributes: ImmutableAttributes
        get() = transformedVariantDefinition.getTargetAttributes()

    override fun toString(): String {
        return root.asDescribable().getDisplayName() + " <- " + this.transformedVariantDefinition + " = " + attributes
    }
}
