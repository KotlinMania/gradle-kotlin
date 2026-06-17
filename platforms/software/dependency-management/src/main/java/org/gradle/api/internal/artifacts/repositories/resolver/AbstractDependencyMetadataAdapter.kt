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
package org.gradle.api.internal.artifacts.repositories.resolver

import org.gradle.api.Action
import org.gradle.api.artifacts.DependencyMetadata
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.getGroup
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.getModule
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.getModuleIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.external.model.ModuleDependencyMetadata
import org.gradle.internal.component.external.model.VariantMetadataRules.getAttributes
import org.gradle.internal.component.model.ForcingDependencyMetadata

abstract class AbstractDependencyMetadataAdapter<T : DependencyMetadata<T?>?>(private val attributesFactory: AttributesFactory, var metadata: ModuleDependencyMetadata) : DependencyMetadata<T?> {
    protected fun updateMetadata(modifiedMetadata: ModuleDependencyMetadata) {
        this.metadata = modifiedMetadata
    }

    override fun getGroup(): String {
        return this.metadata.selector.getGroup()
    }

    override fun getName(): String {
        return this.metadata.selector.getModule()
    }

    override fun getVersionConstraint(): VersionConstraint {
        return this.metadata.selector.getVersionConstraint()
    }

    override fun version(configureAction: Action<in MutableVersionConstraint>): T? {
        val mutableVersionConstraint = DefaultMutableVersionConstraint(getVersionConstraint())
        configureAction.execute(mutableVersionConstraint)
        updateMetadata(this.metadata.withRequestedVersion(mutableVersionConstraint)!!)
        return uncheckedCast<T?>(this)
    }

    override fun because(reason: String): T? {
        updateMetadata(this.metadata.withReason(reason)!!)
        return uncheckedCast<T?>(this)
    }

    override fun getModule(): ModuleIdentifier {
        return this.metadata.selector.getModuleIdentifier()
    }

    override fun getReason(): String {
        return this.metadata.reason!!
    }

    override fun toString(): String {
        return getGroup() + ":" + getName() + ":" + getVersionConstraint()
    }

    override fun getAttributes(): AttributeContainer {
        return this.metadata.selector.getAttributes()
    }

    override fun attributes(configureAction: Action<in AttributeContainer>): T? {
        val selector: ModuleComponentSelector = this.metadata.selector
        val attributes = attributesFactory.mutable(selector.getAttributes() as AttributeContainerInternal)
        configureAction.execute(attributes)
        val target = DefaultModuleComponentSelector.newSelector(selector.getModuleIdentifier(), selector.getVersionConstraint(), attributes!!.asImmutable(), selector.getCapabilitySelectors())
        val metadata = this.metadata.withTarget(target) as ModuleDependencyMetadata?
        updateMetadata(metadata!!)
        return uncheckedCast<T?>(this)
    }

    fun forced() {
        val originalMetadata = this.metadata
        if (originalMetadata is ForcingDependencyMetadata) {
            updateMetadata(((originalMetadata as org.gradle.internal.component.model.ForcingDependencyMetadata).forced() as org.gradle.internal.component.external.model.ModuleDependencyMetadata?)!!)
        }
    }
}
