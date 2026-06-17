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
package org.gradle.internal.component.model

import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema

/**
 * Information about a locally resolved dependency.
 */
class LocalComponentDependencyMetadata(
    private val selector: ComponentSelector,
    private val dependencyConfiguration: String?,
    private val artifactNames: ImmutableList<IvyArtifactName>,
    private val excludes: ImmutableList<ExcludeMetadata>,
    private val force: Boolean, private val changing: Boolean, private val transitive: Boolean,
    private val constraint: Boolean, private val endorsing: Boolean, private val fromLock: Boolean,
    private val reason: String?
) : LocalOriginDependencyMetadata {
    constructor(
        selector: ComponentSelector,
        dependencyConfiguration: String?,
        artifactNames: ImmutableList<IvyArtifactName>,
        excludes: ImmutableList<ExcludeMetadata>,
        force: Boolean, changing: Boolean, transitive: Boolean, constraint: Boolean, endorsing: Boolean,
        reason: String?
    ) : this(selector, dependencyConfiguration, artifactNames, excludes, force, changing, transitive, constraint, endorsing, false, reason)

    override fun toString(): String {
        return selector.toString()
    }

    override fun getSelector(): ComponentSelector {
        return selector
    }

    override fun overrideVariantSelection(
        variantSelector: GraphVariantSelector,
        consumerAttributes: ImmutableAttributes,
        targetComponentState: ComponentGraphResolveState,
        consumerSchema: ImmutableAttributesSchema
    ): MutableList<out VariantGraphResolveState>? {
        if (dependencyConfiguration != null) {
            val selected = variantSelector.selectVariantByConfigurationName(
                dependencyConfiguration,
                consumerAttributes,
                targetComponentState,
                consumerSchema
            )

            return mutableListOf<VariantGraphResolveState>(selected)
        }

        return null
    }

    override fun getExcludes(): ImmutableList<ExcludeMetadata> {
        return excludes
    }

    override fun isChanging(): Boolean {
        return changing
    }

    override fun isTransitive(): Boolean {
        return transitive
    }

    override fun isForce(): Boolean {
        return force
    }

    override fun isConstraint(): Boolean {
        return constraint
    }

    override fun isEndorsingStrictVersions(): Boolean {
        return endorsing
    }

    override fun getReason(): String? {
        return reason
    }

    override fun getArtifacts(): ImmutableList<IvyArtifactName> {
        return artifactNames
    }

    override fun withTarget(target: ComponentSelector): LocalOriginDependencyMetadata {
        if (selector == target) {
            return this
        }
        return copyWithTarget(target)
    }

    override fun withTargetAndArtifacts(target: ComponentSelector, artifacts: ImmutableList<IvyArtifactName>): LocalOriginDependencyMetadata {
        if (selector == target && artifacts == getArtifacts()) {
            return this
        }
        return copyWithTargetAndArtifacts(target, artifacts)
    }

    override fun forced(): LocalOriginDependencyMetadata {
        if (force) {
            return this
        }
        return copyWithForce()
    }

    override fun isFromLock(): Boolean {
        return fromLock
    }

    override fun withReason(reason: String): DependencyMetadata {
        if (Objects.equal(reason, this.reason)) {
            return this
        }
        return copyWithReason(reason)
    }

    private fun copyWithTarget(selector: ComponentSelector): LocalOriginDependencyMetadata {
        return LocalComponentDependencyMetadata(selector, dependencyConfiguration, artifactNames, excludes, force, changing, transitive, constraint, endorsing, fromLock, reason)
    }

    private fun copyWithTargetAndArtifacts(selector: ComponentSelector, artifactNames: ImmutableList<IvyArtifactName>): LocalOriginDependencyMetadata {
        return LocalComponentDependencyMetadata(selector, dependencyConfiguration, artifactNames, excludes, force, changing, transitive, constraint, endorsing, fromLock, reason)
    }

    private fun copyWithReason(reason: String): LocalOriginDependencyMetadata {
        return LocalComponentDependencyMetadata(selector, dependencyConfiguration, artifactNames, excludes, force, changing, transitive, constraint, endorsing, fromLock, reason)
    }

    private fun copyWithForce(): LocalOriginDependencyMetadata {
        return LocalComponentDependencyMetadata(selector, dependencyConfiguration, artifactNames, excludes, true, changing, transitive, constraint, endorsing, fromLock, reason)
    }
}
