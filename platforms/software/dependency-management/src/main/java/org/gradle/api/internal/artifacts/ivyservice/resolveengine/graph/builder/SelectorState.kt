/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder

import com.google.common.base.Joiner
import org.gradle.api.Describable
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.ResolvableSelectorState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints.equals
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata.Companion.forDependency
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.RejectedByAttributesVersion
import org.gradle.internal.resolve.RejectedByRuleVersion
import org.gradle.internal.resolve.RejectedBySelectorVersion
import org.gradle.internal.resolve.RejectedVersion
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult
import org.gradle.internal.resolve.result.ComponentIdResolveResult
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult
import org.gradle.util.internal.TextUtil
import java.util.function.Consumer

/**
 * Resolution state for a given module version selector.
 *
 * There are 3 possible states:
 * 1. The selector has been newly added to a `ModuleResolveState`. In this case [.resolved] will be `false`.
 * 2. The selector failed to resolve. In this case [.failure] will be `!= null`.
 * 3. The selector was part of resolution to a particular module version.
 * In this case [.resolved] will be `true` and [ModuleResolveState.getSelected] will point to the selected component.
 */
internal class SelectorState(
    dependencyState: DependencyState,
    private val resolver: DependencyToComponentIdResolver,
    private val resolveState: ResolveState,
    targetModuleId: ModuleIdentifier,
    val isVersionProvidedByAncestor: Boolean
) : DependencyGraphSelector, ResolvableSelectorState {
    private val dependencyState: DependencyState
    private val versionConstraint: ResolvedVersionConstraint
    private val isProjectSelector: Boolean
    private val attributeDesugaring: AttributeDesugaring

    private var preferResult: ComponentIdResolveResult? = null
    private var requireResult: ComponentIdResolveResult? = null

    /**
     * Return any failure to resolve the component selector to id, or failure to resolve component metadata for id.
     */
    var failure: ModuleVersionResolveException? = null
        private set
    var targetModule: ModuleResolveState
        private set
    var isResolved: Boolean = false
        private set
    private var forced = false
    private var softForced = false
    private var fromLock = false
    private var reusable = false
    private var markedReusableAlready = false
    private var changing = false

    // An internal counter used to track the number of outgoing edges
    // that use this selector. Since a module resolve state tracks all selectors
    // for this module, when considering selectors that need to be used when
    // choosing a version, we must only consider the ones which currently have
    // outgoing edges pointing to them. If not, then it means the module was
    // evicted, but it can still be reintegrated later in a different path.
    private var outgoingEdgeCount = 0

    init {
        this.targetModule = resolveState.getModule(targetModuleId)
        update(dependencyState)
        this.dependencyState = dependencyState
        this.versionConstraint =
            (if (isVersionProvidedByAncestor) resolveState.resolveVersionConstraint(org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint.of()) else resolveState.resolveVersionConstraint(
                dependencyState.getDependency().selector
            ))!!
        this.isProjectSelector = getSelector() is ProjectComponentSelector
        this.attributeDesugaring = resolveState.getAttributeDesugaring()
    }

    override fun isProject(): Boolean {
        // this is cached because used very often in sorting selectors
        return isProjectSelector
    }

    fun use(deferSelection: Boolean) {
        outgoingEdgeCount++
        if (outgoingEdgeCount == 1) {
            targetModule.addSelector(this, deferSelection)
        }
    }

    /**
     * Decrease the count of edges using this selector, updating state on the target module if
     * this selector is no longer used by any edges.
     *
     * @return True if releasing this selector requires the target module to be reselected.
     */
    fun release(): Boolean {
        outgoingEdgeCount--
        assert(outgoingEdgeCount >= 0) { "Inconsistent selector state detected for '" + this + "': outgoing edge count cannot be negative" }
        if (outgoingEdgeCount == 0) {
            targetModule.removeSelector(this)
            val needsSelection = markForReuse()
            this.isResolved = false
            return needsSelection
        }
        return false
    }

    override fun toString(): String {
        return dependencyState.getDependency().toString()
    }

    override fun getRequested(): ComponentSelector {
        return attributeDesugaring.desugarSelector(dependencyState.getRequested())
    }

    /**
     * Does the work of actually resolving a component selector to a component identifier.
     */
    override fun resolve(allRejects: VersionSelector): ComponentIdResolveResult {
        val requiredSelector: VersionSelector? = if (versionConstraint == null) null else versionConstraint.requiredSelector
        requireResult = resolve(requiredSelector, allRejects, requireResult!!)
        return requireResult!!
    }

    override fun resolvePrefer(allRejects: VersionSelector): ComponentIdResolveResult {
        if (versionConstraint == null || versionConstraint.preferredSelector == null) {
            return null
        }
        preferResult = resolve(versionConstraint.preferredSelector, allRejects, preferResult!!)
        return preferResult!!
    }

    private fun resolve(selector: VersionSelector?, rejector: VersionSelector, previousResult: ComponentIdResolveResult): ComponentIdResolveResult {
        try {
            if (!requiresResolve(previousResult, rejector)) {
                return previousResult
            }

            val idResolveResult: BuildableComponentIdResolveResult = DefaultBuildableComponentIdResolveResult()
            if (dependencyState.getSubstitutionFailure() != null) {
                idResolveResult.failed(dependencyState.getSubstitutionFailure())
            } else {
                val firstArtifact = getFirstDependencyArtifact()
                val overrideMetadata = forDependency(changing, firstArtifact)
                val requestAttributes = resolveState.getAttributesFactory().concat(resolveState.getConsumerAttributes(), targetModule.getMergedConstraintAttributes())
                resolver.resolve(dependencyState.getDependency().selector!!, overrideMetadata, selector!!, rejector, idResolveResult, requestAttributes)
            }

            if (idResolveResult.getFailure() != null) {
                failure = idResolveResult.getFailure()
            }

            return idResolveResult
        } finally {
            this.isResolved = true
        }
    }

    private fun requiresResolve(previousResult: ComponentIdResolveResult?, allRejects: VersionSelector?): Boolean {
        this.reusable = false
        // If we've never resolved, must resolve
        if (previousResult == null) {
            return true
        }

        // If previous resolve failed, no point in re-resolving
        if (previousResult.getFailure() != null) {
            return false
        }

        // If the previous result was rejected, do not need to re-resolve (new rejects will be a superset of previous rejects)
        if (previousResult.isRejected) {
            return false
        }

        // If the previous result is still not rejected, do not need to re-resolve. The previous result is still good.
        return allRejects != null && allRejects.accept(previousResult.moduleVersionId.getVersion())
    }

    override fun markResolved() {
        this.isResolved = true
    }

    /**
     * Marks a selector for reuse,
     * indicating it could be used again for resolution
     *
     * @return true if marking this selector for reuse requires the target module to be reselected
     */
    fun markForReuse(): Boolean {
        if (!this.isResolved) {
            // Selector was marked for deferred selection - let's not trigger selection now
            return false
        }
        this.reusable = true
        if (markedReusableAlready) {
            // TODO: We have hit an unstable graph. This selector has already added, removed, added again,
            // and we are removing it once again. We should fail the resolution here and ask the user
            // to fix the graph -- likely by adding a version constraint.
            return false
        } else {
            markedReusableAlready = true
            return true
        }
    }

    /**
     * Checks if the selector affects selection at the moment it is added to a module
     *
     * @return `true` if the selector can resolve, `false` otherwise
     */
    fun canAffectSelection(): Boolean {
        if (reusable) {
            return true
        }
        return !this.isResolved
    }

    /**
     * Overrides the component that is the chosen for this selector.
     * This happens when the `ModuleResolveState` is restarted, during conflict resolution or version range merging.
     */
    fun overrideSelection(selected: ComponentState) {
        this.isResolved = true
        this.reusable = false

        // Target module can change, if this is called as the result of a module or capability replacement conflict.
        this.targetModule = selected.getModule()
    }

    fun visitSelectionReasons(visitor: Consumer<ComponentSelectionDescriptorInternal>) {
        val result = this.result
        if (result != null) {
            for (rejectedVersion in result.rejectedVersions!!) {
                val version: String = rejectedVersion.id.getVersion()
                if (rejectedVersion is RejectedByRuleVersion) {
                    val reason = rejectedVersion.reason
                    visitor.accept(
                        ComponentSelectionReasons.REJECTION.withDescription(
                            org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.SelectorState.RejectedByRuleReason(
                                version,
                                reason
                            )
                        )!!
                    )
                } else if (rejectedVersion is RejectedByAttributesVersion) {
                    visitor.accept(
                        ComponentSelectionReasons.REJECTION.withDescription(
                            org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.SelectorState.RejectedByAttributesReason(
                                rejectedVersion
                            )
                        )!!
                    )
                }
            }
        }
    }

    /**
     * Add additional details to the given reason descriptor, including any 'unmatched' or 'rejected' reasons.
     */
    fun maybeEnhanceReason(descriptor: ComponentSelectionDescriptorInternal): ComponentSelectionDescriptorInternal {
        val result = this.result
        if (result == null) {
            return descriptor
        }

        val rejectedVersions: MutableCollection<RejectedVersion> = result.rejectedVersions
        if (!rejectedVersions.isEmpty()) {
            var rejectedBySelector: MutableList<String>? = null
            for (rejectedVersion in rejectedVersions) {
                if (rejectedVersion is RejectedBySelectorVersion) {
                    if (rejectedBySelector == null) {
                        rejectedBySelector = ArrayList<String>(rejectedVersions.size)
                    }
                    rejectedBySelector.add(rejectedVersion.id.getVersion())
                }
            }
            if (rejectedBySelector != null) {
                return descriptor.withDescription(RejectedBySelectorReason(rejectedBySelector, descriptor))!!
            }
        }

        val unmatchedVersions: MutableSet<String> = result.unmatchedVersions
        if (!unmatchedVersions.isEmpty()) {
            return descriptor.withDescription(UnmatchedVersionsReason(unmatchedVersions, descriptor))!!
        }

        return descriptor
    }

    private val result: ComponentIdResolveResult?
        get() {
            if (preferResult == null) {
                return requireResult
            } else {
                return preferResult
            }
        }

    val dependencyMetadata: DependencyMetadata
        get() = dependencyState.getDependency()

    override fun getFirstDependencyArtifact(): IvyArtifactName {
        val artifacts: MutableList<IvyArtifactName> = dependencyState.getDependency().artifacts!!
        return (if (artifacts.isEmpty()) null else artifacts.get(0))!!
    }

    override fun isChanging(): Boolean {
        return changing
    }

    override fun getVersionConstraint(): ResolvedVersionConstraint? {
        return versionConstraint
    }

    override fun getSelector(): ComponentSelector {
        return dependencyState.getDependency().selector!!
    }

    override fun isForce(): Boolean {
        return forced
    }

    override fun isSoftForce(): Boolean {
        return softForced
    }

    override fun isFromLock(): Boolean {
        return fromLock
    }

    override fun hasStrongOpinion(): Boolean {
        return forced || (versionConstraint != null && versionConstraint.isStrict)
    }

    fun update(dependencyState: DependencyState) {
        if (dependencyState !== this.dependencyState) {
            if (!forced && dependencyState.isForced()) {
                forced = true
                if (dependencyState.getDependency() is LenientPlatformDependencyMetadata) {
                    softForced = true
                    targetModule.resolveOptimizations.declareForcedPlatformInUse()
                }
                this.isResolved = false // when a selector changes from non forced to forced, we must reselect
            }
            if (!fromLock && dependencyState.isFromLock()) {
                fromLock = true
                this.isResolved = false // when a selector changes from non lock to lock, we must reselect
            }

            changing = changing || dependencyState.getDependency().isChanging
        }
    }

    private class UnmatchedVersionsReason(private val rejectedVersions: MutableSet<String>, private val descriptor: ComponentSelectionDescriptorInternal) : Describable {
        private val hashCode: Int

        init {
            this.hashCode = computeHashCode(descriptor, rejectedVersions)
        }

        override fun getDisplayName(): String {
            val hasCustomDescription = descriptor.hasCustomDescription()
            val sb = StringBuilder(estimateSize(hasCustomDescription))
            sb.append("didn't match version").append(TextUtil.getPluralEnding(rejectedVersions)).append(" ")
            Joiner.on(", ").appendTo(sb, rejectedVersions)
            if (hasCustomDescription) {
                sb.append(" because ").append(descriptor.getDescription())
            }
            return sb.toString()
        }

        fun estimateSize(hasCustomDescription: Boolean): Int {
            return 24 + rejectedVersions.size * 8 + (if (hasCustomDescription) 24 else 0)
        }

        override fun equals(o: Any): Boolean {
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as UnmatchedVersionsReason
            return rejectedVersions == that.rejectedVersions &&
                    descriptor == that.descriptor
        }

        override fun hashCode(): Int {
            return hashCode
        }

        companion object {
            private fun computeHashCode(descriptor: ComponentSelectionDescriptorInternal, rejectedVersions: MutableSet<String>): Int {
                var result = rejectedVersions.hashCode()
                result = 31 * result + descriptor.hashCode()
                return result
            }
        }
    }

    private class RejectedByRuleReason(private val version: String, private val reason: String?) : Describable {
        override fun getDisplayName(): String {
            return version + " by rule" + (if (reason != null) " because " + reason else "")
        }
    }

    private class RejectedByAttributesReason(private val version: RejectedByAttributesVersion) : Describable {
        override fun getDisplayName(): String {
            val formatter = TreeFormatter()
            version.describeTo(formatter)
            return "version " + formatter
        }
    }

    private class RejectedBySelectorReason(private val rejectedVersions: MutableList<String>, private val descriptor: ComponentSelectionDescriptorInternal) : Describable {
        private val hashCode: Int

        init {
            this.hashCode = computeHashCode(descriptor, rejectedVersions)
        }

        override fun getDisplayName(): String {
            val hasCustomDescription = descriptor.hasCustomDescription()
            val sb = StringBuilder(estimateSize(hasCustomDescription))
            sb.append("rejected version").append(TextUtil.getPluralEnding(rejectedVersions)).append(" ")
            Joiner.on(", ").appendTo(sb, rejectedVersions)
            if (hasCustomDescription) {
                sb.append(" because ").append(descriptor.getDescription())
            }
            return sb.toString()
        }

        fun estimateSize(hasCustomDescription: Boolean): Int {
            return 20 + rejectedVersions.size * 8 + (if (hasCustomDescription) 24 else 0)
        }

        override fun equals(o: Any): Boolean {
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as RejectedBySelectorReason
            return rejectedVersions == that.rejectedVersions &&
                    descriptor == that.descriptor
        }

        override fun hashCode(): Int {
            return hashCode
        }

        companion object {
            private fun computeHashCode(descriptor: ComponentSelectionDescriptorInternal, rejectedVersions: MutableList<String>): Int {
                var result = rejectedVersions.hashCode()
                result = 31 * result + descriptor.hashCode()
                return result
            }
        }
    }
}
