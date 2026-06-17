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

import com.google.common.collect.ImmutableSet
import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactSelectionDetails
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.DependencySubstitution
import org.gradle.api.artifacts.DependencySubstitutions
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.VariantSelectionDetails
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.ComponentSelectorConverter
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableModuleDependencyCapabilitiesHandler
import org.gradle.api.internal.artifacts.dependencies.ModuleDependencyCapabilitiesInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.model.ObjectFactory
import org.gradle.internal.Actions
import org.gradle.internal.Describables
import org.gradle.internal.ImmutableActionSet
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector.Companion.withAttributes
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import org.gradle.internal.exceptions.DiagnosticsVisitor
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationConvertResult
import org.gradle.internal.typeconversion.NotationConverter
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.internal.typeconversion.TypeConversionException
import org.gradle.util.Path
import java.util.function.Supplier
import javax.inject.Inject

open class DefaultDependencySubstitutions private constructor(
    private val reason: ComponentSelectionDescriptor,
    private var substitutionRules: ImmutableActionSet<DependencySubstitution>,
    private val moduleSelectorNotationParser: NotationParser<Any, ComponentSelector>,
    private val projectSelectorNotationParser: NotationParser<Any, ComponentSelector>,
    private val instantiator: Instantiator,
    private val objectFactory: ObjectFactory,
    private val attributesFactory: AttributesFactory,
    private val capabilityNotationParser: NotationParser<Any, Capability>
) : DependencySubstitutionsInternal {
    private var mutationValidator = MutationValidator.IGNORE
    private var rulesMayAddProjectDependency = false

    @Inject
    constructor(
        reason: ComponentSelectionDescriptor,
        projectSelectorNotationParser: NotationParser<Any, ComponentSelector>,
        moduleSelectorNotationParser: NotationParser<Any, ComponentSelector>,
        instantiator: Instantiator,
        objectFactory: ObjectFactory,
        attributesFactory: AttributesFactory,
        capabilityNotationParser: NotationParser<Any, Capability>
    ) : this(
        reason,
        ImmutableActionSet.empty<DependencySubstitution>(),
        moduleSelectorNotationParser,
        projectSelectorNotationParser,
        instantiator,
        objectFactory,
        attributesFactory,
        capabilityNotationParser
    )

    override fun discard() {
        substitutionRules = ImmutableActionSet.empty<DependencySubstitution>()
        rulesMayAddProjectDependency = false
    }

    override fun rulesMayAddProjectDependency(): Boolean {
        return rulesMayAddProjectDependency
    }

    override fun getRuleAction(): Action<DependencySubstitution> {
        return substitutionRules
    }

    protected open fun addSubstitution(rule: Action<in DependencySubstitution>, projectInvolved: Boolean) {
        addRule(rule)
        if (projectInvolved) {
            rulesMayAddProjectDependency = true
        }
    }

    private fun addRule(rule: Action<in DependencySubstitution>) {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)
        substitutionRules = substitutionRules.add(rule)
    }

    override fun all(rule: Action<in DependencySubstitution>): DependencySubstitutions {
        addRule(rule)
        rulesMayAddProjectDependency = true
        return this
    }

    override fun allWithDependencyResolveDetails(rule: Action<in DependencyResolveDetails>, componentSelectorConverter: ComponentSelectorConverter): DependencySubstitutions {
        addRule(DependencyResolveDetailsWrapperAction(rule, componentSelectorConverter, Supplier { Actions.doNothing() }, instantiator))
        return this
    }

    override fun module(notation: String): ComponentSelector {
        return moduleSelectorNotationParser.parseNotation(notation)
    }

    override fun project(path: String): ComponentSelector {
        return projectSelectorNotationParser.parseNotation(path)
    }

    override fun platform(selector: ComponentSelector): ComponentSelector {
        return variant(selector, Action { obj: VariantSelectionDetails -> obj.platform() })
    }

    override fun variant(selector: ComponentSelector, detailsAction: Action<in VariantSelectionDetails>): ComponentSelector {
        val details = instantiator.newInstance<DefaultVariantSelectionDetails>(
            DefaultVariantSelectionDetails::class.java,
            attributesFactory,
            objectFactory,
            capabilityNotationParser,
            selector
        )
        detailsAction.execute(details)
        return details.selector
    }

    override fun substitute(substituted: ComponentSelector): DependencySubstitutions.Substitution {
        return object : DependencySubstitutions.Substitution {
            var artifactAction: Action<in ArtifactSelectionDetails> = Actions.doNothing<ArtifactSelectionDetails>()

            var substitutionReason: ComponentSelectionDescriptorInternal = reason as ComponentSelectionDescriptorInternal

            override fun because(description: String): DependencySubstitutions.Substitution {
                substitutionReason = substitutionReason.withDescription(Describables.of(description))!!
                return this
            }

            override fun withClassifier(classifier: String): DependencySubstitutions.Substitution {
                artifactAction = Actions.composite<ArtifactSelectionDetails>(artifactAction, SetClassifier(classifier))
                return this
            }

            override fun withoutClassifier(): DependencySubstitutions.Substitution {
                artifactAction = Actions.composite<ArtifactSelectionDetails>(artifactAction, NoClassifier.Companion.INSTANCE)
                return this
            }

            override fun withoutArtifactSelectors(): DependencySubstitutions.Substitution {
                artifactAction = Actions.composite<ArtifactSelectionDetails>(artifactAction, NoArtifactSelector.Companion.INSTANCE)
                return this
            }

            override fun using(notation: ComponentSelector): DependencySubstitutions.Substitution {
                DefaultDependencySubstitution.Companion.validateTarget(notation)

                // A project is involved, need to be aware of it
                val projectInvolved = substituted is ProjectComponentSelector || notation is ProjectComponentSelector

                if (substituted is UnversionedModuleComponentSelector) {
                    val moduleId = substituted.getModuleIdentifier()
                    if (notation is ModuleComponentSelector) {
                        if (notation.getModuleIdentifier() == moduleId) {
                            // This substitution is effectively a force
                            substitutionReason = substitutionReason.markAsEquivalentToForce()!!
                        }
                    }
                    addSubstitution(ModuleMatchDependencySubstitutionAction(substitutionReason, moduleId, notation, Supplier { artifactAction }), projectInvolved)
                } else {
                    addSubstitution(ExactMatchDependencySubstitutionAction(substitutionReason, substituted, notation, Supplier { artifactAction }), projectInvolved)
                }
                return this
            }
        }
    }

    override fun setMutationValidator(validator: MutationValidator) {
        mutationValidator = validator
    }

    override fun copy(): DependencySubstitutionsInternal {
        return DefaultDependencySubstitutions(
            reason,
            substitutionRules,
            moduleSelectorNotationParser,
            projectSelectorNotationParser,
            instantiator,
            objectFactory,
            attributesFactory,
            capabilityNotationParser
        )
    }

    private class ProjectPathConverter(private val build: BuildState) : NotationConverter<String, ProjectComponentSelector> {
        override fun describe(visitor: DiagnosticsVisitor) {
            visitor.example("Project paths, e.g. ':api'.")
        }

        @Throws(TypeConversionException::class)
        override fun convert(notation: String, result: NotationConvertResult<in ProjectComponentSelector>) {
            val id = build.getProjects().getProject(Path.path(notation)).getIdentity()
            result.converted(DefaultProjectComponentSelector(id, ImmutableAttributes.EMPTY, ImmutableSet.of<CapabilitySelector>()))
        }
    }

    private class CompositeBuildSubstitutionAction(private val delegate: Action<in DependencySubstitution>) : Action<DependencySubstitution> {
        override fun execute(dependencySubstitution: DependencySubstitution) {
            val ds = dependencySubstitution as DependencySubstitutionInternal
            delegate.execute(object : DependencySubstitutionInternal {
                val configuredTargetSelector: ComponentSelector?
                    get() = ds.configuredTargetSelector

                val ruleDescriptors: ImmutableList<ComponentSelectionDescriptorInternal>?
                    get() = ds.ruleDescriptors

                val configuredArtifactSelectors: ImmutableList<DependencyArtifactSelector>?
                    get() = ds.configuredArtifactSelectors

                override fun getRequested(): ComponentSelector {
                    return ds.getRequested()
                }

                // Implicitly set the substituted dependency attributes as the target dependency attributes
                fun addImplicitRequestAttributesAndCapabilities(notation: Any): Any {
                    if (notation is ProjectComponentSelector) {
                        val projectSelector = notation

                        val requested = getRequested()
                        return DefaultProjectComponentSelector.withAttributesAndCapabilities(
                            projectSelector,
                            (requested.getAttributes() as AttributeContainerInternal).asImmutable(),
                            ImmutableSet.< E > copyOf < E >(requested.getCapabilitySelectors())
                        )
                    }
                    return notation
                }

                override fun useTarget(notation: Any, ruleDescriptor: ComponentSelectionDescriptor) {
                    ds.useTarget(addImplicitRequestAttributesAndCapabilities(notation), ruleDescriptor)
                }

                override fun useTarget(notation: Any) {
                    ds.useTarget(addImplicitRequestAttributesAndCapabilities(notation))
                }

                override fun useTarget(notation: Any, reason: String) {
                    ds.useTarget(addImplicitRequestAttributesAndCapabilities(notation), reason)
                }

                override fun artifactSelection(action: Action<in ArtifactSelectionDetails>) {
                    ds.artifactSelection(action)
                }
            })
        }
    }

    private abstract class AbstractDependencySubstitutionAction protected constructor(private val artifactSelectionAction: Supplier<Action<in ArtifactSelectionDetails>>) :
        Action<DependencySubstitution> {
        override fun execute(dependencySubstitution: DependencySubstitution) {
            dependencySubstitution.artifactSelection(artifactSelectionAction.get())
        }
    }

    private class ExactMatchDependencySubstitutionAction(
        private val selectionReason: ComponentSelectionDescriptorInternal,
        private val substituted: ComponentSelector,
        private val substitute: ComponentSelector,
        artifactSelectionAction: Supplier<Action<in ArtifactSelectionDetails>>
    ) : AbstractDependencySubstitutionAction(artifactSelectionAction) {
        override fun execute(dependencySubstitution: DependencySubstitution) {
            if (substituted == dependencySubstitution.getRequested()) {
                super.execute(dependencySubstitution)
                (dependencySubstitution as DependencySubstitutionInternal).useTarget(substitute, selectionReason)
            }
        }
    }

    private class ModuleMatchDependencySubstitutionAction(
        private val selectionReason: ComponentSelectionDescriptorInternal,
        private val moduleId: ModuleIdentifier,
        private val substitute: ComponentSelector,
        artifactSelectionAction: Supplier<Action<in ArtifactSelectionDetails>>
    ) : AbstractDependencySubstitutionAction(artifactSelectionAction) {
        override fun execute(dependencySubstitution: DependencySubstitution) {
            if (dependencySubstitution.getRequested() is ModuleComponentSelector) {
                val requested = dependencySubstitution.getRequested() as ModuleComponentSelector
                if (moduleId == requested.getModuleIdentifier()) {
                    super.execute(dependencySubstitution)
                    (dependencySubstitution as DependencySubstitutionInternal).useTarget(substitute, selectionReason)
                }
            }
        }
    }

    private class DependencyResolveDetailsWrapperAction(
        private val delegate: Action<in DependencyResolveDetails>,
        private val componentSelectorConverter: ComponentSelectorConverter,
        artifactSelectionAction: Supplier<Action<in ArtifactSelectionDetails>>,
        private val instantiator: Instantiator
    ) : AbstractDependencySubstitutionAction(artifactSelectionAction) {
        override fun execute(substitution: DependencySubstitution) {
            super.execute(substitution)
            val requested = componentSelectorConverter.getModuleVersionId(substitution.getRequested())
            val details = instantiator.newInstance<DefaultDependencyResolveDetails>(DefaultDependencyResolveDetails::class.java, substitution, requested)
            delegate.execute(details)
            details.complete()
        }
    }

    private class SetClassifier(private val classifier: String) : Action<ArtifactSelectionDetails> {
        override fun execute(artifactSelectionDetails: ArtifactSelectionDetails) {
            artifactSelectionDetails.selectArtifact("jar", null, classifier)
        }
    }

    private class NoClassifier : Action<ArtifactSelectionDetails> {
        override fun execute(artifactSelectionDetails: ArtifactSelectionDetails) {
            artifactSelectionDetails.selectArtifact("jar", null, null)
        }

        companion object {
            private val INSTANCE = NoClassifier()
        }
    }

    private class NoArtifactSelector : Action<ArtifactSelectionDetails> {
        override fun execute(artifactSelectionDetails: ArtifactSelectionDetails) {
            artifactSelectionDetails.withoutArtifactSelectors()
        }

        companion object {
            private val INSTANCE = NoArtifactSelector()
        }
    }

    class DefaultVariantSelectionDetails @Inject constructor(
        private val attributesFactory: AttributesFactory,
        private val objectFactory: ObjectFactory,
        private val capabilityNotationParser: NotationParser<Any, Capability>,
        private var selector: ComponentSelector
    ) : VariantSelectionDetails {
        private fun createComponentOfCategory(category: String) {
            if (selector is ProjectComponentSelector) {
                val container = createCategory(category)
                selector = DefaultProjectComponentSelector.withAttributes(selector as ProjectComponentSelector, container.asImmutable())
            } else if (selector is ModuleComponentSelector) {
                val container = createCategory(category)
                selector = withAttributes(selector as ModuleComponentSelector, container.asImmutable())
            }
        }

        private fun createCategory(category: String): AttributeContainerInternal {
            val attributeContainer: AttributeContainer = attributesFactory.mutable()
            return attributeContainer
                .attribute<Category>(Category.CATEGORY_ATTRIBUTE, attributeContainer.named<Category>(Category::class.java, category)) as AttributeContainerInternal
        }

        override fun platform() {
            createComponentOfCategory(Category.REGULAR_PLATFORM)
        }

        override fun enforcedPlatform() {
            createComponentOfCategory(Category.ENFORCED_PLATFORM)
        }

        override fun library() {
            createComponentOfCategory(Category.LIBRARY)
        }

        override fun attributes(configurationAction: Action<in AttributeContainer>) {
            val container = attributesFactory.mutable()
            configurationAction.execute(container)
            if (selector is ProjectComponentSelector) {
                selector = DefaultProjectComponentSelector.withAttributes(selector as ProjectComponentSelector, container.asImmutable())
            } else if (selector is ModuleComponentSelector) {
                selector = withAttributes(selector as ModuleComponentSelector, container.asImmutable())
            }
        }

        override fun capabilities(configurationAction: Action<in ModuleDependencyCapabilitiesHandler>) {
            val handler: ModuleDependencyCapabilitiesInternal = objectFactory.newInstance<DefaultMutableModuleDependencyCapabilitiesHandler>(
                DefaultMutableModuleDependencyCapabilitiesHandler::class.java,
                capabilityNotationParser
            )
            configurationAction.execute(handler)
            if (selector is ProjectComponentSelector) {
                selector = DefaultProjectComponentSelector.withCapabilities(selector as ProjectComponentSelector, ImmutableSet.< E > copyOf < E >(handler.getCapabilitySelectors().get()))
            } else if (selector is ModuleComponentSelector) {
                selector = DefaultModuleComponentSelector.withCapabilities(selector as ModuleComponentSelector, ImmutableSet.< E > copyOf < E >(handler.getCapabilitySelectors().get()))
            }
        }
    }

    class CompositeBuildAwareSubstitutions @Inject constructor(
        projectSelectorNotationParser: NotationParser<Any, ComponentSelector>,
        moduleIdentifierFactory: NotationParser<Any, ComponentSelector>,
        instantiator: Instantiator,
        objectFactory: ObjectFactory,
        attributesFactory: AttributesFactory,
        capabilityNotationParser: NotationParser<Any, Capability>
    ) : DefaultDependencySubstitutions(
        ComponentSelectionReasons.COMPOSITE_BUILD, projectSelectorNotationParser, moduleIdentifierFactory, instantiator, objectFactory, attributesFactory, capabilityNotationParser
    ) {
        override fun addSubstitution(rule: Action<in DependencySubstitution>, projectInvolved: Boolean) {
            val decorated = CompositeBuildSubstitutionAction(rule)
            super.addSubstitution(decorated, projectInvolved)
        }
    }

    companion object {
        fun forResolutionStrategy(
            build: BuildState,
            moduleSelectorNotationParser: NotationParser<Any, ComponentSelector>,
            instantiator: Instantiator,
            objectFactory: ObjectFactory,
            attributesFactory: AttributesFactory,
            capabilityNotationParser: NotationParser<Any, Capability>
        ): DefaultDependencySubstitutions {
            val projectSelectorNotationParser: NotationParser<Any, ComponentSelector> = notationParserFor(build)

            return instantiator.newInstance<DefaultDependencySubstitutions>(
                DefaultDependencySubstitutions::class.java,
                ComponentSelectionReasons.SELECTED_BY_RULE,
                projectSelectorNotationParser,
                moduleSelectorNotationParser,
                instantiator,
                objectFactory,
                attributesFactory,
                capabilityNotationParser
            )
        }

        fun forIncludedBuild(
            build: IncludedBuildState,
            instantiator: Instantiator,
            objectFactory: ObjectFactory,
            attributesFactory: AttributesFactory,
            moduleSelectorNotationParser: NotationParser<Any, ComponentSelector>,
            capabilityNotationParser: NotationParser<Any, Capability>
        ): DefaultDependencySubstitutions {
            val projectSelectorNotationParser: NotationParser<Any, ComponentSelector> = notationParserFor(build)

            return instantiator.newInstance<CompositeBuildAwareSubstitutions>(
                CompositeBuildAwareSubstitutions::class.java,
                projectSelectorNotationParser,
                moduleSelectorNotationParser,
                instantiator,
                objectFactory,
                attributesFactory,
                capabilityNotationParser
            )
        }

        private fun notationParserFor(build: BuildState): NotationParser<Any, ComponentSelector> {
            return NotationParserBuilder
                .toType<ComponentSelector>(ComponentSelector::class.java)
                .fromCharSequence(ProjectPathConverter(build))
                .toComposite()
        }
    }
}
