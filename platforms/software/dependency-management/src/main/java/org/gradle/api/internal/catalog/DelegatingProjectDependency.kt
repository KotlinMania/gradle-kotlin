/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.catalog

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal
import org.gradle.api.internal.project.ProjectIdentity

@Suppress("unused")
class DelegatingProjectDependency(protected val factory: TypeSafeProjectDependencyFactory, private val delegate: ProjectDependencyInternal) : ProjectDependencyInternal {
    protected fun create(path: String): ProjectDependencyInternal {
        return factory.create(path)
    }

    override fun getTargetProjectIdentity(): ProjectIdentity {
        return delegate.getTargetProjectIdentity()
    }

    override fun getPath(): String {
        return delegate.getPath()
    }

    override fun copy(): ProjectDependency {
        return delegate.copy()
    }

    override fun exclude(excludeProperties: MutableMap<String, String>): ModuleDependency {
        return delegate.exclude(excludeProperties)
    }

    override fun getExcludeRules(): MutableSet<ExcludeRule> {
        return delegate.getExcludeRules()
    }

    override fun getArtifacts(): MutableSet<DependencyArtifact> {
        return delegate.getArtifacts()
    }

    override fun addArtifact(artifact: DependencyArtifact): ModuleDependency {
        return delegate.addArtifact(artifact)
    }

    override fun artifact(configureClosure: Closure<*>): DependencyArtifact {
        return delegate.artifact(configureClosure)
    }

    override fun artifact(configureAction: Action<in DependencyArtifact>): DependencyArtifact {
        return delegate.artifact(configureAction)
    }

    override fun isTransitive(): Boolean {
        return delegate.isTransitive()
    }

    override fun setTransitive(transitive: Boolean): ModuleDependency {
        return delegate.setTransitive(transitive)
    }

    override fun getTargetConfiguration(): String? {
        return delegate.getTargetConfiguration()
    }

    override fun setTargetConfiguration(name: String?) {
        delegate.setTargetConfiguration(name)
    }

    override fun getAttributes(): AttributeContainer {
        return delegate.getAttributes()
    }

    override fun attributes(configureAction: Action<in AttributeContainer>): ModuleDependency {
        return delegate.attributes(configureAction)
    }

    override fun capabilities(configureAction: Action<in ModuleDependencyCapabilitiesHandler>): ModuleDependency {
        return delegate.capabilities(configureAction)
    }

    override fun getRequestedCapabilities(): MutableList<Capability> {
        return delegate.getRequestedCapabilities()
    }

    override fun getCapabilitySelectors(): MutableSet<CapabilitySelector> {
        return delegate.getCapabilitySelectors()
    }

    override fun endorseStrictVersions() {
        delegate.endorseStrictVersions()
    }

    override fun doNotEndorseStrictVersions() {
        delegate.doNotEndorseStrictVersions()
    }

    override fun isEndorsingStrictVersions(): Boolean {
        return delegate.isEndorsingStrictVersions()
    }

    override fun getGroup(): String? {
        return delegate.getGroup()
    }

    override fun getName(): String {
        return delegate.getName()
    }

    override fun getVersion(): String? {
        return delegate.getVersion()
    }

    override fun getReason(): String? {
        return delegate.getReason()
    }

    override fun because(reason: String?) {
        delegate.because(reason)
    }

    override fun toString(): String {
        return delegate.toString()
    }
}
