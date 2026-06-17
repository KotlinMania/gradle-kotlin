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
package org.gradle.api.internal.artifacts.dependencies

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.capability.DefaultSpecificCapabilitySelector
import org.gradle.api.internal.artifacts.capability.FeatureCapabilitySelector
import org.gradle.api.internal.artifacts.capability.SpecificCapabilitySelector
import org.gradle.api.internal.capabilities.CapabilityInternal
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.component.external.model.ProjectDerivedCapability

class DefaultProjectDependency(private val projectState: ProjectState) : AbstractModuleDependency(), ProjectDependencyInternal {
    override fun getPath(): String {
        return getTargetProjectIdentity().getProjectPath().asString()
    }

    override fun getGroup(): String {
        return unsafeGetProject().getGroup().toString()
    }

    override fun getName(): String {
        return getTargetProjectIdentity().getProjectName()
    }

    override fun getVersion(): String {
        return unsafeGetProject().getVersion().toString()
    }

    override fun getTargetProjectIdentity(): ProjectIdentity {
        return projectState.getIdentity()
    }

    override fun copy(): ProjectDependency {
        val copiedProjectDependency = DefaultProjectDependency(projectState)
        copyTo(copiedProjectDependency)
        return copiedProjectDependency
    }

    @Suppress("deprecation")
    override fun getRequestedCapabilities(): MutableList<Capability> {
        return getCapabilitySelectors().stream()
            .map<CapabilityInternal> { c: CapabilitySelector? ->
                if (c is SpecificCapabilitySelector) {
                    return@map (c as DefaultSpecificCapabilitySelector).getBackingCapability()
                } else if (c is FeatureCapabilitySelector) {
                    return@map ProjectDerivedCapability(unsafeGetProject(), c.getFeatureName())
                } else {
                    throw UnsupportedOperationException("Unsupported capability selector type: " + c!!.javaClass.getName())
                }
            }
            .collect(ImmutableList.toImmutableList<Capability>())
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultProjectDependency
        return getTargetProjectIdentity() == that.getTargetProjectIdentity() &&
                isCommonContentEquals(that)
    }

    override fun hashCode(): Int {
        var hashCode = getTargetProjectIdentity().hashCode()
        if (getTargetConfiguration() != null) {
            hashCode = 31 * hashCode + getTargetConfiguration().hashCode()
        }
        return hashCode
    }

    override fun toString(): String {
        return "project '" + getTargetProjectIdentity().getBuildTreePath() + "'"
    }

    /**
     * Any code which depends on this method should be deprecated and removed.
     *
     *
     * A project dependency should be a simple wrapper around the _identity_ of a given
     * project, and should not retain any reference to the actual project instance.
     */
    @Deprecated("")
    private fun unsafeGetProject(): ProjectInternal {
        return projectState.getMutableModel()
    }
}
