/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.internal.artifacts.ComponentSelectionInternal
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.internal.rules.SpecRuleAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ComponentSelectionRulesProcessor {
    private val withNoInputs: Spec<SpecRuleAction<in ComponentSelection?>?> =
        org.gradle.api.specs.Spec { element: SpecRuleAction<in ComponentSelection?>? -> element!!.action!!.inputTypes!!.isEmpty() }
    private val withInputs: Spec<SpecRuleAction<in ComponentSelection?>?> = Specs.negate<SpecRuleAction<in ComponentSelection?>?>(withNoInputs)

    fun apply(selection: ComponentSelectionInternal, specRuleActions: MutableCollection<SpecRuleAction<in ComponentSelection?>>, metadataProvider: MetadataProvider) {
        if (processRules(specRuleActions, withNoInputs, selection, metadataProvider)) {
            processRules(specRuleActions, withInputs, selection, metadataProvider)
        }
    }

    private fun processRules(
        specRuleActions: MutableCollection<SpecRuleAction<in ComponentSelection?>>,
        filter: Spec<SpecRuleAction<in ComponentSelection?>?>,
        selection: ComponentSelectionInternal,
        metadataProvider: MetadataProvider
    ): Boolean {
        for (rule in specRuleActions) {
            if (filter.isSatisfiedBy(rule)) {
                processRule(rule, selection, metadataProvider)

                if (selection.isRejected) {
                    LOGGER.info("Selection of {} rejected by component selection rule: {}", selection.getCandidate().getDisplayName(), selection.rejectionReason)
                    return false
                }
            }
        }
        return true
    }

    private fun processRule(rule: SpecRuleAction<in ComponentSelection?>, selection: ComponentSelection, metadataProvider: MetadataProvider) {
        if (!rule.spec!!.isSatisfiedBy(selection)) {
            return
        }

        val inputValues = getInputValues(rule.action!!.inputTypes!!, metadataProvider)

        if (inputValues == null) {
            // Broken meta-data, bail
            return
        }

        if (inputValues.contains(null)) {
            // If any of the input values are not available for this selection, ignore the rule
            return
        }

        try {
            rule.action.execute(selection, inputValues)
        } catch (e: Exception) {
            throw InvalidUserCodeException(String.format("There was an error while evaluating a component selection rule for %s.", selection.getCandidate().getDisplayName()), e)
        }
    }

    private fun getInputValues(inputTypes: MutableList<Class<*>?>, metadataProvider: MetadataProvider): MutableList<Any?>? {
        if (inputTypes.size == 0) {
            return mutableListOf<Any?>()
        }

        if (!metadataProvider.isUsable()) {
            return null
        }

        val inputs: MutableList<Any?> = ArrayList<Any?>(inputTypes.size)
        for (inputType in inputTypes) {
            if (inputType == ComponentMetadata::class.java) {
                inputs.add(metadataProvider.getComponentMetadata())
                continue
            }
            if (inputType == IvyModuleDescriptor::class.java) {
                inputs.add(metadataProvider.getIvyModuleDescriptor())
                continue
            }
            // We've already validated the inputs: should never get here.
            throw IllegalStateException()
        }
        return inputs
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(ComponentSelectionRulesProcessor::class.java)
    }
}
