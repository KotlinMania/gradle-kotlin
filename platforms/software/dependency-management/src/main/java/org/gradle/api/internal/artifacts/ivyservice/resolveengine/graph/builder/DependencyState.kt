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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ComponentSelectorConverter
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ForcingDependencyMetadata
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.internal.resolve.ModuleVersionResolveException

/**
 * A declared dependency, potentially transformed based on a substitution.
 */
class DependencyState(
    /**
     * The declared dependency this state is based off of, after substitution.
     */
    val dependency: DependencyMetadata,
    /**
     * The original requested component, before substitution.
     */
    val requested: ComponentSelector,
    /**
     * Describes the substitutions applied to this dependency, if any.
     */
    val ruleDescriptors: ImmutableList<ComponentSelectionDescriptorInternal>,
    /**
     * If non-null, the failure that occurred while trying to substitute this dependency.
     */
    val substitutionFailure: ModuleVersionResolveException?
) {
    private var moduleIdentifier: ModuleIdentifier? = null

    /**
     * Determine the module identifier of the component that this dependency targets.
     *
     *
     * This may resolve the target component. In practice all components do not necessarily belong
     * to a module, so we should avoid this method if possible. If possible, we should delay this
     * sort of functionality to _after_ we've resolved a selector to a component.
     */
    fun getModuleIdentifier(componentSelectorConverter: ComponentSelectorConverter): ModuleIdentifier {
        if (moduleIdentifier == null) {
            val componentSelector = dependency.selector
            if (componentSelector is ModuleComponentSelector) {
                moduleIdentifier = componentSelector.getModuleIdentifier()
            } else {
                moduleIdentifier = componentSelectorConverter.getModuleVersionId(componentSelector)!!.getModule()
            }
        }
        return moduleIdentifier!!
    }

    val isForced: Boolean
        get() {
            if (!ruleDescriptors.isEmpty()) {
                for (ruleDescriptor in ruleDescriptors) {
                    if (ruleDescriptor.isEquivalentToForce) {
                        return true
                    }
                }
            }

            return dependency is ForcingDependencyMetadata && dependency.isForce()
        }

    val isFromLock: Boolean
        get() = dependency is LocalOriginDependencyMetadata && dependency.isFromLock()

    override fun toString(): String {
        if (requested == dependency.selector) {
            return dependency.toString()
        } else {
            return dependency.toString() + " (requested " + requested + ")"
        }
    }
}
