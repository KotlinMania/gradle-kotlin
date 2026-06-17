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
package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ArtifactSelectionDetails
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal
import org.gradle.api.internal.artifacts.dsl.ComponentSelectorParsers
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons
import org.gradle.internal.component.model.IvyArtifactName
import javax.inject.Inject

class DefaultDependencySubstitution @Inject constructor(
    private val componentSelectionDescriptorFactory: ComponentSelectionDescriptorFactory,
    private val requestedSelector: ComponentSelector,
    private val requestedArtifacts: ImmutableList<IvyArtifactName>
) : DependencySubstitutionInternal {
    var configuredTargetSelector: ComponentSelector? = null
        private set
    private var ruleDescriptors: MutableList<ComponentSelectionDescriptorInternal>? = null
    private var artifactSelectionDetails: ArtifactSelectionDetailsInternal? = null

    override fun getRequested(): ComponentSelector {
        return requestedSelector
    }

    override fun useTarget(notation: Any) {
        useTarget(notation, ComponentSelectionReasons.SELECTED_BY_RULE)
    }

    override fun useTarget(notation: Any, reason: String) {
        useTarget(notation, componentSelectionDescriptorFactory.newDescriptor(ComponentSelectionCause.SELECTED_BY_RULE, reason)!!)
    }

    override fun artifactSelection(action: Action<in ArtifactSelectionDetails>) {
        if (artifactSelectionDetails == null) {
            artifactSelectionDetails = DefaultArtifactSelectionDetails(requestedArtifacts)
        }
        action.execute(artifactSelectionDetails)
    }

    override fun useTarget(notation: Any, ruleDescriptor: ComponentSelectionDescriptor) {
        this.configuredTargetSelector = COMPONENT_SELECTOR_PARSER.parseNotation(notation)
        if (this.ruleDescriptors == null) {
            this.ruleDescriptors = ArrayList<ComponentSelectionDescriptorInternal>()
        }
        this.ruleDescriptors!!.add(ruleDescriptor as ComponentSelectionDescriptorInternal)
        Companion.validateTarget(this.configuredTargetSelector!!)
    }

    override fun getRuleDescriptors(): ImmutableList<ComponentSelectionDescriptorInternal>? {
        val hasConfiguredTarget = configuredTargetSelector != null
        val hasConfiguredArtifactSelectors = configuredArtifactSelectors != null

        if (!hasConfiguredTarget && !hasConfiguredArtifactSelectors) {
            return null
        }

        val builder = ImmutableList.builder<ComponentSelectionDescriptorInternal>()
        if (hasConfiguredTarget) {
            checkNotNull(ruleDescriptors)
            builder.addAll(ruleDescriptors!!)
        }

        if (hasConfiguredArtifactSelectors) {
            builder.add(ComponentSelectionReasons.SELECTED_BY_RULE)
        }

        return builder.build()
    }

    val configuredArtifactSelectors: ImmutableList<DependencyArtifactSelector>?
        get() = if (artifactSelectionDetails != null) artifactSelectionDetails!!.getConfiguredSelectors() else null

    companion object {
        private val COMPONENT_SELECTOR_PARSER = ComponentSelectorParsers.parser()

        fun validateTarget(componentSelector: ComponentSelector) {
            if (componentSelector is UnversionedModuleComponentSelector) {
                throw InvalidUserDataException("Must specify version for target of dependency substitution")
            }
        }
    }
}
