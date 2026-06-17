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
package org.gradle.internal.component.external.model

import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.internal.component.model.DelegatingDependencyMetadata
import org.gradle.internal.component.model.DependencyMetadata

class ModuleDependencyMetadataWrapper(private val delegate: DependencyMetadata) : DelegatingDependencyMetadata(delegate), ModuleDependencyMetadata {
    override fun withRequestedVersion(requestedVersion: VersionConstraint): ModuleDependencyMetadata {
        val selector: ModuleComponentSelector = getSelector()
        val newSelector = DefaultModuleComponentSelector.newSelector(selector.getModuleIdentifier(), requestedVersion, selector.getAttributes(), selector.getCapabilitySelectors())
        return ModuleDependencyMetadataWrapper(delegate.withTarget(newSelector)!!)
    }

    override fun withReason(reason: String): ModuleDependencyMetadata {
        return ModuleDependencyMetadataWrapper(delegate.withReason(reason)!!)
    }

    override fun withEndorseStrictVersions(endorse: Boolean): ModuleDependencyMetadata {
        if (delegate is ModuleDependencyMetadata) {
            return ModuleDependencyMetadataWrapper(delegate.withEndorseStrictVersions(endorse))
        }
        return this
    }

    override fun getSelector(): ModuleComponentSelector {
        return delegate.selector as ModuleComponentSelector
    }
}
