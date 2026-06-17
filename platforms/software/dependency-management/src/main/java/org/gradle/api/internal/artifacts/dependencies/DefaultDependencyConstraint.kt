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
package org.gradle.api.internal.artifacts.dependencies

import com.google.common.base.Objects
import com.google.common.base.Strings
import org.gradle.api.Action
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ModuleVersionSelectorStrictSpec
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger

class DefaultDependencyConstraint : AbstractDependencyConstraint {
    private val moduleIdentifier: ModuleIdentifier
    private val versionConstraint: MutableVersionConstraint

    private var reason: String? = null
    private var attributesFactory: AttributesFactory? = null
    private var attributes: AttributeContainerInternal? = null
    private var force = false

    constructor(group: String, name: String, version: String) {
        this.moduleIdentifier = DefaultModuleIdentifier.newId(group, name)
        this.versionConstraint = DefaultMutableVersionConstraint(version)
    }

    constructor(module: ModuleIdentifier, versionConstraint: VersionConstraint) : this(module, DefaultMutableVersionConstraint(versionConstraint))

    private constructor(module: ModuleIdentifier, versionConstraint: MutableVersionConstraint) {
        this.moduleIdentifier = module
        this.versionConstraint = versionConstraint
    }

    override fun getGroup(): String? {
        return moduleIdentifier.getGroup()
    }

    override fun getName(): String {
        return moduleIdentifier.getName()
    }

    override fun getVersion(): String {
        return Strings.emptyToNull(versionConstraint.getRequiredVersion())!!
    }

    override fun getAttributes(): AttributeContainer {
        return if (attributes == null) ImmutableAttributes.EMPTY else attributes!!.asImmutable()
    }

    override fun attributes(configureAction: Action<in AttributeContainer>): DependencyConstraint {
        if (attributesFactory == null) {
            warnAboutInternalApiUse()
            return this
        }
        validateMutation()
        if (attributes == null) {
            attributes = attributesFactory!!.mutable()
        }
        configureAction.execute(attributes)
        return this
    }

    private fun warnAboutInternalApiUse() {
        LOG.warn("Cannot set attributes for constraint \"" + this.getGroup() + ":" + this.getName() + ":" + this.getVersion() + "\": it was probably created by a plugin using internal APIs")
    }

    fun setAttributesFactory(attributesFactory: AttributesFactory) {
        this.attributesFactory = attributesFactory
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultDependencyConstraint
        return Objects.equal(moduleIdentifier, that.moduleIdentifier) &&
                Objects.equal(versionConstraint, that.versionConstraint) &&
                Objects.equal(attributes, that.attributes) && force == that.force
    }

    override fun hashCode(): Int {
        return Objects.hashCode(moduleIdentifier, versionConstraint, attributes)
    }

    override fun version(configureAction: Action<in MutableVersionConstraint>) {
        validateMutation()
        configureAction.execute(versionConstraint)
    }

    override fun getVersionConstraint(): VersionConstraint {
        return versionConstraint
    }

    override fun matchesStrictly(identifier: ModuleVersionIdentifier): Boolean {
        return ModuleVersionSelectorStrictSpec(this).isSatisfiedBy(identifier)
    }

    override fun getModule(): ModuleIdentifier {
        return moduleIdentifier
    }

    override fun getReason(): String {
        return reason!!
    }

    override fun because(reason: String) {
        validateMutation()
        this.reason = reason
    }

    override fun copy(): DependencyConstraint {
        val constraint = DefaultDependencyConstraint(moduleIdentifier, versionConstraint)
        constraint.reason = reason
        constraint.attributes = attributes
        constraint.attributesFactory = attributesFactory
        constraint.force = force
        return constraint
    }

    override fun toString(): String {
        return "constraint " +
                moduleIdentifier + ":" + versionConstraint +
                ", attributes=" + attributes
    }

    override fun setForce(force: Boolean) {
        validateMutation()
        this.force = force
    }

    override fun isForce(): Boolean {
        return force
    }

    companion object {
        private val LOG: Logger = getLogger(DefaultDependencyConstraint::class.java)!!
    }
}
