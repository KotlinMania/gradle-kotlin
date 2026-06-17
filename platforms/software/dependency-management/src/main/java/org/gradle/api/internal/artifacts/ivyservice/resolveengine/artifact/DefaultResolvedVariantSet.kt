/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import com.google.common.collect.ImmutableList
import org.gradle.api.Describable
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.transform.ResolvedVariantTransformer
import org.gradle.api.internal.artifacts.transform.VariantDefinition
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.internal.Describables

/**
 * Default implementation of [ResolvedVariantSet].
 */
class DefaultResolvedVariantSet(
    val componentIdentifier: ComponentIdentifier,
    val producerSchema: ImmutableAttributesSchema,
    val overriddenAttributes: ImmutableAttributes,
    val candidates: ImmutableList<ResolvedVariant?>?,
// Services
    private val transformer: ResolvedVariantTransformer
) : ResolvedVariantSet {
    init {
        this.transformer = transformer
    }

    override fun toString(): String {
        return asDescribable()!!.getDisplayName()
    }

    override fun asDescribable(): Describable? {
        return Describables.of(componentIdentifier)
    }

    override fun transformCandidate(
        candidate: ResolvedVariant,
        variantDefinition: VariantDefinition
    ): ResolvedArtifactSet? {
        return transformer.transform(componentIdentifier, candidate, variantDefinition)
    }
}
