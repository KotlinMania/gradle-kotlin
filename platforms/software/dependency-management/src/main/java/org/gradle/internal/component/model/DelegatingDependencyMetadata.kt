/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema

/**
 * A [DependencyMetadata] implementation which delegates all method calls to a provided `delegate`.
 */
abstract class DelegatingDependencyMetadata(private val delegate: DependencyMetadata) : DependencyMetadata {
    override fun getSelector(): ComponentSelector {
        return delegate.getSelector()
    }

    override fun overrideVariantSelection(
        variantSelector: GraphVariantSelector,
        consumerAttributes: ImmutableAttributes,
        targetComponentState: ComponentGraphResolveState,
        consumerSchema: ImmutableAttributesSchema
    ): MutableList<out VariantGraphResolveState>? {
        return delegate.overrideVariantSelection(variantSelector, consumerAttributes, targetComponentState, consumerSchema)
    }

    override fun selectLegacyVariants(
        variantSelector: GraphVariantSelector,
        consumerAttributes: ImmutableAttributes,
        targetComponentState: ComponentGraphResolveState,
        consumerSchema: ImmutableAttributesSchema
    ): MutableList<out VariantGraphResolveState> {
        return delegate.selectLegacyVariants(variantSelector, consumerAttributes, targetComponentState, consumerSchema)
    }

    override fun getExcludes(): ImmutableList<ExcludeMetadata> {
        return delegate.getExcludes()
    }

    override fun getArtifacts(): ImmutableList<IvyArtifactName> {
        return delegate.getArtifacts()
    }

    override fun withTarget(target: ComponentSelector): DependencyMetadata {
        return delegate.withTarget(target)
    }

    override fun withTargetAndArtifacts(target: ComponentSelector, artifacts: ImmutableList<IvyArtifactName>): DependencyMetadata {
        return delegate.withTargetAndArtifacts(target, artifacts)
    }

    override fun isChanging(): Boolean {
        return delegate.isChanging()
    }

    override fun isTransitive(): Boolean {
        return delegate.isTransitive()
    }

    override fun isConstraint(): Boolean {
        return delegate.isConstraint()
    }

    override fun isEndorsingStrictVersions(): Boolean {
        return delegate.isEndorsingStrictVersions()
    }

    override fun getReason(): String? {
        return delegate.getReason()
    }

    override fun withReason(reason: String): DependencyMetadata {
        return delegate.withReason(reason)
    }
}
