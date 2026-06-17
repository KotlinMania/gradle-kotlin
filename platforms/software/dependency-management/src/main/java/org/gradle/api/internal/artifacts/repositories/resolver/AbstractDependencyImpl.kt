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
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Cast.uncheckedCast

abstract class AbstractDependencyImpl<T : DependencyMetadata<T?>?>(group: String, name: String, version: String) : DependencyMetadata<T?> {
    private val moduleIdentifier: ModuleIdentifier
    private val versionConstraint: MutableVersionConstraint
    private var reason: String? = null
    private var attributes: AttributeContainer = ImmutableAttributes.EMPTY

    init {
        this.moduleIdentifier = DefaultModuleIdentifier.newId(group, name)
        this.versionConstraint = DefaultMutableVersionConstraint(version)
    }

    override fun getGroup(): String {
        return moduleIdentifier.getGroup()
    }

    override fun getName(): String {
        return moduleIdentifier.getName()
    }

    override fun getVersionConstraint(): VersionConstraint {
        return this.versionConstraint
    }

    override fun getModule(): ModuleIdentifier {
        return moduleIdentifier
    }

    override fun version(configureAction: Action<in MutableVersionConstraint>): T? {
        configureAction.execute(versionConstraint)
        return uncheckedCast<T?>(this)
    }

    override fun attributes(configureAction: Action<in AttributeContainer>): T? {
        configureAction.execute(attributes)
        return uncheckedCast<T?>(this)
    }

    override fun getReason(): String {
        return reason!!
    }

    override fun because(reason: String): T? {
        this.reason = reason
        return uncheckedCast<T?>(this)
    }

    fun setAttributes(attributes: AttributeContainer) {
        this.attributes = attributes
    }

    override fun getAttributes(): AttributeContainer {
        return attributes
    }

    override fun toString(): String {
        return getGroup() + ":" + getName() + ":" + getVersionConstraint()
    }
}
