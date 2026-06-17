/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.configurations

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import groovy.lang.Closure
import org.apache.commons.lang3.text.WordUtils
import org.gradle.api.Action
import org.gradle.api.Describable
import org.gradle.api.DomainObjectCollection
import org.gradle.api.DomainObjectSet
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.ConfigurablePublishArtifact
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationPublications
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.DependencyConstraintSet
import org.gradle.api.artifacts.DependencyResolutionListener
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.PublishArtifactSet
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.api.artifacts.ResolvedConfiguration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.capabilities.Capability
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.CompositeDomainObjectSet
import org.gradle.api.internal.ConfigurationServicesBundle
import org.gradle.api.internal.DefaultDomainObjectSet
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.ConfigurationResolver
import org.gradle.api.internal.artifacts.ConfigurationResolver.Factory.create
import org.gradle.api.internal.artifacts.DefaultDependencyConstraintSet
import org.gradle.api.internal.artifacts.DefaultDependencySet
import org.gradle.api.internal.artifacts.DefaultExcludeRule
import org.gradle.api.internal.artifacts.DefaultPublishArtifactSet
import org.gradle.api.internal.artifacts.ExcludeRuleNotationConverter
import org.gradle.api.internal.artifacts.ResolveExceptionMapper
import org.gradle.api.internal.artifacts.ResolverResults
import org.gradle.api.internal.artifacts.dependencies.DependencyConstraintInternal
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory.create
import org.gradle.api.internal.artifacts.dsl.PublishArtifactNotationParserFactory.create
import org.gradle.api.internal.artifacts.ivyservice.ResolutionParameters
import org.gradle.api.internal.artifacts.ivyservice.TypedResolveException
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure
import org.gradle.api.internal.artifacts.resolver.DefaultResolutionOutputs
import org.gradle.api.internal.artifacts.resolver.ResolutionAccess
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.FreezableAttributeContainer
import org.gradle.api.internal.file.AbstractFileCollection
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.initialization.ResettableConfiguration
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.problems.ProblemId.Companion.create
import org.gradle.api.problems.internal.GradleCoreProblemGroup.configurationUsage
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.api.provider.Provider
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.Cast.uncheckedCast
import org.gradle.internal.Cast.uncheckedNonnullCast
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.Factories
import org.gradle.internal.Factory
import org.gradle.internal.Factory.create
import org.gradle.internal.ImmutableActionSet
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.deprecation.DeprecationLogger.deprecateAction
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.deprecation.Documentation.Companion.userManual
import org.gradle.internal.event.ListenerBroadcast
import org.gradle.internal.exceptions.ResolutionProvider
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.internal.model.CalculatedModelValue
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.CallableBuildOperation
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity
import org.gradle.util.Path
import org.gradle.util.internal.CollectionUtils
import org.gradle.util.internal.ConfigureUtil
import org.gradle.util.internal.WrapUtil
import java.io.File
import java.util.ArrayDeque
import java.util.Arrays
import java.util.Collections
import java.util.Objects
import java.util.Optional
import java.util.Queue
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.StreamSupport
import javax.inject.Inject

/**
 * The default [Configuration] implementation.
 */
abstract class DefaultConfiguration(
    configurationServices: ConfigurationServicesBundle,
    domainObjectContext: DomainObjectContext,
    private val name: String,
    private val isDetached: Boolean,
    private val resolver: ConfigurationResolver,
    dependencyResolutionListeners: ListenerBroadcast<DependencyResolutionListener?>,
    resolutionStrategyFactory: Factory<ResolutionStrategyInternal?>,
    artifactNotationParser: NotationParser<Any, ConfigurablePublishArtifact>,
    capabilityNotationParser: NotationParser<Any, Capability>,
    private val userCodeApplicationContext: UserCodeApplicationContext,
    defaultConfigurationFactory: DefaultConfigurationFactory,
    roleAtCreation: ConfigurationRole,
    lockUsage: Boolean
) : AbstractFileCollection(configurationServices.taskDependencyFactory), ConfigurationInternal, MutationValidator, ResettableConfiguration {
    private val dependencies: DefaultDependencySet
    private val dependencyConstraints: DefaultDependencyConstraintSet
    private val ownDependencies: DefaultDomainObjectSet<Dependency>
    private val ownDependencyConstraints: DefaultDomainObjectSet<DependencyConstraint>
    private var inheritedDependencies: InheritedCollection<Dependency>? = null
    private var inheritedDependencyConstraints: InheritedCollection<DependencyConstraint>? = null
    private var allDependencies: DefaultDependencySet? = null
    private var allDependencyConstraints: DefaultDependencyConstraintSet? = null
    private var defaultDependencyActions = ImmutableActionSet.empty<DependencySet>()
    private var withDependencyActions = ImmutableActionSet.empty<DependencySet>()
    private val artifacts: DefaultPublishArtifactSet
    private val ownArtifacts: DefaultDomainObjectSet<PublishArtifact>
    private var inheritedArtifacts: InheritedCollection<PublishArtifact>? = null
    private var allArtifacts: DefaultPublishArtifactSet? = null
    private val resolvableDependencies: ConfigurationResolvableDependencies

    @get:VisibleForTesting
    var dependencyResolutionListeners: ListenerBroadcast<DependencyResolutionListener?>
        private set

    private val identityPath: Path
    private val projectPath: Path

    private val outgoing: DefaultConfigurationPublications

    private var visible = true
    private var transitive = true
    private var extendsFrom: ExtendedConfigurations
    private val validateExtendedConfiguration: Consumer<Configuration>
    private var description: String? = null
    private val excludeRules: MutableSet<Any> = LinkedHashSet<Any>()
    private var parsedExcludeRules: MutableSet<ExcludeRule>? = null

    private var canBeConsumed: Boolean
    private var canBeResolved: Boolean
    private var canBeDeclaredAgainst: Boolean
    private val consumptionDeprecated: Boolean
    private val resolutionDeprecated: Boolean
    private val declarationDeprecated: Boolean
    private var usageCanBeMutated = true
    private val roleAtCreation: ConfigurationRole

    // This field is reflectively accessed by Nebula:
    // https://github.com/nebula-plugins/gradle-resolution-rules-plugin/blob/db24ee7e0b5c5c6f6327cdfd377e90e505bb1fd2/src/main/kotlin/nebula/plugin/resolutionrules/configurations.kt#L59
    private var observedState = ConfigurationInternal.InternalState.UNRESOLVED
    private var observationReason: Supplier<String>? = null
    var dependenciesObserved: Boolean = false

    private val configurationAttributes: FreezableAttributeContainer
    private val domainObjectContext: DomainObjectContext
    private val resolutionAccess: ResolutionAccess
    private var intrinsicFiles: FileCollectionInternal? = null
        get() {
            if (field == null) {
                assertIsResolvable()
                field = resolutionAccess.publicView!!.getFiles()
            }
            return field
        }

    private val displayName: DisplayName

    private val copyCount = AtomicInteger()

    private var declarationAlternatives: MutableList<String> = ImmutableList.of<String>()
    private var resolutionAlternatives: MutableList<String> = ImmutableList.of<String>()

    private val currentResolveState: CalculatedModelValue<Optional<ResolverResults>>

    private var consistentResolutionSource: ConfigurationInternal? = null
    private var consistentResolutionReason: String? = null

    /** This factory can't be extracted to the services bundle, as it would create a circular dependency between those two types.  */
    private val defaultConfigurationFactory: DefaultConfigurationFactory

    /** This factory has some unique usages during copy, so it can't be extracted to the services bundle.  */
    private var resolutionStrategyFactory: Factory<ResolutionStrategyInternal?>?
    private var resolutionStrategy: ResolutionStrategyInternal? = null

    private val configurationServices: ConfigurationServicesBundle

    /**
     * To create an instance, use [DefaultConfigurationFactory.create].
     */
    init {
        this.identityPath = domainObjectContext.identityPath(name)
        this.projectPath = domainObjectContext.projectPath(name)
        this.resolutionStrategyFactory = resolutionStrategyFactory
        this.dependencyResolutionListeners = dependencyResolutionListeners
        this.domainObjectContext = domainObjectContext

        this.displayName = Describables.memoize(ConfigurationDescription(identityPath))
        this.configurationAttributes = configurationServices.attributesFactory.freezable(configurationServices.attributesFactory.mutable(), this.displayName)

        this.resolutionAccess = DefaultConfiguration.ConfigurationResolutionAccess()
        this.resolvableDependencies = configurationServices.objectFactory.newInstance(ConfigurationResolvableDependencies::class.java, this)

        this.ownDependencies = configurationServices.domainObjectCollectionFactory.newDomainObjectSet(Dependency::class.java) as DefaultDomainObjectSet<Dependency>
        this.ownDependencies.beforeCollectionChanges(validateMutationType(this, MutationValidator.MutationType.DEPENDENCIES))
        this.ownDependencyConstraints = configurationServices.domainObjectCollectionFactory.newDomainObjectSet(DependencyConstraint::class.java) as DefaultDomainObjectSet<DependencyConstraint>
        this.ownDependencyConstraints.beforeCollectionChanges(validateMutationType(this, MutationValidator.MutationType.DEPENDENCIES))

        this.dependencies = DefaultDependencySet(Describables.of(displayName, "dependencies"), this, ownDependencies)
        this.dependencyConstraints = DefaultDependencyConstraintSet(Describables.of(displayName, "dependency constraints"), this, ownDependencyConstraints)

        this.ownArtifacts = configurationServices.domainObjectCollectionFactory.newDomainObjectSet(PublishArtifact::class.java) as DefaultDomainObjectSet<PublishArtifact>
        this.ownArtifacts.beforeCollectionChanges(validateMutationType(this, MutationValidator.MutationType.ARTIFACTS))

        this.artifacts = DefaultPublishArtifactSet(Describables.of(displayName, "artifacts"), ownArtifacts, configurationServices.fileCollectionFactory, taskDependencyFactory)

        this.outgoing = configurationServices.objectFactory.newInstance(
            DefaultConfigurationPublications::class.java,
            displayName,
            artifacts,
            DefaultConfiguration.AllArtifactsProvider(),
            configurationAttributes,
            artifactNotationParser,
            capabilityNotationParser,
            configurationServices.fileCollectionFactory,
            configurationServices.attributesFactory,
            configurationServices.domainObjectCollectionFactory,
            taskDependencyFactory
        )
        this.currentResolveState = domainObjectContext.getModel().newCalculatedValue<Optional<ResolverResults>>(Optional.empty<ResolverResults>())
        this.defaultConfigurationFactory = defaultConfigurationFactory

        this.canBeConsumed = roleAtCreation.isConsumable()
        this.canBeResolved = roleAtCreation.isResolvable()
        this.canBeDeclaredAgainst = roleAtCreation.isDeclarable()
        this.consumptionDeprecated = roleAtCreation.isConsumptionDeprecated()
        this.resolutionDeprecated = roleAtCreation.isResolutionDeprecated()
        this.declarationDeprecated = roleAtCreation.isDeclarationAgainstDeprecated()
        this.usageCanBeMutated = !lockUsage
        this.roleAtCreation = roleAtCreation

        this.configurationServices = configurationServices

        this.validateExtendedConfiguration = Consumer { extended: Configuration? ->
            val other = Objects.requireNonNull<ConfigurationInternal>(uncheckedCast<ConfigurationInternal?>(extended))
            if (domainObjectContext != other.getDomainObjectContext()) {
                throw InvalidUserDataException(
                    String.format(
                        "%s in %s cannot extend %s from %s. Configurations can only extend from configurations in the same context.",
                        displayName.getCapitalizedDisplayName(),
                        this@DefaultConfiguration.domainObjectContext.getDisplayName(),
                        other.getDisplayName(),
                        other.getDomainObjectContext().getDisplayName()
                    )
                )
            }
            if (other.getHierarchy().contains(this@DefaultConfiguration)) {
                throw InvalidUserDataException(
                    String.format(
                        "Cyclic extendsFrom from %s and %s is not allowed. See existing hierarchy: %s", this@DefaultConfiguration,
                        other, other.getHierarchy()
                    )
                )
            }
        }
        this.extendsFrom = ExtendedConfigurations(validateExtendedConfiguration, configurationServices.providerFactory)
    }

    private fun initializeInheritedArtifacts() {
        if (inheritedArtifacts == null) {
            // Use addCollectionProvider to avoid eagerly calling realizePending() on ownArtifacts,
            // which can force realization of lazy artifact providers before variant computation
            // has completed.
            val all = CompositeDomainObjectSet.create<PublishArtifact>(PublishArtifact::class.java, configurationServices.collectionCallbackActionDecorator!!)
            all.addCollectionProvider(configurationServices.providerFactory.provider({ ownArtifacts }))
            inheritedArtifacts = DefaultConfiguration.InheritedCollection<PublishArtifact>(all, extendsFrom, Function { obj: Configuration? -> obj!!.getAllArtifacts() })
        }
    }

    private fun initializeInheritedDependencies() {
        if (inheritedDependencies == null) {
            val all: CompositeDomainObjectSet<Dependency> = configurationServices.domainObjectCollectionFactory.newDomainObjectSet(Dependency::class.java, ownDependencies)
            inheritedDependencies = DefaultConfiguration.InheritedCollection<Dependency>(all, extendsFrom, Function { obj: Configuration? -> obj!!.getAllDependencies() })
        }
    }

    private fun initializeInheritedDependencyConstraints() {
        if (inheritedDependencyConstraints == null) {
            val all: CompositeDomainObjectSet<DependencyConstraint> = configurationServices.domainObjectCollectionFactory.newDomainObjectSet(DependencyConstraint::class.java, ownDependencyConstraints)
            inheritedDependencyConstraints = DefaultConfiguration.InheritedCollection<DependencyConstraint>(all, extendsFrom, Function { obj: Configuration? -> obj!!.getAllDependencyConstraints() })
        }
    }

    override fun getName(): String {
        return name
    }

    override fun getState(): Configuration.State {
        val currentState = currentResolveState.get()
        if (!currentState.isPresent()) {
            return Configuration.State.UNRESOLVED
        }

        val resolvedState = currentState.get()
        if (resolvedState.visitedGraph.hasAnyFailure()) {
            return Configuration.State.RESOLVED_WITH_FAILURES
        } else if (resolvedState.isFullyResolved) {
            return Configuration.State.RESOLVED
        } else {
            return Configuration.State.UNRESOLVED
        }
    }

    @Deprecated("")
    override fun isVisible(): Boolean {
        deprecateMethod(Configuration::class.java, "isVisible")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "deprecate-visible-property")!!
            .nagUser()
        return visible
    }

    @Deprecated("")
    override fun setVisible(visible: Boolean): Configuration {
        validateMutation(MutationValidator.MutationType.BASIC_STATE)
        // TODO: Create a deprecation warning once https://youtrack.jetbrains.com/issue/KT-78754 is resolved
        this.visible = visible
        return this
    }

    override fun getExtendsFrom(): MutableSet<Configuration> {
        val set: MutableSet<Configuration> = LinkedHashSet<Configuration>()
        extendsFrom.visitConfigurations(ExtendedConfiguration.Visitor { configuration: ExtendedConfiguration? -> set.add(configuration!!.get()) })
        return Collections.unmodifiableSet<Configuration>(set)
    }

    private fun updateInheritedCollections() {
        maybeUpdateCollection(inheritedDependencies)
        maybeUpdateCollection(inheritedArtifacts)
        maybeUpdateCollection(inheritedDependencyConstraints)
    }

    private fun maybeUpdateCollection(collection: InheritedCollection<*>?) {
        if (collection != null) {
            collection.updateExtendedConfigurations(this.extendsFrom)
        }
    }

    override fun setExtendsFrom(extendsFrom: Iterable<Configuration>): Configuration {
        validateMutation(MutationValidator.MutationType.HIERARCHY)
        assertNotDetachedExtensionDoingExtending(extendsFrom)
        this.extendsFrom = ExtendedConfigurations(validateExtendedConfiguration, configurationServices.providerFactory)
        for (configuration in extendsFrom) {
            extendsFrom(configuration)
        }
        updateInheritedCollections()
        return this
    }

    override fun extendsFrom(vararg extendsFrom: Configuration): Configuration {
        validateMutation(MutationValidator.MutationType.HIERARCHY)
        assertNotDetachedExtensionDoingExtending(Arrays.asList<Configuration>(*extendsFrom))
        for (extended in extendsFrom) {
            this.extendsFrom.add(extended)
        }
        updateInheritedCollections()
        return this
    }

    @SafeVarargs
    override fun extendsFrom(vararg extendsFrom: Provider<out Configuration>): Configuration {
        validateMutation(MutationValidator.MutationType.HIERARCHY)
        assertNotDetachedExtensionDoingExtendingProviders(Arrays.asList<Provider<out Configuration>>(*extendsFrom))
        for (extended in extendsFrom) {
            this.extendsFrom.add(extended)
        }
        updateInheritedCollections()
        return this
    }

    override fun isTransitive(): Boolean {
        return transitive
    }

    override fun setTransitive(transitive: Boolean): Configuration {
        validateMutation(MutationValidator.MutationType.BASIC_STATE)
        this.transitive = transitive
        return this
    }

    override fun getDescription(): String? {
        return description
    }

    override fun setDescription(description: String?): Configuration {
        this.description = description
        return this
    }

    override fun getHierarchy(): MutableSet<Configuration> {
        if (extendsFrom.isEmpty()) {
            return mutableSetOf<Configuration>(this)
        }
        val result = WrapUtil.toLinkedSet<Configuration>(this)
        collectSuperConfigs(this, result)
        return result
    }

    private fun collectSuperConfigs(configuration: Configuration, result: MutableSet<Configuration>) {
        for (superConfig in configuration.getExtendsFrom()) {
            // The result is an ordered set - so seeing the same value a second time pushes further down
            result.remove(superConfig)
            result.add(superConfig)
            if (superConfig !== this) {  // don't recurse if there's a cycle
                collectSuperConfigs(superConfig, result)
            }
        }
    }

    override fun defaultDependencies(action: Action<in DependencySet>): Configuration {
        warnOrFailOnInvalidUsage("defaultDependencies(Action)", ProperMethodUsage.DECLARABLE_AGAINST)

        // For backwards compatibility, we permit more than just dependencies to be
        // mutated in this callback, which is why we don't use MutationType.DEPENDENCIES here
        validateMutation(MutationValidator.MutationType.BASIC_STATE)

        defaultDependencyActions = defaultDependencyActions.add(configurationServices.collectionCallbackActionDecorator!!.decorate<DependencySet>(Action { dependencies: DependencySet ->
            if (dependencies.isEmpty()) {
                action.execute(dependencies)
            }
        }))
        return this
    }

    override fun withDependencies(action: Action<in DependencySet>): Configuration {
        // For backwards compatibility, we permit more than just dependencies to be
        // mutated in this callback, which is why we don't use MutationType.DEPENDENCIES here
        validateMutation(MutationValidator.MutationType.BASIC_STATE)

        withDependencyActions = withDependencyActions.add(configurationServices.collectionCallbackActionDecorator!!.decorate(action))
        return this
    }

    override fun runDependencyActions() {
        runActionInHierarchy(Action { conf: DefaultConfiguration ->
            conf.defaultDependencyActions.execute(conf.dependencies)
            conf.withDependencyActions.execute(conf.dependencies)

            // Discard actions after execution
            conf.defaultDependencyActions = ImmutableActionSet.empty<DependencySet>()
            conf.withDependencyActions = ImmutableActionSet.empty<DependencySet>()
        })
    }

    override fun resolve(): MutableSet<File> {
        warnOrFailOnInvalidUsage("resolve()", ProperMethodUsage.RESOLVABLE)
        return getFiles()
    }

    override fun iterator(): MutableIterator<File> {
        return this.intrinsicFiles!!.iterator()
    }

    override fun visitContents(visitor: FileCollectionStructureVisitor) {
        this.intrinsicFiles!!.visitStructure(visitor)
    }

    override fun appendContents(formatter: TreeFormatter) {
        formatter.node("configuration: " + identityPath)
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method should only be called on resolvable configurations and throws an exception if
     * called on a configuration that does not permit this usage.
     */
    override fun contains(file: File): Boolean {
        warnOrFailOnInvalidUsage("contains(File)", ProperMethodUsage.RESOLVABLE)
        return this.intrinsicFiles!!.contains(file)
    }

    override fun isEmpty(): Boolean {
        return this.intrinsicFiles!!.isEmpty()
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    override fun getResolvedConfiguration(): ResolvedConfiguration {
        warnOrFailOnInvalidUsage("getResolvedConfiguration()", ProperMethodUsage.RESOLVABLE)
        return resolutionAccess.results.getValue().legacyResults.resolvedConfiguration
    }

    private inner class ConfigurationResolutionAccess : ResolutionAccess {
        val host: ResolutionHost
            get() = DefaultResolutionHost(
                identityPath,
                displayName,
                configurationServices.problems,
                configurationServices.exceptionMapper
            )

        val attributes: ImmutableAttributes
            get() {
                configurationAttributes.freeze()
                return configurationAttributes.asImmutable()
            }

        val defaultSortOrder: ResolutionStrategy.SortOrder
            get() = getResolutionStrategy().sortOrder

        val results: ResolutionResultProvider<ResolverResults>
            get() = DefaultConfiguration.ResolverResultsResolutionResultProvider()

        val publicView: ResolutionOutputsInternal
            get() = DefaultResolutionOutputs(
                this,
                taskDependencyFactory,
                configurationServices.calculatedValueContainerFactory,
                configurationServices.attributesFactory,
                configurationServices.attributeDesugaring,
                configurationServices.objectFactory
            )
    }

    /**
     * A provider that lazily resolves this configuration.
     */
    private inner class ResolverResultsResolutionResultProvider : ResolutionResultProvider<ResolverResults> {
        override fun getTaskDependencyValue(): ResolverResults {
            if (getResolutionStrategy().resolveGraphToDetermineTaskDependencies()) {
                // Force graph resolution as this is required to calculate build dependencies
                return getValue()
            } else {
                return resolveGraphForBuildDependenciesIfRequired()
            }
        }

        override fun getValue(): ResolverResults {
            return resolveGraphIfRequired()
        }
    }

    private fun resolveGraphIfRequired(): ResolverResults {
        assertIsResolvable()
        maybeEmitResolutionDeprecation()

        val currentState = currentResolveState.get()
        if (isFullyResolved(currentState)) {
            return currentState.get()
        }

        val newState: ResolverResults
        if (!domainObjectContext.getModel().hasMutableState()) {
            throw IllegalResolutionException("Resolution of the " + displayName.getDisplayName() + " was attempted without an exclusive lock. This is unsafe and not allowed.")
        } else {
            newState = resolveExclusivelyIfRequired()
        }

        return newState
    }

    private fun resolveExclusivelyIfRequired(): ResolverResults {
        return currentResolveState.update(Function { currentState: Optional<ResolverResults>? ->
            if (Companion.isFullyResolved(currentState!!)) {
                return@update currentState
            }
            Optional.of<ResolverResults>(resolveGraphInBuildOperation())
        }).get()
    }

    /**
     * Must be called from [.resolveExclusivelyIfRequired] only.
     */
    private fun resolveGraphInBuildOperation(): ResolverResults {
        return configurationServices.buildOperationRunner.call(object : CallableBuildOperation<ResolverResults> {
            override fun call(context: BuildOperationContext): ResolverResults {
                runDependencyActions()
                dependencyResolutionListeners.getSource()!!.beforeResolve(getIncoming())

                val results: ResolverResults
                try {
                    results = resolver.resolveGraph(this@DefaultConfiguration)!!
                } catch (e: Exception) {
                    throw configurationServices.exceptionMapper.mapFailure(e, "dependencies", displayName.getDisplayName())
                }

                // Make the new state visible in case a dependency resolution listener queries the result, which requires the new state
                currentResolveState.set(Optional.of<ResolverResults>(results))

                dependencyResolutionListeners.getSource()!!.afterResolve(getIncoming())

                // Discard State
                dependencyResolutionListeners.removeAll()
                if (resolutionStrategy != null) {
                    resolutionStrategy!!.maybeDiscardStateRequiredForGraphResolution()
                }

                captureBuildOperationResult(context, results)
                return results
            }

            fun captureBuildOperationResult(context: BuildOperationContext, results: ResolverResults) {
                // When dependency resolution has failed, we don't want the build operation listeners to fail as well
                // because:
                // 1. the `failed` method will have been called with the user facing error
                // 2. such an error may still lead to a valid dependency graph
                val visitedGraph: VisitedGraphResults = results.visitedGraph
                context.setResult(
                    ResolveConfigurationResolutionBuildOperationResult(
                        visitedGraph.resolvedGraphResultSource,
                        visitedGraph.requestedAttributes,
                        configurationServices.attributesFactory
                    )
                )
            }

            override fun description(): BuildOperationDescriptor.Builder {
                val displayName = "Resolve dependencies of " + identityPath
                val projectId = domainObjectContext.getProjectIdentity()
                var projectPathString: String? = null
                if (!domainObjectContext.isScript()) {
                    if (projectId != null) {
                        projectPathString = projectId.getProjectPath().asString()
                    }
                }
                return BuildOperationDescriptor.displayName(displayName)
                    .progressDisplayName(displayName)
                    .details(
                        ResolveConfigurationResolutionBuildOperationDetails(
                            getName(),
                            domainObjectContext.isScript(),
                            getDescription(),
                            domainObjectContext.getBuildPath().asString(),
                            projectPathString,
                            visible,
                            isTransitive(),
                            resolver.allRepositories
                        )
                    )
            }
        })
    }


    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    override fun getConsistentResolutionSource(): ConfigurationInternal {
        warnOrFailOnInvalidInternalAPIUsage("getConsistentResolutionSource()", ProperMethodUsage.RESOLVABLE)
        return consistentResolutionSource!!
    }

    override fun getConsistentResolutionVersionLocks(): ImmutableList<ResolutionParameters.ModuleVersionLock> {
        if (consistentResolutionSource == null) {
            return ImmutableList.of<ResolutionParameters.ModuleVersionLock>()
        }

        assertThatConsistentResolutionIsPropertyConfigured()
        val consistentResolutionResults: ResolverResults = consistentResolutionSource!!.getResolutionAccess().results.getValue()
        val structure: GraphStructure = consistentResolutionResults.visitedGraph.getGraphStructureSource().get()

        val components = structure.components()
        val numComponents = components!!.count()
        val locks = ImmutableList.builderWithExpectedSize<ResolutionParameters.ModuleVersionLock>(numComponents)
        for (i in 0..<numComponents) {
            if (components.id(i) is ModuleComponentIdentifier) {
                locks.add(
                    ResolutionParameters.ModuleVersionLock(
                        moduleId.getModuleIdentifier(),
                        moduleId.getVersion(),
                        consistentResolutionReason!!,
                        true
                    )
                )
            }
        }
        return locks.build()
    }

    private fun assertThatConsistentResolutionIsPropertyConfigured() {
        if (!consistentResolutionSource!!.isCanBeResolved()) {
            throw InvalidUserCodeException("You can't use " + consistentResolutionSource + " as a consistent resolution source for " + this + " because it isn't a resolvable configuration.")
        }

        // Ensure there are no cycles in the consistent resolution graph.
        val sources: MutableSet<ConfigurationInternal> = LinkedHashSet<ConfigurationInternal>()
        var src: ConfigurationInternal? = this
        while (src != null) {
            if (!sources.add(src)) {
                val cycle = sources.stream().map<String> { obj: ConfigurationInternal? -> obj!!.getName() }.collect(Collectors.joining(" -> ")) + " -> " + getName()
                throw InvalidUserDataException("Cycle detected in consistent resolution sources: " + cycle)
            }
            src = src.getConsistentResolutionSource()
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    override fun <T> callAndResetResolutionState(factory: Factory<T?>): T? {
        warnOrFailOnInvalidInternalAPIUsage("callAndResetResolutionState(Factory)", ProperMethodUsage.RESOLVABLE)
        try {
            // Prevent the state required for resolution from being discarded if anything in the
            // factory resolves this configuration
            getResolutionStrategy().setKeepStateRequiredForGraphResolution(true)

            val value = factory.create()

            // Reset this configuration to an unresolved state
            currentResolveState.set(Optional.empty<ResolverResults>())

            return value
        } finally {
            getResolutionStrategy().setKeepStateRequiredForGraphResolution(false)
        }
    }

    private fun resolveGraphForBuildDependenciesIfRequired(): ResolverResults {
        assertIsResolvable()
        return currentResolveState.update(Function { initial: Optional<ResolverResults>? ->
            if (!initial!!.isPresent()) {
                val futureCompleteResults: CalculatedValue<ResolverResults>? = configurationServices.calculatedValueContainerFactory.create(Describables.of("Full results for", getName()), { context ->
                    val currentState = currentResolveState.get()
                    if (!isFullyResolved(currentState)) {
                        // Do not validate that the current thread holds the project lock.
                        // TODO: Should instead assert that the results are available and fail if not.
                        return@create resolveExclusivelyIfRequired()
                    }
                    currentState.get()
                })

                try {
                    return@update Optional.of<ResolverResults>(resolver.resolveBuildDependencies(this, futureCompleteResults)!!)
                } catch (e: Exception) {
                    throw configurationServices.exceptionMapper.mapFailure(e, "dependencies", displayName.getDisplayName())
                }
            } // Otherwise, already have a result, so reuse it
            initial
        }).get()
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
        context.add(this.intrinsicFiles)
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated("")
    @Suppress("deprecation")
    override fun getTaskDependencyFromProjectDependency(useDependedOn: Boolean, taskName: String): TaskDependency {
        deprecateMethod(Configuration::class.java, "getTaskDependencyFromProjectDependency(boolean, String)")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "deprecate_getTaskDependencyFromProjectDependency")!!
            .nagUser()

        if (useDependedOn) {
            return TasksFromProjectDependencies(
                taskName,
                Supplier { getAllDependencies().withType<ProjectDependency>(ProjectDependency::class.java) },
                taskDependencyFactory,
                configurationServices.projectStateRegistry
            )
        } else {
            return TasksFromDependentProjects(taskName, getName(), taskDependencyFactory)
        }
    }

    override fun getDependencies(): DependencySet {
        return dependencies
    }

    override fun getAllDependencies(): DependencySet {
        if (allDependencies == null) {
            initAllDependencies()
        }
        return allDependencies!!
    }

    @Synchronized
    private fun initAllDependencies() {
        if (allDependencies != null) {
            return
        }

        initializeInheritedDependencies()
        allDependencies = DefaultDependencySet(Describables.of(displayName, "all dependencies"), this, inheritedDependencies!!.allInherited)
    }

    override fun getDependencyConstraints(): DependencyConstraintSet {
        return dependencyConstraints
    }

    override fun getAllDependencyConstraints(): DependencyConstraintSet {
        if (allDependencyConstraints == null) {
            initAllDependencyConstraints()
        }
        return allDependencyConstraints!!
    }

    @Synchronized
    private fun initAllDependencyConstraints() {
        if (allDependencyConstraints != null) {
            return
        }

        initializeInheritedDependencyConstraints()
        allDependencyConstraints = DefaultDependencyConstraintSet(
            Describables.of(displayName, "all dependency constraints"), this,
            inheritedDependencyConstraints!!.allInherited
        )
    }

    override fun getArtifacts(): PublishArtifactSet {
        return artifacts
    }

    override fun getAllArtifacts(): PublishArtifactSet {
        initAllArtifacts()
        return allArtifacts!!
    }

    @Synchronized
    private fun initAllArtifacts() {
        if (allArtifacts != null) {
            return
        }
        val displayName = Describables.of(this.displayName, "all artifacts")

        if (this.isObserved && extendsFrom.isEmpty()) {
            // No further mutation is allowed and there's no parent: the artifact set corresponds to this configuration own artifacts
            this.allArtifacts = DefaultPublishArtifactSet(displayName, ownArtifacts, configurationServices.fileCollectionFactory, taskDependencyFactory)
        } else {
            // Otherwise, the configuration can still be mutated, so we need to create a composite in case extendsFrom are added
            initializeInheritedArtifacts()
            this.allArtifacts = DefaultPublishArtifactSet(
                displayName,
                inheritedArtifacts!!.allInherited, configurationServices.fileCollectionFactory, taskDependencyFactory
            )
        }
    }

    override fun getExcludeRules(): MutableSet<ExcludeRule> {
        initExcludeRules()
        return Collections.unmodifiableSet<ExcludeRule>(parsedExcludeRules)
    }

    override fun getAllExcludeRules(): MutableSet<ExcludeRule> {
        val result: MutableSet<ExcludeRule> = LinkedHashSet<ExcludeRule>(getExcludeRules())
        extendsFrom.visitConfigurations(ExtendedConfiguration.Visitor { configuration: ExtendedConfiguration? -> result.addAll((configuration!!.get() as ConfigurationInternal).getAllExcludeRules()) })
        return result
    }

    /**
     * Synchronize read access to excludes. Mutation does not need to be thread-safe.
     */
    @Synchronized
    private fun initExcludeRules() {
        if (parsedExcludeRules == null) {
            val parser = ExcludeRuleNotationConverter.parser()
            parsedExcludeRules = LinkedHashSet<ExcludeRule>()
            for (excludeRule in excludeRules) {
                parsedExcludeRules!!.add(parser.parseNotation(excludeRule))
            }
        }
    }

    override fun exclude(excludeRuleArgs: MutableMap<String, String>): DefaultConfiguration {
        validateMutation(MutationValidator.MutationType.DEPENDENCIES)
        parsedExcludeRules = null
        excludeRules.add(excludeRuleArgs)
        return this
    }

    override fun getDisplayName(): String {
        return displayName.getDisplayName()
    }

    override fun asDescribable(): DisplayName {
        return displayName
    }

    override fun getIncoming(): ResolvableDependencies {
        return resolvableDependencies
    }

    override fun getOutgoing(): ConfigurationPublications {
        return outgoing
    }

    override fun collectVariants(visitor: ConfigurationInternal.VariantVisitor) {
        outgoing.collectVariants(visitor)
    }

    override fun isCanBeMutated(): Boolean {
        val immutable = this.isObserved || currentResolveState.get().isPresent()
        return !immutable
    }

    override fun markAsObserved(reason: String) {
        if (this.isObserved) {
            return
        }

        runActionInHierarchy(Action { conf: DefaultConfiguration ->
            if (!conf.isObserved) {
                conf.observationReason = Supplier {
                    val target = if (conf === this) "the configuration" else "the configuration's child " + this.getDisplayName()
                    target + " was " + reason
                }

                // This field is only set for compatibility with Nebula
                conf.observedState = ConfigurationInternal.InternalState.OBSERVED

                conf.configurationAttributes.freeze()
                conf.outgoing.preventFromFurtherMutation(conf.observationReason!!)
                conf.preventUsageMutation()
            }
        })
    }

    override fun markDependenciesObserved() {
        check(this.isObserved) { "Cannot observe dependencies before markAsObserved(String) has been called." }

        this.dependenciesObserved = true
    }

    private val isObserved: Boolean
        get() = observationReason != null

    /**
     * Runs the provided action for this configuration and all configurations that it extends from.
     *
     *
     * Specifically handles the case where [Configuration.extendsFrom] is called during the
     * action execution.
     */
    private fun runActionInHierarchy(action: Action<DefaultConfiguration>) {
        val seen: MutableSet<Configuration> = HashSet<Configuration>()
        val remaining: Queue<Configuration> = ArrayDeque<Configuration>()
        remaining.add(this)

        while (!remaining.isEmpty()) {
            val current = remaining.remove()
            action.execute(current as DefaultConfiguration)

            for (parent in current.getExtendsFrom()) {
                if (seen.add(parent)) {
                    remaining.add(parent)
                }
            }
        }
    }

    override fun outgoing(action: Action<in ConfigurationPublications>) {
        action.execute(outgoing)
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    override fun copy(): ConfigurationInternal {
        warnOrFailOnInvalidUsage("copy()", ProperMethodUsage.RESOLVABLE)
        return createCopy(getDependencies(), getDependencyConstraints())
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    override fun copyRecursive(): Configuration {
        warnOrFailOnInvalidUsage("copyRecursive()", ProperMethodUsage.RESOLVABLE)
        return createCopy(getAllDependencies(), getAllDependencyConstraints())
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    override fun copy(dependencySpec: Spec<in Dependency?>): Configuration {
        warnOrFailOnInvalidUsage("copy(Spec)", ProperMethodUsage.RESOLVABLE)
        return createCopy(CollectionUtils.filter<Dependency?>(getDependencies(), dependencySpec), getDependencyConstraints())
    }

    override fun copyRecursive(dependencySpec: Spec<in Dependency?>): Configuration {
        warnOrFailOnInvalidUsage("copyRecursive(Spec)", ProperMethodUsage.RESOLVABLE)
        return createCopy(CollectionUtils.filter<Dependency?>(getAllDependencies(), dependencySpec), getAllDependencyConstraints())
    }

    /**
     * Instead of copying a configuration's roles outright, we allow copied configurations
     * to assume any role. However, any roles which were previously disabled will become
     * deprecated in the copied configuration.
     *
     * This means the copy created is **NOT** a strictly identical copy of the original, as the role
     * will be not only a different instance, but also may return different deprecation values.
     */
    private fun createCopy(dependencies: MutableSet<Dependency>, dependencyConstraints: MutableSet<DependencyConstraint>): DefaultConfiguration {
        val copiedConfiguration = copyAsDetached()

        copiedConfiguration.visible = visible
        copiedConfiguration.transitive = transitive
        copiedConfiguration.description = description

        copiedConfiguration.defaultDependencyActions = defaultDependencyActions
        copiedConfiguration.withDependencyActions = withDependencyActions
        copiedConfiguration.dependencyResolutionListeners = dependencyResolutionListeners.copy()

        copiedConfiguration.declarationAlternatives = declarationAlternatives
        copiedConfiguration.resolutionAlternatives = resolutionAlternatives

        copiedConfiguration.getArtifacts().addAll(getAllArtifacts())

        if (!configurationAttributes.isEmpty()) {
            for (attribute in configurationAttributes.keySet()) {
                val value: Any? = configurationAttributes.getAttribute(attribute)
                copiedConfiguration.getAttributes().attribute<Any>(uncheckedNonnullCast<Attribute<Any>?>(attribute)!!, value!!)
            }
        }

        // todo An ExcludeRule is a value object but we don't enforce immutability for DefaultExcludeRule as strong as we
        // should (we expose the Map). We should provide a better API for ExcludeRule (I don't want to use unmodifiable Map).
        // As soon as DefaultExcludeRule is truly immutable, we don't need to create a new instance of DefaultExcludeRule.
        for (excludeRule in getAllExcludeRules()) {
            copiedConfiguration.excludeRules.add(DefaultExcludeRule(excludeRule.getGroup(), excludeRule.getModule()))
        }

        val copiedDependencies: DomainObjectSet<Dependency> = copiedConfiguration.getDependencies()
        for (dependency in dependencies) {
            copiedDependencies.add(dependency.copy())
        }
        val copiedDependencyConstraints: DomainObjectSet<DependencyConstraint> = copiedConfiguration.getDependencyConstraints()
        for (dependencyConstraint in dependencyConstraints) {
            copiedDependencyConstraints.add((dependencyConstraint as DependencyConstraintInternal).copy())
        }
        return copiedConfiguration
    }

    private fun copyAsDetached(): DefaultConfiguration {
        val newName = this.nameWithCopySuffix
        val childResolutionStrategy: Factory<ResolutionStrategyInternal?> =
            if (resolutionStrategy != null) Factories.constant<ResolutionStrategyInternal>(resolutionStrategy!!.copy()) else resolutionStrategyFactory

        @Suppress("deprecation") val role = ConfigurationRoles.RESOLVABLE_DEPENDENCY_SCOPE
        return defaultConfigurationFactory.create(
            newName,
            true,
            resolver,
            childResolutionStrategy,
            role
        )
    }

    private val nameWithCopySuffix: String
        get() {
            val count = copyCount.incrementAndGet()
            val copyName = name + "Copy"
            return if (count == 1)
                copyName
            else
                copyName + count
        }

    override fun copy(dependencySpec: Closure<*>): Configuration {
        return copy(Specs.convertClosureToSpec<Dependency>(dependencySpec))
    }

    override fun copyRecursive(dependencySpec: Closure<*>): Configuration {
        return copyRecursive(Specs.convertClosureToSpec<Dependency>(dependencySpec))
    }

    override fun getResolutionStrategy(): ResolutionStrategyInternal {
        if (resolutionStrategy == null) {
            resolutionStrategy = resolutionStrategyFactory!!.create()
            resolutionStrategy!!.setMutationValidator(this)
            resolutionStrategyFactory = null
        }
        return resolutionStrategy!!
    }

    override fun getDomainObjectContext(): DomainObjectContext {
        return domainObjectContext
    }

    override fun resolutionStrategy(closure: Closure<*>): Configuration {
        ConfigureUtil.configure<ResolutionStrategyInternal>(closure, getResolutionStrategy())
        return this
    }

    override fun resolutionStrategy(action: Action<in ResolutionStrategy>): Configuration {
        action.execute(getResolutionStrategy())
        return this
    }

    override fun validateMutation(type: MutationValidator.MutationType) {
        if (isMutationForbidden(type)) {
            throw InvalidUserCodeException(
                String.format("Cannot mutate the %s of %s after %s. ", type, this.getDisplayName(), observationReason!!.get()) +
                        "After a configuration has been observed, it should not be modified."
            )
        }

        if (type == MutationValidator.MutationType.USAGE) {
            assertUsageIsMutable()
        }
    }

    /**
     * Given the type of mutation, determine based on the observation state of this
     * configuration whether the mutation is forbidden or if it may proceed.
     */
    private fun isMutationForbidden(type: MutationValidator.MutationType): Boolean {
        if (observationReason == null) {
            // This configuration has not been observed, and so is still mutable.
            // No reason to throw an exception.
            return false
        }

        if (type == MutationValidator.MutationType.STRATEGY && !isFullyResolved(currentResolveState.get())) {
            // TODO: Eventually this should become an error, but plugins (Android?) are mutating the
            // resolution strategy in beforeResolve in order to save memory.
            return false
        }

        if (type == MutationValidator.MutationType.DEPENDENCIES || type == MutationValidator.MutationType.DEPENDENCY_ATTRIBUTES || type == MutationValidator.MutationType.DEPENDENCY_CONSTRAINT_ATTRIBUTES
        ) {
            // When building variant metadata, dependencies are observed lazily after attributes, capabilities, etc.
            // We allow these to be marked as observed separately from the remainder of its state.
            return dependenciesObserved
        }

        // Otherwise, non-dependency state has been observed and is therefore non-mutable.
        return true
    }

    override fun getConfigurationIdentity(): ConfigurationIdentity {
        val name = getName()
        val projectId = domainObjectContext.getProjectIdentity()
        val projectPath = if (projectId == null) null else projectId.getProjectPath().asString()
        val buildPath = domainObjectContext.getBuildPath().toString()
        return DefaultConfigurationIdentity(buildPath, projectPath, name)
    }

    private fun isProperUsage(vararg properUsages: ProperMethodUsage): Boolean {
        val conf: ConfigurationInternal = this
        return Arrays.stream<ProperMethodUsage>(properUsages).anyMatch { pu: ProperMethodUsage? -> pu!!.isAllowed(conf) }
    }

    /**
     * Checks if the only usages that allow this method are also deprecated.
     *
     * @param properUsages the usages to check against
     * @return `true` if so; `false` otherwise
     */
    private fun isExclusivelyDeprecatedUsage(vararg properUsages: ProperMethodUsage): Boolean {
        val conf: ConfigurationInternal = this
        return Arrays.stream<ProperMethodUsage>(properUsages)
            .filter { pu: ProperMethodUsage? -> pu!!.isAllowed(conf) }
            .allMatch { pu: ProperMethodUsage? -> pu!!.isDeprecated(conf) }
    }

    // TODO: This causes redundant deprecation logs when we call internal methods to support
    //       features on deprecated configurations. We already emit deprecation warnings
    //       when using public deprecated methods, we should not emit them again for internal API usage.
    private fun warnOrFailOnInvalidInternalAPIUsage(methodName: String, vararg properUsages: ProperMethodUsage) {
        warnOrFailOnInvalidUsage(methodName, true, *properUsages)
    }

    private fun warnOrFailOnInvalidUsage(methodName: String, vararg properUsages: ProperMethodUsage) {
        warnOrFailOnInvalidUsage(methodName, false, *properUsages)
    }

    private fun warnOrFailOnInvalidUsage(methodName: String, allowDeprecated: Boolean, vararg properUsages: ProperMethodUsage) {
        if (!isProperUsage(*properUsages)) {
            val currentUsageDesc = UsageDescriber.describeCurrentUsage(this)
            val properUsageDesc: String = ProperMethodUsage.Companion.summarizeProperUsage(*properUsages)
            val prefixTemplate = "Calling configuration method '%s' is not allowed for configuration '%s'"
            val suffixTemplate = "This method is only meant to be called on configurations which allow the %susage(s): '%s'."
            val ex = GradleException(
                String.format(
                    prefixTemplate + ", which has permitted usage(s):\n%s\n" + suffixTemplate,
                    methodName,
                    getName(),
                    currentUsageDesc,
                    if (allowDeprecated) "" else "(non-deprecated) ",
                    properUsageDesc
                )
            )

            val id = create("method-not-allowed", "Method call not allowed", configurationUsage())
            throw configurationServices.problems.internalReporter.throwing(ex, id, { spec ->
                spec.contextualLabel(
                    String.format(
                        prefixTemplate,
                        methodName,
                        getName()
                    )
                )
                spec.details(
                    String.format(
                        "'%s' has the following permitted usage(s):\n%s\n" + suffixTemplate,
                        getName(),
                        currentUsageDesc,
                        if (allowDeprecated) "" else "(non-deprecated) ",
                        properUsageDesc
                    )
                )
            })
        } else if (isExclusivelyDeprecatedUsage(*properUsages)) {
            deprecateAction(String.format("Calling %s on %s", methodName, this))
                .withContext("This configuration does not allow this method to be called.")!!
                .willBecomeAnErrorInGradle10()
                .withUpgradeGuideSection(8, "configurations_allowed_usage")!!
                .nagUser()
        }
    }

    private class ConfigurationDescription(private val identityPath: Path) : Describable {
        override fun getDisplayName(): String {
            return "configuration '" + identityPath + "'"
        }
    }

    private class DefaultConfigurationIdentity(val buildPath: String, val projectPath: String?, val name: String) : ConfigurationIdentity {
        override fun toString(): String {
            var path = Path.path(buildPath)
            if (projectPath != null) {
                path = path.append(Path.path(projectPath))
            }
            path = path.child(name)
            return "Configuration '" + path.toString() + "'"
        }
    }

    private fun assertIsResolvable() {
        check(canBeResolved) { "Resolving dependency configuration '" + name + "' is not allowed as it is defined as 'canBeResolved=false'.\nInstead, a resolvable ('canBeResolved=true') dependency configuration that extends '" + name + "' should be resolved." }
    }

    override fun assertCanCarryBuildDependencies() {
        assertIsResolvable()
    }

    override fun getAttributes(): AttributeContainerInternal {
        return configurationAttributes
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on consumable or resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    override fun attributes(action: Action<in AttributeContainer>): Configuration {
        warnOrFailOnInvalidUsage("attributes(Action)", ProperMethodUsage.CONSUMABLE, ProperMethodUsage.RESOLVABLE)
        action.execute(configurationAttributes)
        return this
    }

    override fun preventUsageMutation() {
        usageCanBeMutated = false
    }

    @Suppress("deprecation")
    private fun assertUsageIsMutable() {
        if (!usageCanBeMutated) {
            // Don't print role message for configurations with all usages - users might not have actively chosen this role
            if (roleAtCreation !== ConfigurationRoles.ALL) {
                throw GradleException(
                    String.format(
                        "Cannot change the allowed usage of %s, as it was locked upon creation to the role: '%s'.\n" +
                                "This role permits the following usage:\n" +
                                "%s\n" +
                                "Ideally, each configuration should be used for a single purpose.",
                        getDisplayName(), roleAtCreation.getName(), roleAtCreation.describeUsage()
                    )
                )
            } else {
                throw GradleException(String.format("Cannot change the allowed usage of %s, as it has been locked.", getDisplayName()))
            }
        }
    }

    /**
     * If this configuration has a role set upon creation, conditionally fail upon usage mutation.
     *
     *
     * Configurations with roles set upon creation should not have their usage changed.
     *
     *
     * For **redundant**, where a method is called but no change in the usage occurs, this method does not fail. This is
     * to allow plugins utilizing this behavior to continue to function, as popular third-party plugins continue to
     * violate these conditions.  However, it may emit a warning on redundant changes if a special flag is set.
     *
     *
     * The eventual goal is that all configuration usage be specified upon creation and immutable
     * thereafter.
     */
    private fun checkChangingUsage(methodName: String, current: Boolean, newValue: Boolean) {
        if (hasAllUsages()) {
            // We currently allow configurations with all usages -- those that are created with
            // `create` and `register` -- to have mutable roles. This is likely to change in the future
            // when we deprecate any configuration with mutable roles.
            return
        }

        val redundantChange = current == newValue

        // Error will be thrown later. Don't emit a duplicate warning.
        if (!usageCanBeMutated && !redundantChange) {
            return
        }

        // KGP continues to set the already-set value for a given usage even though it is already set
        // This property exists to allow KGP to test whether they have properly stopped making unnecessary redundant
        // changes to detachedConfigurations.
        // This property WILL be removed without warning and should be removed in Gradle 9.x.
        val extraWarningsEnabled = java.lang.Boolean.getBoolean("org.gradle.internal.deprecation.preliminary.Configuration.redundantUsageChangeWarning.enabled")

        if (redundantChange) {
            // Remove this condition in Gradle 9.x and warn on every redundant change, in Gradle 10 this should fail.
            if (extraWarningsEnabled) {
                warnAboutChangingUsage(methodName, newValue)
            }
        } else {
            if (isDetachedConfiguration() && !newValue) {
                // This is an actual change, and permitting it is not desired behavior, but we haven't deprecated
                // changing detached confs usages to false as of 9.0, so we have to permit even these non-redundant changes,
                // but we can at least warn if the flag is set.
                // Remove this check and warn on every actual change to a detached conf in Gradle 9.x, in Gradle 10 this should fail.
                if (extraWarningsEnabled) {
                    warnAboutChangingUsage(methodName, newValue)
                }
            } else {
                failDueToChangingUsage(methodName, newValue)
            }
        }
    }

    private fun warnAboutChangingUsage(methodName: String, newValue: Boolean) {
        deprecateAction(String.format("Calling %s(%b) on %s", methodName, newValue, this))
            .withContext("This configuration's role was set upon creation and its usage should not be changed.")!!
            .willBecomeAnErrorInGradle10()
            .withUpgradeGuideSection(8, "configurations_allowed_usage")!!
            .nagUser()
    }

    private fun failDueToChangingUsage(methodName: String, newValue: Boolean) {
        val ex =
            GradleException(String.format("Calling %s(%b) on %s is not allowed.  This configuration's role was set upon creation and its usage should not be changed.", methodName, newValue, this))
        val id = create("method-not-allowed", "Method call not allowed", configurationUsage())
        throw configurationServices.problems.internalReporter.throwing(ex, id, { spec ->
            spec.contextualLabel(ex.message)
        })
    }

    override fun isDetachedConfiguration(): Boolean {
        return isDetached
    }

    @Suppress("deprecation")
    private fun hasAllUsages(): Boolean {
        return roleAtCreation === ConfigurationRoles.ALL
    }

    override fun isDeprecatedForConsumption(): Boolean {
        return consumptionDeprecated
    }

    override fun isDeprecatedForResolution(): Boolean {
        return resolutionDeprecated
    }

    override fun isDeprecatedForDeclarationAgainst(): Boolean {
        return declarationDeprecated
    }

    override fun isCanBeConsumed(): Boolean {
        return canBeConsumed
    }

    override fun setCanBeConsumed(allowed: Boolean) {
        checkChangingUsage("setCanBeConsumed", canBeConsumed, allowed)
        if (canBeConsumed != allowed) {
            validateMutation(MutationValidator.MutationType.USAGE)
            canBeConsumed = allowed
        }
    }

    override fun isCanBeResolved(): Boolean {
        return canBeResolved
    }

    override fun setCanBeResolved(allowed: Boolean) {
        checkChangingUsage("setCanBeResolved", canBeResolved, allowed)
        if (canBeResolved != allowed) {
            validateMutation(MutationValidator.MutationType.USAGE)
            canBeResolved = allowed
        }
    }

    override fun isCanBeDeclared(): Boolean {
        return canBeDeclaredAgainst
    }

    override fun setCanBeDeclared(allowed: Boolean) {
        checkChangingUsage("setCanBeDeclared", canBeDeclaredAgainst, allowed)
        if (canBeDeclaredAgainst != allowed) {
            validateMutation(MutationValidator.MutationType.USAGE)
            canBeDeclaredAgainst = allowed
        }
    }

    override fun getDeclarationAlternatives(): MutableList<String> {
        return declarationAlternatives
    }

    override fun getResolutionAlternatives(): MutableList<String> {
        return resolutionAlternatives
    }

    override fun addDeclarationAlternatives(vararg alternativesForDeclaring: String) {
        this.declarationAlternatives = ImmutableList.builder<String>()
            .addAll(declarationAlternatives)
            .addAll(Arrays.asList<String>(*alternativesForDeclaring))
            .build()
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    override fun addResolutionAlternatives(vararg alternativesForResolving: String) {
        this.resolutionAlternatives = ImmutableList.builder<String>()
            .addAll(resolutionAlternatives)
            .addAll(Arrays.asList<String>(*alternativesForResolving))
            .build()
    }

    override fun shouldResolveConsistentlyWith(versionsSource: Configuration): Configuration {
        warnOrFailOnInvalidUsage("shouldResolveConsistentlyWith(Configuration)", ProperMethodUsage.RESOLVABLE)
        this.consistentResolutionSource = versionsSource as ConfigurationInternal
        this.consistentResolutionReason = "version resolved in " + versionsSource + " by consistent resolution"
        return this
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Usage: This method can only be called on resolvable configurations and will throw an exception if
     * called on a configuration that does not permit this usage.
     */
    override fun disableConsistentResolution(): Configuration {
        warnOrFailOnInvalidUsage("disableConsistentResolution()", ProperMethodUsage.RESOLVABLE)
        this.consistentResolutionSource = null
        this.consistentResolutionReason = null
        return this
    }

    override fun getRoleAtCreation(): ConfigurationRole {
        return roleAtCreation
    }

    val problems: ProblemsInternal
        get() = configurationServices.problems

    private fun assertNotDetachedExtensionDoingExtending(extendsFrom: Iterable<Configuration>) {
        if (isDetachedConfiguration()) {
            throwDetachedConfigurationWithExtendsFromError(extendsFrom)
        }
    }

    private fun assertNotDetachedExtensionDoingExtendingProviders(extendsFrom: MutableList<Provider<out Configuration>>) {
        if (isDetachedConfiguration()) {
            throwDetachedConfigurationWithExtendsFromError(extendsFrom.stream().map { obj: Provider<*>? -> obj!!.get() }.collect(Collectors.toList()))
        }
    }

    private fun throwDetachedConfigurationWithExtendsFromError(extendsFrom: Iterable<Configuration>) {
        val summarizedExtensionTargets = StreamSupport.stream<Configuration>(extendsFrom.spliterator(), false)
            .map<ConfigurationInternal> { obj: Configuration? -> ConfigurationInternal::class.java.cast(obj) }
            .map<String> { obj: ConfigurationInternal? -> obj!!.getDisplayName() }
            .collect(Collectors.joining(", "))
        val ex = GradleException(getDisplayName() + " cannot extend " + summarizedExtensionTargets)
        val id = create("extend-detached-not-allowed", "Extending a detachedConfiguration is not allowed", configurationUsage())
        throw configurationServices.problems.internalReporter.throwing(ex, id, { spec ->
            spec.contextualLabel(ex.message)
        })
    }

    class ConfigurationResolvableDependencies @Inject constructor(private val configuration: DefaultConfiguration) : ResolvableDependencies {
        override fun getName(): String {
            return configuration.name
        }

        override fun getPath(): String {
            return configuration.projectPath.asString()
        }

        override fun toString(): String {
            return "dependencies '" + configuration.identityPath + "'"
        }

        override fun getFiles(): FileCollection {
            return configuration.intrinsicFiles!!
        }

        override fun getDependencies(): DependencySet {
            configuration.runDependencyActions()
            return configuration.getAllDependencies()
        }

        override fun getDependencyConstraints(): DependencyConstraintSet {
            configuration.runDependencyActions()
            return configuration.getAllDependencyConstraints()
        }

        override fun beforeResolve(action: Action<in ResolvableDependencies>) {
            configuration.dependencyResolutionListeners.add("beforeResolve", configuration.userCodeApplicationContext.reapplyCurrentLater(action))
        }

        override fun beforeResolve(action: Closure<*>) {
            beforeResolve(ConfigureUtil.configureUsing<ResolvableDependencies>(action))
        }

        override fun afterResolve(action: Action<in ResolvableDependencies>) {
            configuration.dependencyResolutionListeners.add("afterResolve", configuration.userCodeApplicationContext.reapplyCurrentLater(action))
        }

        override fun afterResolve(action: Closure<*>) {
            afterResolve(ConfigureUtil.configureUsing<ResolvableDependencies>(action))
        }

        override fun getResolutionResult(): ResolutionResult {
            configuration.assertIsResolvable()
            return configuration.resolutionAccess.publicView!!.resolutionResult
        }

        override fun getArtifacts(): ArtifactCollection {
            return configuration.resolutionAccess.publicView!!.getArtifacts()!!
        }

        override fun artifactView(configAction: Action<in ArtifactView.ViewConfiguration>): ArtifactView {
            return configuration.resolutionAccess.publicView!!.artifactView(configAction)!!
        }

        override fun getAttributes(): AttributeContainer {
            return configuration.configurationAttributes
        }
    }

    private inner class AllArtifactsProvider : PublishArtifactSetProvider {
        override fun getPublishArtifactSet(): PublishArtifactSet {
            return getAllArtifacts()
        }
    }

    override fun getResolutionHost(): ResolutionHost {
        return resolutionAccess.host
    }

    override fun getResolutionAccess(): ResolutionAccess {
        return resolutionAccess
    }

    private class DefaultResolutionHost(
        private val buildTreePath: Path,
        private val displayName: DisplayName,
        private val problems: ProblemsInternal,
        private val exceptionMapper: ResolveExceptionMapper
    ) : ResolutionHost {
        override fun getProblems(): ProblemsInternal {
            return problems
        }

        override fun displayName(): DisplayName {
            return displayName
        }

        override fun consolidateFailures(resolutionType: String, failures: MutableCollection<Throwable>): Optional<TypedResolveException> {
            return Optional.ofNullable<TypedResolveException>(exceptionMapper.mapFailures(failures, resolutionType, displayName))
        }

        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as DefaultResolutionHost
            return buildTreePath == that.buildTreePath
        }

        override fun hashCode(): Int {
            return buildTreePath.hashCode()
        }
    }

    private enum class ProperMethodUsage {
        CONSUMABLE {
            override fun isAllowed(configuration: ConfigurationInternal): Boolean {
                return configuration.isCanBeConsumed()
            }

            override fun isDeprecated(configuration: ConfigurationInternal): Boolean {
                return configuration.isDeprecatedForConsumption()
            }
        },
        RESOLVABLE {
            override fun isAllowed(configuration: ConfigurationInternal): Boolean {
                return configuration.isCanBeResolved()
            }

            override fun isDeprecated(configuration: ConfigurationInternal): Boolean {
                return configuration.isDeprecatedForResolution()
            }
        },
        DECLARABLE_AGAINST {
            override fun isAllowed(configuration: ConfigurationInternal): Boolean {
                return configuration.isCanBeDeclared()
            }

            override fun isDeprecated(configuration: ConfigurationInternal): Boolean {
                return configuration.isDeprecatedForDeclarationAgainst()
            }
        };

        abstract fun isAllowed(configuration: ConfigurationInternal): Boolean

        abstract fun isDeprecated(configuration: ConfigurationInternal): Boolean

        companion object {
            fun buildProperName(usage: ProperMethodUsage): String {
                @Suppress("deprecation") val capitalizedName = WordUtils.capitalizeFully(usage.name.replace('_', ' '))
                return capitalizedName
            }

            fun summarizeProperUsage(vararg properUsages: ProperMethodUsage): String {
                return Arrays.stream<ProperMethodUsage>(properUsages)
                    .map<String> { usage: ProperMethodUsage? -> ProperMethodUsage.Companion.buildProperName(usage!!) }
                    .collect(Collectors.joining(", "))
            }
        }
    }

    private class IllegalResolutionException(message: String) : GradleException(message), ResolutionProvider {
        private val resolution: String

        init {
            val userGuideLink = userManual("viewing_debugging_dependencies", "sub:resolving-unsafe-configuration-resolution-errors")
            resolution = "For more information, please refer to " + userGuideLink.url + " in the Gradle documentation."
        }

        val resolutions: MutableList<String>
            get() = mutableListOf<String>(resolution)
    }

    /**
     * This class encapsulates the logic for maintaining a collection that is derived off of extended configurations.
     * Specifically, it handles updating the derived collection when the set of extended configurations changes.
     */
    private class InheritedCollection<T>(
        private val all: CompositeDomainObjectSet<T?>,
        extendsFrom: ExtendedConfigurations,
        private val configurationToCollection: Function<Configuration, DomainObjectCollection<T?>>
    ) {
        private val inheritedCollections: MutableList<Provider<DomainObjectCollection<out T>>> = ArrayList<Provider<DomainObjectCollection<out T>>>()

        init {
            updateExtendedConfigurations(extendsFrom)
        }

        /**
         * Called when the extended configurations have changed, to update the derived collection.
         */
        fun updateExtendedConfigurations(extendsFrom: ExtendedConfigurations) {
            if (!inheritedCollections.isEmpty()) {
                inheritedCollections.forEach(Consumer { collectionProvider: Provider<DomainObjectCollection<out T?>?>? -> all.removeCollectionProvider(collectionProvider) })
                inheritedCollections.clear()
            }
            extendsFrom.visitConfigurations(ExtendedConfiguration.Visitor { configuration: ExtendedConfiguration? ->
                val providedCollection = configuration!!.mapToCollection<T?>(configurationToCollection)
                // The composite set contains more than just the inherited collections (for instance, it also contains the collection from
                // this configuration, and it's also technically possible to add additional, non-inherited collections to the composite),
                // so we keep track of the inherited collections separately
                inheritedCollections.add(providedCollection)
                all.addCollectionProvider(providedCollection)
            })
        }

        val allInherited: DomainObjectSet<T?>
            get() = all
    }

    companion object {
        private fun validateMutationType(mutationValidator: MutationValidator, type: MutationValidator.MutationType): Action<String> {
            return Action { arg: String -> mutationValidator.validateMutation(type) }
        }

        private fun isFullyResolved(currentState: Optional<ResolverResults>): Boolean {
            return currentState.map<Any>(ResolverResults::isFullyResolved).orElse(false)
        }
    }
}
