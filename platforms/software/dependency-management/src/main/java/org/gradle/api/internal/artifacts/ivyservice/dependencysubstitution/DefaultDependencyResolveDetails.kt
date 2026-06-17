/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution

import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactSelectionDetails
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.DependencySubstitution
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.dsl.ModuleComponentSelectorParsers
import org.gradle.internal.Actions
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import javax.inject.Inject

class DefaultDependencyResolveDetails @Inject constructor(delegate: DependencySubstitutionInternal, requested: ModuleVersionIdentifier) : DependencyResolveDetails {
    private val delegate: DependencySubstitution
    private val requested: ModuleVersionIdentifier

    private var customDescription: String? = null
    private var useVersion: VersionConstraint? = null
    private var useSelector: ModuleComponentSelector? = null
    private var target: ComponentSelector
    private var artifactSelectionAction = Actions.doNothing<ArtifactSelectionDetails>()
    private var dirty = false

    init {
        this.delegate = delegate
        this.requested = requested
        this.target = if (delegate.configuredTargetSelector != null) delegate.configuredTargetSelector else delegate.getRequested()
    }

    override fun getRequested(): ModuleVersionSelector {
        return DefaultModuleVersionSelector.newSelector(requested)
    }

    override fun useVersion(version: String) {
        requireNotNull(version) { "Configuring the dependency resolve details with 'null' version is not allowed." }
        useSelector = null
        useVersion = DefaultMutableVersionConstraint(version)
        dirty = true
    }

    override fun useTarget(notation: Any) {
        useVersion = null
        useSelector = USE_TARGET_NOTATION_PARSER.parseNotation(notation)
        dirty = true
    }

    override fun because(description: String): DependencyResolveDetails {
        customDescription = description
        dirty = true
        return this
    }

    override fun artifactSelection(configurationAction: Action<in ArtifactSelectionDetails>): DependencyResolveDetails {
        artifactSelectionAction = Actions.composite<ArtifactSelectionDetails>(artifactSelectionAction, configurationAction)
        dirty = true
        return this
    }

    override fun getTarget(): ModuleVersionSelector {
        complete()

        if (target == delegate.getRequested()) {
            return DefaultModuleVersionSelector.newSelector(requested)
        }
        // The target may already be modified from the original requested
        if (target is ModuleComponentSelector) {
            return DefaultModuleVersionSelector.newSelector(target as ModuleComponentSelector)
        }
        // If the target is a project component, it has not been modified from the requested
        return DefaultModuleVersionSelector.newSelector(requested)
    }

    fun complete() {
        if (!dirty) {
            return
        }

        if (useSelector != null) {
            useTarget(useSelector!!)
        } else if (useVersion != null) {
            if (target is ModuleComponentSelector) {
                val moduleSelector = target as ModuleComponentSelector
                if (useVersion != moduleSelector.getVersionConstraint()) {
                    useTarget(DefaultModuleComponentSelector.newSelector(moduleSelector.getModuleIdentifier(), useVersion!!, moduleSelector.getAttributes(), moduleSelector.getCapabilitySelectors()))
                } else {
                    // Still 'updated' with reason when version remains the same.
                    useTarget(target)
                }
            } else {
                // If the current target is a project component, it must be unmodified from the requested
                val newTarget = DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(requested.getGroup(), requested.getName()), useVersion!!)
                useTarget(newTarget)
            }
        }

        delegate.artifactSelection(artifactSelectionAction)
        dirty = false
    }

    private fun useTarget(selector: ModuleComponentSelector) {
        this.target = selector
        if (customDescription != null) {
            delegate.useTarget(selector, customDescription!!)
        } else {
            delegate.useTarget(selector)
        }
    }

    companion object {
        private val USE_TARGET_NOTATION_PARSER = ModuleComponentSelectorParsers.parser("useTarget()")
    }
}
