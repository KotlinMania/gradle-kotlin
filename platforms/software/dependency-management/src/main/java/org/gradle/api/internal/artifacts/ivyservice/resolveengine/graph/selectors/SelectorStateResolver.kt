/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors

import com.google.common.collect.Sets
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.UnionVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ModuleSelectors
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ResolveOptimizations
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultConflictResolverDetails
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictResolutionDetails
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints.equals
import org.gradle.internal.UncheckedException
import org.gradle.internal.resolve.result.ComponentIdResolveResult
import java.util.TreeSet
import java.util.stream.Collectors

class SelectorStateResolver<T : ComponentResolutionState?>(
    conflictResolver: ModuleConflictResolver<T?>,
    componentFactory: ComponentStateFactory<T?>,
    rootComponent: T?,
    resolveOptimizations: ResolveOptimizations,
    versionComparator: Comparator<Version?>,
    versionParser: VersionParser
) {
    private val conflictResolver: ModuleConflictResolver<T?>
    private val componentFactory: ComponentStateFactory<T?>
    private val rootComponent: T?
    private val rootModuleId: ModuleIdentifier?
    private val resolveOptimizations: ResolveOptimizations
    private val versionComparator: Comparator<Version?>
    private val versionParser: VersionParser

    init {
        this.conflictResolver = conflictResolver
        this.componentFactory = componentFactory
        this.rootComponent = rootComponent
        this.rootModuleId = rootComponent!!.id.getModule()
        this.resolveOptimizations = resolveOptimizations
        this.versionComparator = versionComparator
        this.versionParser = versionParser
    }

    fun selectBest(moduleId: ModuleIdentifier, selectors: ModuleSelectors<out ResolvableSelectorState>): T? {
        val allRejects = createAllRejects(selectors)
        var candidates = resolveSelectors(selectors, allRejects)
        assert(!candidates.isEmpty())

        // If the module matches, add the root component into the mix
        if (moduleId == rootModuleId && !candidates.contains(rootComponent)) {
            candidates = ArrayList<T?>(candidates)
            candidates.add(rootComponent)
        }

        // If we have a single common resolution, no conflicts to resolve
        if (candidates.size == 1) {
            return candidates.get(0)
        }

        if (resolveOptimizations.mayHaveForcedPlatforms()) {
            val allowed = candidates
                .stream()
                .filter { componentState: T? -> SelectorStateResolverResults.Companion.isVersionAllowedByPlatform(componentState) }
                .collect(Collectors.toList())
            if (!allowed.isEmpty()) {
                if (allowed.size == 1) {
                    return allowed.get(0)
                }
                candidates = allowed
            }
        }

        // Perform conflict resolution
        return resolveConflicts(candidates)
    }

    private fun resolveSelectors(selectors: ModuleSelectors<out ResolvableSelectorState>, allRejects: VersionSelector?): MutableList<T?> {
        if (selectors.size() == 1) {
            val selectorState: ResolvableSelectorState? = selectors.first()
            // Short-circuit selector merging for single selector without 'prefer'
            if (selectorState!!.getVersionConstraint() == null || selectorState.getVersionConstraint()!!.preferredSelector == null) {
                return resolveSingleSelector(selectorState, allRejects)
            }
        }

        val results = buildResolveResults(selectors, allRejects)
        if (results.isEmpty()) {
            // Every selector was empty: simply 'resolve' one of them
            return resolveSingleSelector(selectors.first()!!, allRejects)
        }
        return results
    }

    private fun resolveSingleSelector(selectorState: ResolvableSelectorState, allRejects: VersionSelector?): MutableList<T?> {
        assert(selectorState.getVersionConstraint() == null || selectorState.getVersionConstraint()!!.preferredSelector == null)
        val resolved = selectorState.resolve(allRejects)
        val selected: T? = SelectorStateResolverResults.Companion.componentForIdResolveResult<T?>(componentFactory, resolved, selectorState)
        return mutableListOf<T?>(selected)
    }

    /**
     * Resolves a set of dependency selectors to component identifiers, making an attempt to find best matches.
     * If a single version can satisfy all of the selectors, the result will reflect this.
     * If not, a minimal set of versions will be provided in the result, and conflict resolution will be required to choose.
     */
    private fun buildResolveResults(selectors: ModuleSelectors<out ResolvableSelectorState>, allRejects: VersionSelector?): MutableList<T?> {
        val results = SelectorStateResolverResults(versionComparator, versionParser, selectors.size())
        var preferResults: TreeSet<ComponentIdResolveResult?>? = null // Created only on demand

        for (selector in selectors) {
            resolveRequireConstraint(results, selector, allRejects)
            preferResults = maybeResolvePreferConstraint(preferResults, selector, allRejects)
        }

        integratePreferResults(selectors, results, preferResults)
        return results.getResolved<T?>(componentFactory)
    }

    /**
     * Resolve the 'require' constraint of the selector.
     * A version will be registered for this selector, and it will participate in conflict resolution.
     */
    private fun resolveRequireConstraint(results: SelectorStateResolverResults, selector: ResolvableSelectorState, allRejects: VersionSelector?) {
        // Check already resolved results for a compatible version, and use it for this dependency rather than re-resolving.
        if (results.alreadyHaveResolutionForSelector(selector)) {
            return
        }

        // Need to perform the actual resolve
        val result = selector.resolve(allRejects)

        if (result.getFailure() != null) {
            results.register(selector, result)
            return
        }

        results.replaceExistingResolutionsWithBetterResult(result, selector.isFromLock())
        results.register(selector, result)
    }

    /**
     * Collect the result of the 'prefer' constraint of the selector, if present and not failing.
     * These results are integrated with the 'require' results in the second phase.
     */
    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private fun maybeResolvePreferConstraint(
        previousResults: TreeSet<ComponentIdResolveResult?>?,
        selector: ResolvableSelectorState,
        allRejects: VersionSelector?
    ): TreeSet<ComponentIdResolveResult?>? {
        var preferResults = previousResults
        val resolvedPreference = selector.resolvePrefer(allRejects)
        if (resolvedPreference != null && resolvedPreference.getFailure() == null) {
            if (preferResults == null) {
                preferResults = Sets.newTreeSet<ComponentIdResolveResult?>(DescendingResolveResultComparator(versionComparator, versionParser))
            }
            preferResults.add(resolvedPreference)
        }

        return preferResults
    }

    /**
     * Given the result of resolving any 'prefer' constraints, see if these can be used to further refine the results
     * of resolving the 'require' constraints.
     */
    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private fun integratePreferResults(selectors: ModuleSelectors<out ResolvableSelectorState>, results: SelectorStateResolverResults, preferResults: TreeSet<ComponentIdResolveResult?>?) {
        if (preferResults == null) {
            return
        }

        // If no result from 'require', just use the highest preferred version (no range merging)
        if (results.isEmpty()) {
            val highestPreferredVersion = preferResults.first()
            results.register(selectors.first(), highestPreferredVersion)
            return
        }

        for (preferResult in preferResults) {
            // Use the highest preferred version that refines the chosen 'require' selector
            if (results.replaceExistingResolutionsWithBetterResult(preferResult, false)) {
                break
            }
        }
    }

    private fun createAllRejects(selectors: ModuleSelectors<out ResolvableSelectorState>): VersionSelector? {
        var rejectSelectors: MutableList<VersionSelector?>? = null
        for (selector in selectors) {
            val versionConstraint = selector.getVersionConstraint()
            if (versionConstraint != null && versionConstraint.rejectedSelector != null) {
                if (rejectSelectors == null) {
                    rejectSelectors = ArrayList<VersionSelector?>(selectors.size())
                }
                rejectSelectors.add(versionConstraint.rejectedSelector)
            }
        }
        if (rejectSelectors == null) {
            return null
        }
        if (rejectSelectors.size == 1) {
            return rejectSelectors.get(0)
        }
        return UnionVersionSelector(rejectSelectors)
    }

    private fun resolveConflicts(candidates: MutableCollection<T?>): T? {
        // Do conflict resolution to choose the best out of current selection and candidate.
        val details: ConflictResolverDetails<T?> = DefaultConflictResolverDetails<T?>(candidates)
        conflictResolver.select(details)
        val selected = details.selected
        if (details.hasFailure()) {
            throw UncheckedException.throwAsUncheckedException(details.failure!!)
        } else {
            val desc = ComponentSelectionReasons.CONFLICT_RESOLUTION
            selected!!.addCause(desc.withDescription(VersionConflictResolutionDetails(candidates))!!)
        }
        return selected
    }

    private class DescendingResolveResultComparator(versionComparator: Comparator<Version?>, versionParser: VersionParser) : Comparator<ComponentIdResolveResult?> {
        private val versionComparator: Comparator<Version?>
        private val versionParser: VersionParser

        init {
            this.versionComparator = versionComparator
            this.versionParser = versionParser
        }

        override fun compare(o1: ComponentIdResolveResult, o2: ComponentIdResolveResult): Int {
            return versionComparator.compare(versionParser.transform(o2.moduleVersionId.getVersion()), versionParser.transform(o1.moduleVersionId.getVersion()))
        }
    }
}
