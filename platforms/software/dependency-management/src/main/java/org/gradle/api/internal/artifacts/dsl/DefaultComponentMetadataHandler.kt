/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl

import com.google.common.collect.Interner
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.ActionConfiguration
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.DependencyConstraintMetadata
import org.gradle.api.artifacts.DirectDependencyMetadata
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor
import org.gradle.api.artifacts.maven.PomModuleDescriptor
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.MetadataResolutionContext
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintMetadataImpl
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependencyMetadataImpl
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.notations.ComponentIdentifierParserFactory
import org.gradle.api.internal.notations.DependencyMetadataNotationParser
import org.gradle.api.internal.notations.ModuleIdentifierNotationConverter
import org.gradle.api.problems.Problems
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.internal.DisplayName
import org.gradle.internal.action.ConfigurableRule
import org.gradle.internal.action.DefaultConfigurableRule
import org.gradle.internal.component.external.model.VariantDerivationStrategy
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.isolation.IsolatableFactory
import org.gradle.internal.management.DependencyResolutionManagementInternal
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor
import org.gradle.internal.rules.DefaultRuleActionAdapter
import org.gradle.internal.rules.DefaultRuleActionValidator
import org.gradle.internal.rules.RuleAction
import org.gradle.internal.rules.RuleActionAdapter
import org.gradle.internal.rules.RuleActionValidator
import org.gradle.internal.rules.SpecRuleAction
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.internal.typeconversion.UnsupportedNotationException
import java.util.function.Consumer
import java.util.function.Supplier

class DefaultComponentMetadataHandler : ComponentMetadataHandler, ComponentMetadataHandlerInternal {
    private val instantiator: Instantiator
    private val metadataRuleContainer: ComponentMetadataRuleContainer
    private val ruleActionAdapter: RuleActionAdapter
    private val moduleIdentifierNotationParser: NotationParser<Any, ModuleIdentifier>
    private val dependencyMetadataNotationParser: NotationParser<Any, DirectDependencyMetadataImpl>
    private val dependencyConstraintMetadataNotationParser: NotationParser<Any, DependencyConstraintMetadataImpl>
    private val componentIdentifierNotationParser: NotationParser<Any, ComponentIdentifier>
    private val attributesFactory: AttributesFactory
    private val isolatableFactory: IsolatableFactory
    private val ruleExecutor: ComponentMetadataRuleExecutor
    private val platformSupport: PlatformSupport

    internal constructor(
        instantiator: Instantiator,
        ruleActionAdapter: RuleActionAdapter,
        moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
        stringInterner: Interner<String>,
        attributesFactory: AttributesFactory,
        isolatableFactory: IsolatableFactory,
        ruleExecutor: ComponentMetadataRuleExecutor,
        platformSupport: PlatformSupport,
        problems: Problems
    ) {
        this.instantiator = instantiator
        this.ruleActionAdapter = ruleActionAdapter
        this.moduleIdentifierNotationParser = NotationParserBuilder
            .toType<ModuleIdentifier>(ModuleIdentifier::class.java)
            .fromCharSequence(ModuleIdentifierNotationConverter(moduleIdentifierFactory))
            .toComposite()
        this.ruleExecutor = ruleExecutor
        this.dependencyMetadataNotationParser =
            DependencyMetadataNotationParser.parser<DirectDependencyMetadata, DirectDependencyMetadataImpl?>(instantiator, DirectDependencyMetadataImpl::class.java, stringInterner, problems)
        this.dependencyConstraintMetadataNotationParser = DependencyMetadataNotationParser.parser<DependencyConstraintMetadata?, DependencyConstraintMetadataImpl?>(
            instantiator,
            DependencyConstraintMetadataImpl::class.java,
            stringInterner,
            problems
        )
        this.componentIdentifierNotationParser = ComponentIdentifierParserFactory().create()
        this.attributesFactory = attributesFactory
        this.isolatableFactory = isolatableFactory
        this.metadataRuleContainer = ComponentMetadataRuleContainer()
        this.platformSupport = platformSupport
    }

    constructor(
        instantiator: Instantiator,
        moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
        stringInterner: Interner<String>,
        attributesFactory: AttributesFactory,
        isolatableFactory: IsolatableFactory,
        ruleExecutor: ComponentMetadataRuleExecutor,
        platformSupport: PlatformSupport,
        problems: Problems
    ) : this(instantiator, createAdapter(), moduleIdentifierFactory, stringInterner, attributesFactory, isolatableFactory, ruleExecutor, platformSupport, problems)

    private constructor(
        instantiator: Instantiator,
        ruleActionAdapter: RuleActionAdapter,
        moduleIdentifierNotationParser: NotationParser<Any, ModuleIdentifier>,
        dependencyMetadataNotationParser: NotationParser<Any, DirectDependencyMetadataImpl>,
        dependencyConstraintMetadataNotationParser: NotationParser<Any, DependencyConstraintMetadataImpl>,
        componentIdentifierNotationParser: NotationParser<Any, ComponentIdentifier>,
        attributesFactory: AttributesFactory,
        isolatableFactory: IsolatableFactory,
        ruleExecutor: ComponentMetadataRuleExecutor,
        platformSupport: PlatformSupport
    ) {
        this.instantiator = instantiator
        this.ruleActionAdapter = ruleActionAdapter
        this.moduleIdentifierNotationParser = moduleIdentifierNotationParser
        this.ruleExecutor = ruleExecutor
        this.dependencyMetadataNotationParser = dependencyMetadataNotationParser
        this.dependencyConstraintMetadataNotationParser = dependencyConstraintMetadataNotationParser
        this.componentIdentifierNotationParser = componentIdentifierNotationParser
        this.attributesFactory = attributesFactory
        this.isolatableFactory = isolatableFactory
        this.metadataRuleContainer = ComponentMetadataRuleContainer()
        this.platformSupport = platformSupport
    }

    private fun addRule(ruleAction: SpecRuleAction<in ComponentMetadataDetails?>): ComponentMetadataHandler {
        metadataRuleContainer.addRule(ruleAction)
        return this
    }

    private fun addClassBasedRule(ruleAction: SpecConfigurableRule): ComponentMetadataHandler {
        metadataRuleContainer.addClassRule(ruleAction)
        return this
    }

    private fun <U> createAllSpecRuleAction(ruleAction: RuleAction<in U?>): SpecRuleAction<in U?> {
        return SpecRuleAction<U?>(ruleAction, Specs.satisfyAll<U?>())
    }

    private fun createSpecRuleActionForModule(id: Any, ruleAction: RuleAction<in ComponentMetadataDetails?>): SpecRuleAction<in ComponentMetadataDetails?> {
        val moduleIdentifier: ModuleIdentifier?

        try {
            moduleIdentifier = moduleIdentifierNotationParser.parseNotation(id)
        } catch (e: UnsupportedNotationException) {
            throw InvalidUserCodeException(String.format(INVALID_SPEC_ERROR, if (id == null) "null" else id.toString()), e)
        }

        val spec: Spec<ComponentMetadataDetails?> = ComponentMetadataDetailsMatchingSpec(moduleIdentifier)
        return SpecRuleAction<ComponentMetadataDetails?>(ruleAction, spec)
    }

    override fun all(rule: Action<in ComponentMetadataDetails>): ComponentMetadataHandler {
        return addRule(createAllSpecRuleAction<ComponentMetadataDetails>(ruleActionAdapter.createFromAction<ComponentMetadataDetails?>(rule)!!))
    }

    override fun all(rule: Closure<*>): ComponentMetadataHandler {
        return addRule(createAllSpecRuleAction<ComponentMetadataDetails>(ruleActionAdapter.createFromClosure<ComponentMetadataDetails?>(ComponentMetadataDetails::class.java, rule)!!))
    }

    @Deprecated("")
    override fun all(ruleSource: Any): ComponentMetadataHandler {
        deprecateMethod(ComponentMetadataHandler::class.java, "all(Object)")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "dependency_management_rules")!!
            .nagUser()
        return addRule(createAllSpecRuleAction<ComponentMetadataDetails>(ruleActionAdapter.createFromRuleSource<ComponentMetadataDetails?>(ComponentMetadataDetails::class.java, ruleSource)!!))
    }

    override fun withModule(id: Any, rule: Action<in ComponentMetadataDetails>): ComponentMetadataHandler {
        return addRule(createSpecRuleActionForModule(id, ruleActionAdapter.createFromAction<ComponentMetadataDetails?>(rule)!!))
    }

    override fun withModule(id: Any, rule: Closure<*>): ComponentMetadataHandler {
        return addRule(createSpecRuleActionForModule(id, ruleActionAdapter.createFromClosure<ComponentMetadataDetails?>(ComponentMetadataDetails::class.java, rule)!!))
    }

    @Deprecated("")
    override fun withModule(id: Any, ruleSource: Any): ComponentMetadataHandler {
        deprecateMethod(ComponentMetadataHandler::class.java, "withModule(Object,Object)")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "dependency_management_rules")!!
            .nagUser()
        return addRule(createSpecRuleActionForModule(id, ruleActionAdapter.createFromRuleSource<ComponentMetadataDetails?>(ComponentMetadataDetails::class.java, ruleSource)!!))
    }

    override fun all(rule: Class<out ComponentMetadataRule>): ComponentMetadataHandler {
        return addClassBasedRule(createAllSpecConfigurableRule(DefaultConfigurableRule.of<ComponentMetadataContext>(rule)))
    }

    override fun all(rule: Class<out ComponentMetadataRule>, configureAction: Action<in ActionConfiguration>): ComponentMetadataHandler {
        return addClassBasedRule(createAllSpecConfigurableRule(DefaultConfigurableRule.of<ComponentMetadataContext>(rule, configureAction, isolatableFactory)))
    }

    override fun withModule(id: Any, rule: Class<out ComponentMetadataRule>): ComponentMetadataHandler {
        return addClassBasedRule(createModuleSpecConfigurableRule(id, DefaultConfigurableRule.of<ComponentMetadataContext>(rule)))
    }

    override fun withModule(id: Any, rule: Class<out ComponentMetadataRule>, configureAction: Action<in ActionConfiguration>): ComponentMetadataHandler {
        return addClassBasedRule(createModuleSpecConfigurableRule(id, DefaultConfigurableRule.of<ComponentMetadataContext>(rule, configureAction, isolatableFactory)))
    }

    private fun createModuleSpecConfigurableRule(id: Any, instantiatingAction: ConfigurableRule<ComponentMetadataContext>): SpecConfigurableRule {
        val moduleIdentifier: ModuleIdentifier?

        try {
            moduleIdentifier = moduleIdentifierNotationParser.parseNotation(id)
        } catch (e: UnsupportedNotationException) {
            throw InvalidUserCodeException(String.format(INVALID_SPEC_ERROR, if (id == null) "null" else id.toString()), e)
        }

        val spec: Spec<ModuleVersionIdentifier?> = ModuleVersionIdentifierSpec(moduleIdentifier)
        return SpecConfigurableRule(instantiatingAction, spec)
    }

    private fun createAllSpecConfigurableRule(instantiatingAction: ConfigurableRule<ComponentMetadataContext>): SpecConfigurableRule {
        return SpecConfigurableRule(instantiatingAction, Specs.satisfyAll<ModuleVersionIdentifier>())
    }

    override fun createComponentMetadataProcessor(resolutionContext: MetadataResolutionContext): ComponentMetadataProcessor {
        return DefaultComponentMetadataProcessor(
            metadataRuleContainer,
            instantiator,
            dependencyMetadataNotationParser,
            dependencyConstraintMetadataNotationParser,
            componentIdentifierNotationParser,
            attributesFactory,
            ruleExecutor,
            platformSupport,
            resolutionContext
        )
    }

    override fun setVariantDerivationStrategy(strategy: VariantDerivationStrategy) {
        metadataRuleContainer.setVariantDerivationStrategy(strategy)
    }

    override fun getVariantDerivationStrategy(): VariantDerivationStrategy {
        return metadataRuleContainer.getVariantDerivationStrategy()
    }

    override fun onAddRule(consumer: Consumer<DisplayName>) {
        metadataRuleContainer.onAddRule(consumer)
    }

    override fun createFactory(dependencyResolutionManagement: DependencyResolutionManagementInternal): ComponentMetadataProcessorFactory {
        // we need to defer the creation of the actual factory until configuration is completed
        // Typically the state of whether to prefer project rules or not is not known when this
        // method is called.
        val actualHandler: Supplier<ComponentMetadataHandlerInternal> = Supplier {
            // determine whether to use the project local handler or the settings handler
            val useRules = dependencyResolutionManagement.getConfiguredRulesMode().useProjectRules()
            if (metadataRuleContainer.isEmpty() || !useRules) {
                // We're creating a component metadata handler which will be applied the settings
                // rules and the current derivation strategy
                val delegate = DefaultComponentMetadataHandler(
                    instantiator,
                    ruleActionAdapter,
                    moduleIdentifierNotationParser,
                    dependencyMetadataNotationParser,
                    dependencyConstraintMetadataNotationParser,
                    componentIdentifierNotationParser,
                    attributesFactory,
                    isolatableFactory,
                    ruleExecutor,
                    platformSupport
                )
                dependencyResolutionManagement.applyRules(delegate)
                delegate.setVariantDerivationStrategy(getVariantDerivationStrategy())
                return@Supplier delegate
            }
            this
        }
        return org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory { resolutionContext: MetadataResolutionContext? ->
            actualHandler.get().createComponentMetadataProcessor(resolutionContext!!)
        }
    }

    internal class ComponentMetadataDetailsMatchingSpec(private val target: ModuleIdentifier) : Spec<ComponentMetadataDetails?> {
        override fun isSatisfiedBy(componentMetadataDetails: ComponentMetadataDetails): Boolean {
            val identifier = componentMetadataDetails.getId()
            return identifier.getGroup() == target.getGroup() && identifier.getName() == target.getName()
        }
    }

    internal class ModuleVersionIdentifierSpec(private val target: ModuleIdentifier) : Spec<ModuleVersionIdentifier?> {
        override fun isSatisfiedBy(identifier: ModuleVersionIdentifier): Boolean {
            return identifier.getGroup() == target.getGroup() && identifier.getName() == target.getName()
        }
    }

    companion object {
        private val ADAPTER_NAME: String = ComponentMetadataHandler::class.java.getSimpleName()
        private const val INVALID_SPEC_ERROR = "Could not add a component metadata rule for module '%s'."

        private fun createAdapter(): RuleActionAdapter {
            val ruleActionValidator: RuleActionValidator = DefaultRuleActionValidator(IvyModuleDescriptor::class.java, PomModuleDescriptor::class.java)
            return DefaultRuleActionAdapter(ruleActionValidator, ADAPTER_NAME)
        }
    }
}
