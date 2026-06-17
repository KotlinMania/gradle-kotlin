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
package org.gradle.api.internal.artifacts

import org.gradle.api.artifacts.DependencySubstitution
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor
import org.jspecify.annotations.NullMarked

@NullMarked
interface DependencySubstitutionInternal : DependencySubstitution {
    fun useTarget(notation: Any, ruleDescriptor: ComponentSelectionDescriptor)

    /**
     * Get the user-configured target, if any. Null if the user did not configure a target,
     * and the requested target should be used.
     */
    val configuredTargetSelector: ComponentSelector?

    /**
     * Get all descriptors describing the reasons for any substitutions performed.
     *
     *
     * Non-null and non-empty if any substitutions were performed.
     * Null if no substitutions were performed.
     */
    val ruleDescriptors: ImmutableList<ComponentSelectionDescriptorInternal>?

    /**
     * Returns the user-configured artifact selectors, if any. Null if the user did not
     * configure any artifact selectors and the requested artifact selectors should be used.
     */
    val configuredArtifactSelectors: ImmutableList<DependencyArtifactSelector>?

    val target: ComponentSelector
        /**
         * Get the target of the dependency substitution.
         */
        get() = if (this.configuredTargetSelector != null) this.configuredTargetSelector else getRequested()
}
