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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.component.ComponentSelectorInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.of
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints.equals
import org.gradle.api.internal.attributes.AttributeMergingException
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Describables
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.resolve.ModuleVersionResolveException
import java.util.LinkedList
import java.util.function.Consumer

/**
 * Represents the edges in the dependency graph.
 *
 * A dependency can have the following states:
 * 1. Unattached: in this case the state of the dependency is tied to the state of it's associated [SelectorState].
 * 2. Attached: in this case the Edge has been connected to actual nodes in the target component. Only possible if the [SelectorState] did not fail to resolve.
 */
internal class EdgeState(
    private val from: NodeState,
    private val dependencyMetadata: DependencyMetadata,
    requested: ComponentSelector,
    ruleDescriptors: ImmutableList<ComponentSelectionDescriptorInternal>,
    resolveFailure: ModuleVersionResolveException?,
    private val resolveState: ResolveState
) : DependencyGraphEdge {
    val dependencyState: DependencyState
    private val targetNodes: MutableList<NodeState> = LinkedList<NodeState>()
    private val isTransitive: Boolean
    private val isConstraint: Boolean

    private var selector: SelectorState? = null
    private var targetNodeSelectionFailure: ModuleVersionResolveException? = null

    /**
     * The accumulated exclusions that apply to this edge based on the paths from the root
     */
    var transitiveExclusions: ExcludeSpec? = null
        private set
    private var cachedEdgeExclusions: ExcludeSpec? = null
    private var cachedExclusions: ExcludeSpec? = null

    var isUnattached: Boolean = false
        private set

    init {
        this.isTransitive = from.isTransitive() && dependencyMetadata.isTransitive
        this.isConstraint = dependencyMetadata.isConstraint

        // TODO: DependencyState should eventually be merged into EdgeState
        this.dependencyState = DependencyState(dependencyMetadata, requested, ruleDescriptors, resolveFailure)
    }

    fun computeSelector(ancestorsStrictVersions: StrictVersionConstraints, deferSelection: Boolean): Boolean {
        val ignoreVersion = !dependencyState.isForced() && ancestorsStrictVersions.contains(dependencyState.getModuleIdentifier(resolveState.getComponentSelectorConverter()))
        val newSelector = resolveState.computeSelectorFor(dependencyState, ignoreVersion)
        if (this.selector !== newSelector) {
            clearSelector()
            newSelector.use(deferSelection)
            this.selector = newSelector
            return true
        }

        return false
    }

    fun clearSelector(): Boolean {
        if (this.selector != null) {
            val currentSelector = this.selector
            this.selector = null
            return currentSelector!!.release()
        }
        return false
    }

    override fun toString(): String {
        return String.format("%s -> %s", from.toString(), dependencyMetadata)
    }

    override fun getFrom(): NodeState {
        return from
    }

    override fun getDependencyMetadata(): DependencyMetadata {
        return dependencyMetadata
    }

    val targetComponent: ComponentState?
        /**
         * Returns the target component, if the edge has been successfully resolved.
         * Returns null if the edge failed to resolve, or has not (yet) been successfully resolved to a target component.
         */
        get() {
            if (selector == null || !selector!!.isResolved() || selector!!.getFailure() != null) {
                return null
            }
            val targetModule = selector!!.getTargetModule()
            if (targetModule.isInModuleConflict()) {
                // Do not download metadata for modules in conflict, as the module might
                // lose the conflict, and we want to avoid wasted IO.
                return null
            }

            return targetModule.getSelected()
        }

    fun getSelector(): SelectorState {
        checkNotNull(selector) { "No selector for " + this }
        return selector!!
    }

    override fun isTransitive(): Boolean {
        return isTransitive
    }

    fun attachToTargetNodes() {
        val targetComponent = this.targetComponent
        if (targetComponent == null || !this.isUsed) {
            // The selector failed or the module has been deselected or the edge source has been deselected. Do not attach.
            return
        }

        // We should never try to attach edges to a node in a module that has no incoming hard edges.
        assert(!targetComponent.getModule().isPending())

        calculateTargetNodes(targetComponent)
        if (!targetNodes.isEmpty()) {
            for (targetNode in targetNodes) {
                targetNode.addIncomingEdge(this)
            }
            selector!!.getTargetModule().removeUnattachedEdge(this)
        }
    }

    /**
     * Disconnect this edge from any node that it currently targets,
     * ensuring the target knows it is no longer being pointed to by
     * this edge.
     */
    fun detachFromTargetNodes() {
        if (!targetNodes.isEmpty()) {
            for (targetNode in targetNodes) {
                targetNode.removeIncomingEdge(this)
            }
            targetNodes.clear()
        }
        targetNodeSelectionFailure = null
    }

    /**
     * Call this method to attach a failure late in the process. This is typically
     * done when a failure is caused by graph validation. In that case we want to
     * perform as much resolution as possible, still have a valid graph, but in the
     * end fail resolution.
     */
    fun failWith(err: Throwable) {
        targetNodeSelectionFailure = ModuleVersionResolveException(selector!!.getSelector(), err)
    }

    /**
     * Ensure this edge it up-to-date and attached to the proper nodes, effectively
     * retargeting this edge from its previous potentially incorrect target, to
     * the new correct target.
     *
     *
     * Useful for when the state of the destination has changed, for example
     * when the selected component of the target module has changed.
     */
    fun retarget() {
        detachFromTargetNodes()
        if (this.isUsed) {
            attachToTargetNodes()
            if (targetNodes.isEmpty()) {
                selector!!.getTargetModule().addUnattachedEdge(this) // Attach failed, mark it as such.
            }
        }
    }

    override fun getAttributes(): ImmutableAttributes {
        val module = selector!!.getTargetModule()
        val componentSelector = dependencyMetadata.selector as ComponentSelectorInternal
        return resolveState.getAttributesFactory().safeConcat(module.getMergedConstraintAttributes(), componentSelector.getAttributes())
    }

    private fun calculateTargetNodes(targetComponent: ComponentState) {
        val targetComponentState = targetComponent.getResolveStateOrNull()
        targetNodes.clear() // TODO: Why not `detachFromTargetNodes()`?
        targetNodeSelectionFailure = null
        if (targetComponentState == null) {
            targetComponent.getModule().getPlatformState().addOrphanEdge(this)
            // Broken version
            return
        }
        if (isConstraint) {
            // We are a constraint and therefore may have deferred selection and attachment
            // of some other module/edge. Make sure to attach that deferred edge now that we have
            // performed selection.
            val unattachedEdges: MutableList<EdgeState> = targetComponent.getModule().getUnattachedEdges()
            if (!unattachedEdges.isEmpty()) {
                for (unattachedEdge in ArrayList<EdgeState?>(unattachedEdges)) {
                    if (!unattachedEdge!!.isConstraint()) {
                        unattachedEdge.attachToTargetNodes()
                    }
                }
            }

            // A constraint by definition attaches to any other nodes in the component it constrains.
            for (node in targetComponent.getNodes()) {
                var node = node
                node = node.maybeResolveReplacement()
                if (node.isSelected() && !node.isRoot()) {
                    targetNodes.add(node)
                }
            }

            // If we couldn't attach to any nodes, try to inherit any failures that hard edges have
            // encountered during selection.
            if (targetNodes.isEmpty()) {
                for (unattachedEdge in targetComponent.getModule().getUnattachedEdges()) {
                    if (!unattachedEdge.isConstraint() && unattachedEdge.targetNodeSelectionFailure != null) {
                        this.targetNodeSelectionFailure = unattachedEdge.targetNodeSelectionFailure
                        return
                    }
                }
            }
            return
        }

        val targetVariants: GraphVariantSelectionResult?
        try {
            targetVariants = selectTargetVariants(targetComponentState)
        } catch (mergeError: AttributeMergingException) {
            targetNodeSelectionFailure = ModuleVersionResolveException(getRequested(), org.gradle.internal.Factory {
                val attribute = mergeError.getAttribute()
                val constraintValue = mergeError.getLeftValue()
                val dependencyValue = mergeError.getRightValue()
                "Inconsistency between attributes of a constraint and a dependency, on attribute '" + attribute + "' : dependency requires '" + dependencyValue + "' while constraint required '" + constraintValue + "'"
            })
            return
        } catch (t: Exception) {
            // Failure to select the target variant/configurations from this component, given the dependency attributes/metadata.
            targetNodeSelectionFailure = ModuleVersionResolveException(getRequested(), t)
            return
        }

        for (targetVariant in targetVariants.variants) {
            val requestedNode = resolveState.getNode(
                targetComponent, targetVariant,
                targetVariants.isSelectedByVariantAwareResolution
            )
            val resolvedNode = requestedNode.maybeResolveReplacement()
            this.targetNodes.add(resolvedNode)
        }
    }

    /**
     * Determine which variants of a given target component that this edge should point to.
     */
    private fun selectTargetVariants(targetComponentState: ComponentGraphResolveState): GraphVariantSelectionResult {
        val variantSelector = resolveState.getVariantSelector()
        val attributes = resolveState.getAttributesFactory().concat(resolveState.getConsumerAttributes(), getAttributes())
        val consumerSchema = resolveState.getConsumerSchema()

        // First allow the dependency to override variant selection, if it has a special
        // variant selection mechanism for its ecosystem.
        val overrideVariants = dependencyMetadata.overrideVariantSelection(
            variantSelector,
            attributes,
            targetComponentState,
            consumerSchema
        )

        if (overrideVariants != null) {
            return GraphVariantSelectionResult(overrideVariants, false)
        }

        // Use attribute matching if it is supported.
        if (!targetComponentState.getCandidatesForGraphVariantSelection()!!.getVariantsForAttributeMatching()!!.isEmpty()) {
            val capabilitySelectors = dependencyMetadata.selector!!.getCapabilitySelectors()
            val selected = variantSelector.selectByAttributeMatching(
                attributes,
                capabilitySelectors,
                targetComponentState,
                consumerSchema,
                dependencyMetadata.artifacts!!
            )

            return GraphVariantSelectionResult(mutableListOf<VariantGraphResolveState>(selected), true)
        }

        // Otherwise, for target components that don't support attribute matching, fallback to legacy variant selection.
        val legacyVariants = dependencyMetadata.selectLegacyVariants(
            variantSelector,
            attributes,
            targetComponentState,
            consumerSchema
        )

        return GraphVariantSelectionResult(legacyVariants, false)
    }

    override fun isFromLock(): Boolean {
        return dependencyState.isFromLock()
    }

    class GraphVariantSelectionResult(val variants: MutableList<out VariantGraphResolveState>, val isSelectedByVariantAwareResolution: Boolean)

    override fun getExclusions(): ExcludeSpec {
        if (cachedExclusions == null) {
            computeExclusions()
        }
        return cachedExclusions!!
    }

    private fun computeExclusions() {
        val excludes: MutableList<ExcludeMetadata> = dependencyMetadata.excludes!!
        if (excludes.isEmpty()) {
            cachedExclusions = transitiveExclusions
        } else {
            computeExclusionsWhenExcludesPresent(excludes)
        }
    }

    private fun computeExclusionsWhenExcludesPresent(excludes: MutableList<ExcludeMetadata>) {
        val moduleExclusions = resolveState.getModuleExclusions()
        val edgeExclusions = moduleExclusions.excludeAny(excludes)
        cachedExclusions = moduleExclusions.excludeAny(edgeExclusions, transitiveExclusions)
    }

    val edgeExclusions: ExcludeSpec
        get() {
            if (cachedEdgeExclusions == null) {
                val excludes: MutableList<ExcludeMetadata> = dependencyMetadata.excludes!!
                val moduleExclusions = resolveState.getModuleExclusions()
                if (excludes.isEmpty()) {
                    return moduleExclusions.nothing()
                }
                cachedEdgeExclusions = moduleExclusions.excludeAny(excludes)
            }
            return cachedEdgeExclusions
        }

    override fun getRequested(): ComponentSelector {
        return resolveState.desugarSelector(dependencyState.getRequested())
    }

    override fun getFailure(): ModuleVersionResolveException {
        if (targetNodeSelectionFailure != null) {
            return targetNodeSelectionFailure!!
        }
        var selectorFailure = selector!!.getFailure()
        if (selectorFailure != null) {
            return selectorFailure
        }
        val selectedComponent = selector!!.getTargetModule().getSelected()
        if (selectedComponent == null) {
            val selectors = selector!!.getTargetModule().getSelectors()
            for (state in selectors) {
                selectorFailure = state.getFailure()
                if (selectorFailure != null) {
                    return selectorFailure
                }
            }
            throw IllegalStateException("Expected to find a selector with a failure but none was found")
        }
        return selectedComponent.getMetadataResolveFailure()!!
    }

    override fun getTargetNodes(): MutableList<NodeState> {
        return targetNodes
    }

    val firstTargetNode: NodeState?
        get() {
            if (targetNodes.isEmpty()) {
                return null
            }

            return targetNodes.get(0)
        }

    override fun getReason(): ComponentSelectionReasonInternal {
        val dependencyReasons = ImmutableSet.builderWithExpectedSize<ComponentSelectionDescriptorInternal>(4)
        visitSelectionReasons(Consumer { element: ComponentSelectionDescriptorInternal -> dependencyReasons.add(element) })
        return of(dependencyReasons.build())
    }

    override fun visitSelectionReasons(visitor: Consumer<ComponentSelectionDescriptorInternal>) {
        visitor.accept(this.mainReason)

        val ruleDescriptors = dependencyState.getRuleDescriptors()
        if (!ruleDescriptors.isEmpty()) {
            ruleDescriptors.forEach(visitor)
        }

        if (dependencyState.isForced()) {
            visitor.accept(FORCED)
        }
    }

    private val mainReason: ComponentSelectionDescriptorInternal
        get() {
            if (selector != null && selector!!.isVersionProvidedByAncestor()) {
                return withDependencyReason(BY_ANCESTOR)
            } else if (dependencyState.getDependency().isConstraint) {
                return withSelectorReason(withDependencyReason(CONSTRAINT))
            } else {
                return withSelectorReason(withDependencyReason(REQUESTED))
            }
        }

    private fun withDependencyReason(dependencyDescriptor: ComponentSelectionDescriptorInternal): ComponentSelectionDescriptorInternal {
        var dependencyDescriptor = dependencyDescriptor
        val reason = dependencyState.getDependency().reason
        if (reason != null) {
            dependencyDescriptor = dependencyDescriptor.withDescription(Describables.of(reason))!!
        }
        return dependencyDescriptor
    }

    private fun withSelectorReason(descriptor: ComponentSelectionDescriptorInternal): ComponentSelectionDescriptorInternal {
        return if (selector == null) descriptor else selector!!.maybeEnhanceReason(descriptor)
    }

    override fun isConstraint(): Boolean {
        return isConstraint
    }

    fun updateTransitiveExcludes(newResolutionFilter: ExcludeSpec) {
        if (isConstraint) {
            // Constraint do not carry excludes on a path
            return
        }
        transitiveExclusions = newResolutionFilter
        cachedExclusions = null
    }

    fun updateTransitiveExcludesAndRequeueTargetNodes(newResolutionFilter: ExcludeSpec) {
        updateTransitiveExcludes(newResolutionFilter)
        for (targetNode in targetNodes) {
            targetNode.clearTransitiveExclusionsAndEnqueue()
        }
    }

    fun recomputeSelectorAndRequeueTargetNodes(ancestorsStrictVersions: StrictVersionConstraints, discoveredEdges: MutableCollection<EdgeState>) {
        if (computeSelector(ancestorsStrictVersions, false)) {
            discoveredEdges.add(this)
        }
        // TODO: If we compute the selector for this edge and it changes, we shouldn't add the (potentially) invalid target nodes to the queue.
        // If we added this edge to `discoveredEdges`, then we will recompute target nodes and there is no point in adding the current target nodes to the queue.
        for (targetNode in targetNodes) {
            resolveState.onMoreSelected(targetNode)
        }
    }

    fun markUnattached() {
        this.isUnattached = true
    }

    fun markNotUnattached() {
        this.isUnattached = false
    }

    val isUsed: Boolean
        /**
         * Indicates whether the edge is currently listed as outgoing in a node.
         * It can be either a full edge or an edge to a virtual platform.
         *
         * @return true if used, false otherwise
         */
        get() = selector != null

    val isArtifactOnlyEdge: Boolean
        get() = !isTransitive && !dependencyMetadata.artifacts!!.isEmpty()
}
