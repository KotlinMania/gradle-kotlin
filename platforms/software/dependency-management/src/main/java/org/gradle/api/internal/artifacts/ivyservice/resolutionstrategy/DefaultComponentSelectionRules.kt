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
package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.ComponentSelectionRules
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.api.internal.notations.ModuleIdentifierNotationConverter
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.rules.DefaultRuleActionAdapter
import org.gradle.internal.rules.DefaultRuleActionValidator
import org.gradle.internal.rules.RuleAction
import org.gradle.internal.rules.RuleActionAdapter
import org.gradle.internal.rules.RuleActionValidator
import org.gradle.internal.rules.SpecRuleAction
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.internal.typeconversion.UnsupportedNotationException

class DefaultComponentSelectionRules protected constructor(moduleIdentifierFactory: ImmutableModuleIdentifierFactory, private val ruleActionAdapter: RuleActionAdapter) :
    ComponentSelectionRulesInternal {
    private var mutationValidator = MutationValidator.IGNORE
    private var rules: MutableSet<SpecRuleAction<in ComponentSelection>>? = null

    private val moduleIdentifierNotationParser: NotationParser<Any, ModuleIdentifier>

    constructor(moduleIdentifierFactory: ImmutableModuleIdentifierFactory) : this(moduleIdentifierFactory, createAdapter())

    init {
        this.moduleIdentifierNotationParser = NotationParserBuilder
            .toType<ModuleIdentifier>(ModuleIdentifier::class.java)
            .fromCharSequence(ModuleIdentifierNotationConverter(moduleIdentifierFactory))
            .toComposite()
    }

    /**
     * Sets the validator to invoke prior to each mutation.
     */
    fun setMutationValidator(mutationValidator: MutationValidator) {
        this.mutationValidator = mutationValidator
    }

    override fun getRules(): MutableCollection<SpecRuleAction<in ComponentSelection>> {
        return if (rules != null) rules else mutableSetOf<SpecRuleAction<in ComponentSelection?>>()
    }

    override fun all(selectionAction: Action<in ComponentSelection>): ComponentSelectionRules {
        return addRule(createAllSpecRulesAction(ruleActionAdapter.createFromAction<ComponentSelection?>(selectionAction)!!))
    }

    override fun all(closure: Closure<*>): ComponentSelectionRules {
        return addRule(createAllSpecRulesAction(ruleActionAdapter.createFromClosure<ComponentSelection?>(ComponentSelection::class.java, closure)!!))
    }

    @Deprecated("")
    override fun all(ruleSource: Any): ComponentSelectionRules {
        deprecateMethod(ComponentSelectionRules::class.java, "all(Object)")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "dependency_management_rules")!!
            .nagUser()
        return addRule(createAllSpecRulesAction(ruleActionAdapter.createFromRuleSource<ComponentSelection?>(ComponentSelection::class.java, ruleSource)!!))
    }

    override fun withModule(id: Any, selectionAction: Action<in ComponentSelection>): ComponentSelectionRules {
        return addRule(createSpecRuleActionFromId(id, ruleActionAdapter.createFromAction<ComponentSelection?>(selectionAction)!!))
    }

    override fun withModule(id: Any, closure: Closure<*>): ComponentSelectionRules {
        return addRule(createSpecRuleActionFromId(id, ruleActionAdapter.createFromClosure<ComponentSelection?>(ComponentSelection::class.java, closure)!!))
    }

    @Deprecated("")
    override fun withModule(id: Any, ruleSource: Any): ComponentSelectionRules {
        deprecateMethod(ComponentSelectionRules::class.java, "withModule(Object,Object)")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "dependency_management_rules")!!
            .nagUser()
        return addRule(createSpecRuleActionFromId(id, ruleActionAdapter.createFromRuleSource<ComponentSelection?>(ComponentSelection::class.java, ruleSource)!!))
    }

    override fun addRule(specRuleAction: SpecRuleAction<in ComponentSelection?>): ComponentSelectionRules {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)
        if (rules == null) {
            rules = LinkedHashSet<SpecRuleAction<in ComponentSelection>>()
        }
        rules!!.add(specRuleAction)
        return this
    }

    override fun addRule(specRuleAction: RuleAction<in ComponentSelection?>): ComponentSelectionRules {
        return addRule(createAllSpecRulesAction(specRuleAction))
    }

    private fun createSpecRuleActionFromId(id: Any, ruleAction: RuleAction<in ComponentSelection?>): SpecRuleAction<in ComponentSelection?> {
        val moduleIdentifier: ModuleIdentifier

        try {
            moduleIdentifier = moduleIdentifierNotationParser.parseNotation(id)
        } catch (e: UnsupportedNotationException) {
            throw InvalidUserCodeException(String.format(INVALID_SPEC_ERROR, if (id == null) "null" else id.toString()), e)
        }

        val spec: Spec<ComponentSelection?> = DefaultComponentSelectionRules.ComponentSelectionMatchingSpec(moduleIdentifier)
        return SpecRuleAction<ComponentSelection?>(ruleAction, spec)
    }

    private fun createAllSpecRulesAction(ruleAction: RuleAction<in ComponentSelection?>): SpecRuleAction<in ComponentSelection?> {
        return SpecRuleAction<ComponentSelection?>(ruleAction, Specs.satisfyAll<ComponentSelection>())
    }

    internal class ComponentSelectionMatchingSpec private constructor(val target: ModuleIdentifier) : Spec<ComponentSelection?> {
        override fun isSatisfiedBy(selection: ComponentSelection): Boolean {
            return selection.getCandidate().getGroup() == target.getGroup() && selection.getCandidate().getModule() == target.getName()
        }
    }

    companion object {
        private const val INVALID_SPEC_ERROR = "Could not add a component selection rule for module '%s'."

        private fun createAdapter(): RuleActionAdapter {
            val ruleActionValidator: RuleActionValidator = DefaultRuleActionValidator()
            return DefaultRuleActionAdapter(ruleActionValidator, "ComponentSelectionRules")
        }
    }
}
