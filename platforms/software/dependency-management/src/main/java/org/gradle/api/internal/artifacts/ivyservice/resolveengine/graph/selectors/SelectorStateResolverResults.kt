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

import org.apache.commons.lang3.StringUtils
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.VirtualPlatformState
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.result.ComponentIdResolveResult

internal class SelectorStateResolverResults(versionComparator: Comparator<Version?>, versionParser: VersionParser, size: Int) {
    private val versionComparator: Comparator<Version?>
    private val versionParser: VersionParser
    private val results: MutableList<Registration>

    init {
        this.versionParser = versionParser
        this.results = ArrayList<Registration>(size)
        this.versionComparator = versionComparator
    }

    fun <T : ComponentResolutionState?> getResolved(componentFactory: ComponentStateFactory<T?>): MutableList<T?> {
        val failure: ModuleVersionResolveException? = null
        val resolved: MutableList<T?>? = null
        val hasSoftForce = hasSoftForce()

        val size = results.size
        for (i in 0..<size) {
            val entry = results.get(i)
            val selectorState = entry.selector
            val idResolveResult = entry.result

            if (selectorState.isForce() && !hasSoftForce) {
                val forcedComponent: T?
                T > componentForIdResolveResult<T?>(componentFactory, idResolveResult, selectorState)
                return mutableListOf<T?>(forcedComponent)
            }

            if (idResolveResult.mark(this)) {
                if (idResolveResult.getFailure() == null) {
                    val componentState: T?
                    T > componentForIdResolveResult<T?>(componentFactory, idResolveResult, selectorState)
                    if (resolved == null) {
                        resolved = ArrayList<T?>()
                    }
                    resolved.add(componentState)
                } else {
                    if (failure == null) {
                        failure = idResolveResult.getFailure()
                    }
                }
            }
        }

        if (resolved == null && failure != null) {
            throw failure
        }

        return if (resolved == null) mutableListOf<T?>() else resolved
    }

    private fun hasSoftForce(): Boolean {
        val size = results.size
        for (i in 0..<size) {
            val entry = results.get(i)
            val selectorState = entry.selector
            if (selectorState.isSoftForce()) {
                return true
            }
        }
        return false
    }

    /**
     * Check already resolved results for a compatible version, and use it for this dependency rather than re-resolving.
     */
    fun alreadyHaveResolutionForSelector(selector: ResolvableSelectorState): Boolean {
        val size = results.size
        var found: ComponentIdResolveResult? = null
        for (i in 0..<size) {
            val registration = results.get(i)
            val discovered = registration.result
            if (selectorAcceptsCandidate(selector, discovered, registration.selector.isFromLock())) {
                found = discovered
                selector.markResolved()
                break
            }
        }
        if (found != null) {
            register(selector, found)
            return true
        }
        return false
    }

    fun replaceExistingResolutionsWithBetterResult(candidate: ComponentIdResolveResult, isFromLock: Boolean): Boolean {
        // Check already-resolved dependencies and use this version if it's compatible
        var replaces = false
        val size = results.size
        for (i in 0..<size) {
            val registration = results.get(i)
            val previous = registration.result
            val previousSelector = registration.selector
            if (emptyVersion(previous) || sameVersion(previous, candidate) ||
                (selectorAcceptsCandidate(previousSelector, candidate, isFromLock) && lowerVersion(previous, candidate))
            ) {
                registration.result = candidate
                replaces = true
            }
        }
        return replaces
    }

    fun register(selector: ResolvableSelectorState, resolveResult: ComponentIdResolveResult) {
        results.add(Registration(selector, resolveResult))
    }

    private fun lowerVersion(existing: ComponentIdResolveResult, resolveResult: ComponentIdResolveResult): Boolean {
        if (existing.getFailure() == null && resolveResult.getFailure() == null) {
            val existingVersion = versionParser.transform(existing.moduleVersionId.getVersion())
            val candidateVersion = versionParser.transform(resolveResult.moduleVersionId.getVersion())

            val comparison = versionComparator.compare(candidateVersion, existingVersion)
            return comparison < 0
        }
        return false
    }

    val isEmpty: Boolean
        get() = results.isEmpty()

    private class Registration(selector: ResolvableSelectorState, result: ComponentIdResolveResult) {
        private val selector: ResolvableSelectorState
        private var result: ComponentIdResolveResult

        init {
            this.selector = selector
            this.result = result
        }

        override fun toString(): String {
            return selector.toString() + " -> " + result.moduleVersionId
        }
    }

    companion object {
        fun <T : ComponentResolutionState?> isVersionAllowedByPlatform(componentState: T?): Boolean {
            val platformOwners: MutableSet<VirtualPlatformState> = componentState!!.platformOwners
            if (!platformOwners.isEmpty()) {
                for (platformOwner in platformOwners) {
                    if (platformOwner.isGreaterThanForcedVersion(componentState.version)) {
                        return false
                    }
                }
            } else {
                val platform: VirtualPlatformState? = componentState.platformState
                // the platform itself is greater than the forced version
                return platform == null || !platform.isGreaterThanForcedVersion(componentState.version)
            }
            return true
        }

        fun <T : ComponentResolutionState?> componentForIdResolveResult(
            componentFactory: ComponentStateFactory<T?>,
            idResolveResult: ComponentIdResolveResult,
            selector: ResolvableSelectorState?
        ): T? {
            val component = componentFactory.getRevision(idResolveResult.id, idResolveResult.moduleVersionId, idResolveResult.state, idResolveResult.graphState)
            if (idResolveResult.isRejected) {
                component!!.reject()
            }
            return component
        }

        private fun emptyVersion(existing: ComponentIdResolveResult): Boolean {
            if (existing.getFailure() == null) {
                return existing.moduleVersionId.getVersion().isEmpty()
            }
            return false
        }

        private fun sameVersion(existing: ComponentIdResolveResult, resolveResult: ComponentIdResolveResult): Boolean {
            if (existing.getFailure() == null && resolveResult.getFailure() == null) {
                return existing.id.equals(resolveResult.id)
            }
            return false
        }

        private fun selectorAcceptsCandidate(dep: ResolvableSelectorState, candidate: ComponentIdResolveResult, candidateIsFromLock: Boolean): Boolean {
            if (hasFailure(candidate)) {
                return false
            }
            val versionConstraint = dep.getVersionConstraint()
            if (versionConstraint == null) {
                return dep.getSelector().matchesStrictly(candidate.id)
            }
            val versionSelector: VersionSelector? = versionConstraint.requiredSelector
            if (versionSelector != null &&
                (candidateIsFromLock || versionSelector.canShortCircuitWhenVersionAlreadyPreselected())
            ) {
                if (candidateIsFromLock && versionSelector is LatestVersionSelector) {
                    // Always assume a candidate from a lock will satisfy the latest version selector
                    return true
                }

                val version: String? = candidate.moduleVersionId.getVersion()
                if (StringUtils.isEmpty(version)) {
                    return false
                }
                return versionSelector.accept(version)
            }
            return false
        }

        private fun hasFailure(candidate: ComponentIdResolveResult): Boolean {
            return candidate.getFailure() != null
        }
    }
}
