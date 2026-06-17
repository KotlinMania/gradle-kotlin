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
package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy

import com.google.common.collect.ImmutableSet
import org.gradle.api.Action
import org.gradle.api.artifacts.CapabilitiesResolution
import org.gradle.api.artifacts.ComponentSelectionRules
import org.gradle.api.artifacts.DependencyResolveDetails
import org.gradle.api.artifacts.DependencySubstitutions
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.ComponentSelectorConverter
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.configurations.CachePolicy
import org.gradle.api.internal.artifacts.configurations.ConflictResolution
import org.gradle.api.internal.artifacts.configurations.MutationValidator
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal
import org.gradle.api.internal.artifacts.dsl.ModuleComponentSelectorParsers
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionsInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.internal.ImmutableActionSet
import org.gradle.internal.typeconversion.TimeUnitsParser
import org.gradle.vcs.internal.VcsResolver
import java.lang.Boolean
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.Any
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.String

class DefaultResolutionStrategy @Inject constructor(
    private val cachePolicy: CachePolicy,
    private val dependencySubstitutions: DependencySubstitutionsInternal,
    private val globalDependencySubstitutionRules: GlobalDependencyResolutionRules,
    private val vcsResolver: VcsResolver,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    private val componentSelectorConverter: ComponentSelectorConverter,
    private val dependencyLockingProvider: DependencyLockingProvider,
    private val capabilitiesResolution: CapabilitiesResolutionInternal,
    private val objectFactory: ObjectFactory
) : ResolutionStrategyInternal {
    private val forcedModules: MutableSet<Any> = LinkedHashSet<Any>()
    private var parsedForcedModules: MutableSet<ModuleComponentSelector>? = null
    private var conflictResolution = ConflictResolution.latest
    private val componentSelectionRules: DefaultComponentSelectionRules

    private var mutationValidator = MutationValidator.IGNORE

    private var dependencyLockingEnabled = false
    private var assumeFluidDependencies: Boolean
    private var sortOrder = ResolutionStrategy.SortOrder.DEFAULT
    private var failOnDynamicVersions = false
    private var failOnChangingVersions = false
    private var verifyDependencies = true
    private val useGlobalDependencySubstitutionRules: Property<Boolean>
    private var selectableVariantResults = false
    private var keepStateRequiredForGraphResolution = false

    init {
        this.componentSelectionRules = DefaultComponentSelectionRules(moduleIdentifierFactory)
        this.useGlobalDependencySubstitutionRules = objectFactory.property<Boolean>(Boolean::class.java).convention(true)
        // This is only used for testing purposes so we can test handling of fluid dependencies without adding dependency substitution rule
        assumeFluidDependencies = Boolean.getBoolean(ASSUME_FLUID_DEPENDENCIES)
    }

    override fun maybeDiscardStateRequiredForGraphResolution() {
        if (!keepStateRequiredForGraphResolution) {
            dependencySubstitutions.discard()
        }
    }

    override fun setMutationValidator(validator: MutationValidator) {
        mutationValidator = validator
        cachePolicy.setMutationValidator(validator)
        componentSelectionRules.setMutationValidator(validator)
        dependencySubstitutions.setMutationValidator(validator)
    }

    override fun getForcedModules(): MutableSet<ModuleVersionSelector> {
        return getParsedForcedModules().stream()
            .map<ModuleVersionSelector> { selector: ModuleComponentSelector? -> DefaultModuleVersionSelector.newSelector(selector) }
            .collect(ImmutableSet.toImmutableSet<ModuleVersionSelector>())
    }

    private fun getParsedForcedModules(): MutableSet<ModuleComponentSelector> {
        if (parsedForcedModules == null) {
            parsedForcedModules = FORCED_MODULES_PARSER.parseNotation(forcedModules)
        }
        return parsedForcedModules!!
    }

    override fun failOnVersionConflict(): ResolutionStrategy {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)
        this.conflictResolution = ConflictResolution.strict
        return this
    }

    override fun failOnDynamicVersions(): ResolutionStrategy {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)
        this.failOnDynamicVersions = true
        return this
    }

    override fun failOnChangingVersions(): ResolutionStrategy {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)
        this.failOnChangingVersions = true
        return this
    }

    override fun failOnNonReproducibleResolution(): ResolutionStrategy {
        failOnChangingVersions()
        failOnDynamicVersions()
        return this
    }

    override fun preferProjectModules() {
        conflictResolution = ConflictResolution.preferProjectModules
    }

    override fun activateDependencyLocking(): ResolutionStrategy {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)
        dependencyLockingEnabled = true
        return this
    }

    override fun deactivateDependencyLocking(): ResolutionStrategy {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)
        dependencyLockingEnabled = false
        return this
    }


    override fun sortArtifacts(sortOrder: ResolutionStrategy.SortOrder) {
        this.sortOrder = sortOrder
    }

    override fun capabilitiesResolution(action: Action<in CapabilitiesResolution>): ResolutionStrategy {
        action.execute(capabilitiesResolution)
        return this
    }

    override fun getCapabilitiesResolution(): CapabilitiesResolution {
        return capabilitiesResolution
    }

    override fun getSortOrder(): ResolutionStrategy.SortOrder {
        return sortOrder
    }

    override fun getConflictResolution(): ConflictResolution {
        return this.conflictResolution
    }

    override fun force(vararg notations: Any): DefaultResolutionStrategy {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)
        parsedForcedModules = null
        Collections.addAll<Any>(forcedModules, *notations)
        return this
    }

    override fun eachDependency(rule: Action<in DependencyResolveDetails>): ResolutionStrategy {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)
        dependencySubstitutions.allWithDependencyResolveDetails(rule, componentSelectorConverter)
        return this
    }

    override fun getDependencySubstitutionRule(): ImmutableActionSet<DependencySubstitutionInternal> {
        var result = ImmutableActionSet.empty<DependencySubstitutionInternal>()
        val forcedModules = getParsedForcedModules()
        if (!forcedModules.isEmpty()) {
            result = result.add(ModuleForcingResolveRule(forcedModules))
        }
        result = result.add(dependencySubstitutions.ruleAction)
        if (useGlobalDependencySubstitutionRules.get()) {
            result = result.add(globalDependencySubstitutionRules.dependencySubstitutionRules.ruleAction)
        }
        return result
    }

    override fun assumeFluidDependencies() {
        assumeFluidDependencies = true
    }

    override fun resolveGraphToDetermineTaskDependencies(): kotlin.Boolean {
        return assumeFluidDependencies
                || dependencySubstitutions.rulesMayAddProjectDependency()
                || (useGlobalDependencySubstitutionRules.get() && globalDependencySubstitutionRules.dependencySubstitutionRules.rulesMayAddProjectDependency())
                || vcsResolver.hasRules()
    }

    override fun setForcedModules(vararg moduleVersionSelectorNotations: Any): DefaultResolutionStrategy {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)
        this.forcedModules.clear()
        force(*moduleVersionSelectorNotations)
        return this
    }

    override fun getCachePolicy(): CachePolicy {
        return cachePolicy
    }

    override fun cacheDynamicVersionsFor(value: Int, units: String) {
        val timeUnit = TimeUnitsParser().parseNotation(units, value)
        cacheDynamicVersionsFor(timeUnit.getValue(), timeUnit.getTimeUnit())
    }

    override fun cacheDynamicVersionsFor(value: Int, units: TimeUnit) {
        this.cachePolicy.cacheDynamicVersionsFor(value, units)
    }

    override fun cacheChangingModulesFor(value: Int, units: String) {
        val timeUnit = TimeUnitsParser().parseNotation(units, value)
        cacheChangingModulesFor(timeUnit.getValue(), timeUnit.getTimeUnit())
    }

    override fun cacheChangingModulesFor(value: Int, units: TimeUnit) {
        this.cachePolicy.cacheChangingModulesFor(value, units)
    }

    override fun getComponentSelection(): ComponentSelectionRulesInternal {
        return componentSelectionRules
    }

    override fun componentSelection(action: Action<in ComponentSelectionRules>): ResolutionStrategy {
        action.execute(componentSelectionRules)
        return this
    }

    override fun getDependencySubstitution(): DependencySubstitutionsInternal {
        return dependencySubstitutions
    }

    override fun dependencySubstitution(action: Action<in DependencySubstitutions>): ResolutionStrategy {
        action.execute(dependencySubstitutions)
        return this
    }

    override fun getUseGlobalDependencySubstitutionRules(): Property<kotlin.Boolean> {
        return useGlobalDependencySubstitutionRules
    }

    override fun copy(): DefaultResolutionStrategy {
        val out = DefaultResolutionStrategy(
            cachePolicy.copy(),
            dependencySubstitutions.copy(),
            globalDependencySubstitutionRules,
            vcsResolver,
            moduleIdentifierFactory,
            componentSelectorConverter,
            dependencyLockingProvider,
            capabilitiesResolution,
            objectFactory
        )

        if (conflictResolution == ConflictResolution.strict) {
            out.failOnVersionConflict()
        } else if (conflictResolution == ConflictResolution.preferProjectModules) {
            out.preferProjectModules()
        }
        out.setForcedModules(forcedModules)
        for (ruleAction in componentSelectionRules.rules) {
            out.getComponentSelection().addRule(ruleAction)
        }
        if (isDependencyLockingEnabled()) {
            out.activateDependencyLocking()
        }
        if (isFailingOnDynamicVersions()) {
            out.failOnDynamicVersions()
        }
        if (isFailingOnChangingVersions()) {
            out.failOnChangingVersions()
        }
        if (!isDependencyVerificationEnabled()) {
            out.disableDependencyVerification()
        }
        out.getUseGlobalDependencySubstitutionRules().convention(useGlobalDependencySubstitutionRules.get())
        return out
    }

    override fun getDependencyLockingProvider(): DependencyLockingProvider {
        if (dependencyLockingEnabled) {
            return dependencyLockingProvider
        } else {
            throw IllegalStateException("Dependency locking is not enabled")
        }
    }

    override fun isDependencyLockingEnabled(): kotlin.Boolean {
        return dependencyLockingEnabled
    }

    override fun getCapabilitiesResolutionRules(): CapabilitiesResolutionInternal {
        return capabilitiesResolution
    }

    override fun isFailingOnDynamicVersions(): kotlin.Boolean {
        return failOnDynamicVersions
    }

    override fun isFailingOnChangingVersions(): kotlin.Boolean {
        return failOnChangingVersions
    }

    override fun isDependencyVerificationEnabled(): kotlin.Boolean {
        return verifyDependencies
    }

    override fun disableDependencyVerification(): ResolutionStrategy {
        verifyDependencies = false
        return this
    }

    override fun enableDependencyVerification(): ResolutionStrategy {
        verifyDependencies = true
        return this
    }

    override fun setIncludeAllSelectableVariantResults(selectableVariantResults: kotlin.Boolean) {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY)
        this.selectableVariantResults = selectableVariantResults
    }

    override fun getIncludeAllSelectableVariantResults(): kotlin.Boolean {
        return this.selectableVariantResults
    }

    override fun setKeepStateRequiredForGraphResolution(keepStateRequiredForGraphResolution: kotlin.Boolean) {
        this.keepStateRequiredForGraphResolution = keepStateRequiredForGraphResolution
    }

    companion object {
        private const val ASSUME_FLUID_DEPENDENCIES = "org.gradle.resolution.assumeFluidDependencies"
        private val FORCED_MODULES_PARSER = ModuleComponentSelectorParsers.multiParser("force()")
    }
}
