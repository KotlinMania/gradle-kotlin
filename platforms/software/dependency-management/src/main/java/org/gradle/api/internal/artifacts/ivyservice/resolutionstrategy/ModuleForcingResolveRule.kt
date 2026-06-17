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
package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy

import org.gradle.api.Action
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons

class ModuleForcingResolveRule(forcedModules: MutableCollection<out ModuleComponentSelector>) : Action<DependencySubstitutionInternal?> {
    private val forcedModules: MutableMap<ModuleIdentifier, String>?

    init {
        if (!forcedModules.isEmpty()) {
            this.forcedModules = HashMap<ModuleIdentifier, String>()
            for (module in forcedModules) {
                this.forcedModules.put(module.getModuleIdentifier(), module.getVersion())
            }
        } else {
            this.forcedModules = null
        }
    }

    override fun execute(details: DependencySubstitutionInternal) {
        if (forcedModules == null) {
            return
        }
        if (details.getRequested() is ModuleComponentSelector) {
            val selector = details.getRequested() as ModuleComponentSelector
            val key = selector.getModuleIdentifier()
            if (forcedModules.containsKey(key)) {
                val versionConstraint = DefaultImmutableVersionConstraint(forcedModules.get(key)!!)
                details.useTarget(newSelector(key, versionConstraint, selector.getAttributes(), selector.getCapabilitySelectors()), ComponentSelectionReasons.FORCED)
            }
        }
    }
}
