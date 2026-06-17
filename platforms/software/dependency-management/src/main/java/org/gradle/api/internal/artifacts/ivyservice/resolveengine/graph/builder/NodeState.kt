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

import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap
import org.apache.commons.lang3.StringUtils
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.ComponentSelectorConverter
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.SubstitutionResult
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints.equals
import org.gradle.api.internal.capabilities.ImmutableCapability
import org.gradle.api.internal.capabilities.ShadowedCapability
import org.gradle.internal.Pair
import org.gradle.internal.Try
import org.gradle.internal.collect.PersistentSet
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector.Companion.newSelector
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState
import org.gradle.internal.component.model.ComponentGraphResolveMetadata.getId
import org.gradle.internal.component.model.ComponentGraphResolveState.getId
import org.gradle.internal.component.model.DelegatingDependencyMetadata
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.component.model.VariantGraphResolveMetadata
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getId
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getName
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Objects
import java.util.stream.Collectors
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet
import kotlin.collections.MutableCollection
import kotlin.collections.MutableList
import kotlin.collections.MutableSet
import kotlin.collections.mutableListOf
import kotlin.collections.mutableSetOf

/**
 * Represents a node in the dependency graph.
 */
open class NodeState(
    private val nodeId: Long,
    private val component: ComponentState,
    private val resolveState: ResolveState,
    private val variantState: VariantGraphResolveState,
    private val selectedByVariantAwareResolution: Boolean
) : DependencyGraphNode {
    private val incomingEdges: MutableList<EdgeState> = ArrayList<EdgeState>()
    private val outgoingEdges: MutableList<EdgeState> = ArrayList<EdgeState>()

    private val metadata: VariantGraphResolveMetadata
    private val moduleExclusions: ModuleExclusions
    val isTransitive: Boolean
    private val dependenciesMayChange: Boolean

    var previousTraversalExclusions: ExcludeSpec? = null
    private var queued = false

    /**
     * The number of unresolved capability conflicts this node is involved in.
     */
    private var numCapabilityConflicts = 0

    /**
     * The node in the same component as this node, that won against this node
     * during capability conflict resolution, if any.
     */
    /**
     * The node this node has been replaced by in a capability conflict, if this
     * node has been previously involved in a resolved capability conflict and
     * has lost that conflict.
     */
    var replacement: NodeState? = null
        private set
    private var capabilityReject: Pair<Capability?, MutableCollection<NodeState>?>? = null

    private var transitiveEdgeCount = 0
    private var upcomingNoLongerPendingConstraints: MutableSet<ModuleIdentifier>? = null

    /**
     * Virtual platforms require their constraints to be recomputed each time, as each module addition
     * can cause a shift in versions. Therefore, if this true, we perform a full dependency visit even
     * though we've already visited this node's dependencies before.
     */
    private var virtualPlatformNeedsRefresh = false
    private var edgesToRecompute: MutableSet<EdgeState>? = null
    private var potentiallyActivatedConstraints: Multimap<ModuleIdentifier, EdgeState>? = null

    // Caches the list of edges
    private var cachedEdges: MutableList<EdgeState>? = null

    // Caches the list of edges which are NOT excluded
    private var cachedFilteredEdges: MutableList<EdgeState>? = null

    // exclusions optimizations
    private var cachedNodeExclusions: ExcludeSpec? = null
    private var previousIncomingEdgeCount = 0
    private var previousIncomingHash: Long = 0
    private var incomingHash: Long = 0
    private var cachedModuleResolutionFilter: ExcludeSpec? = null

    /**
     * False if a full visit of dependencies of this node must be performed during
     * [.visitOutgoingDependenciesAndCollectEdges]. This field ensures
     * we remember whether we short-circuited a dependency visit, and therefore skipped
     * linking edge state and other state updates.
     */
    private var visitedDependencies = false

    /**
     * The transitive strict versions from inherited from parents, from the previous
     * graph traversal.
     */
    private var previousAncestorsStrictVersions: StrictVersionConstraints? = null

    /**
     * The transitive strict versions from inherited from parents.
     */
    @VisibleForTesting
    var ancestorsStrictVersions: StrictVersionConstraints = StrictVersionConstraints.EMPTY

    /**
     * Our own strict version constraints, from the previous graph traversal.
     */
    @VisibleForTesting
    var ownStrictVersions: StrictVersionConstraints? = null

    /**
     * Cached copy of all endorsed strict versions. Must be invalidated whenever
     * an outgoing endorsing edge is added or removed, or if the target endorsed
     * node's own strict versions change.
     */
    private var cachedEndorsedStrictVersions: StrictVersionConstraints? = null

    private var findingExternalVariants = false

    init {
        this.metadata = variantState.getMetadata()!!
        this.isTransitive = metadata.isTransitive() || metadata.isExternalVariant()
        this.moduleExclusions = resolveState.getModuleExclusions()
        this.dependenciesMayChange = component.getModule().isVirtualPlatform()
    }

    fun maybeResolveReplacement(): NodeState {
        var node = this
        while (node.replacement != null) {
            node = node.replacement!!
        }
        return node
    }

    // the enqueue and dequeue methods are used for performance reasons
    // in order to avoid tracking the set of enqueued nodes
    fun enqueue(): Boolean {
        if (queued) {
            return false
        }
        queued = true
        return true
    }

    fun dequeue(): NodeState {
        queued = false
        return this
    }

    override fun getComponent(): ComponentState {
        return component
    }

    override fun getNodeId(): Long {
        return nodeId
    }

    override fun getId(): VariantIdentifier {
        return variantState.getMetadata()!!.getId()!!
    }

    override fun isRoot(): Boolean {
        return false
    }

    override fun getOwner(): ComponentState {
        return component
    }

    override fun getIncomingEdges(): MutableList<EdgeState> {
        return incomingEdges
    }

    override fun getOutgoingEdges(): MutableList<EdgeState> {
        return outgoingEdges
    }

    override fun getMetadata(): VariantGraphResolveMetadata {
        return metadata
    }

    override fun getResolveState(): VariantGraphResolveState {
        return variantState
    }

    override fun getOutgoingFileEdges(): MutableSet<out LocalFileDependencyMetadata> {
        if (variantState is LocalVariantGraphResolveState) {
            return variantState.files!!
        }
        return mutableSetOf<LocalFileDependencyMetadata>()
    }

    override fun toString(): String {
        return getDisplayName()
    }

    override fun getDisplayName(): String {
        return String.format("'%s' (%s)", component.componentId.getDisplayName(), metadata.getDisplayName())
    }

    /**
     * Visits all dependencies that originate from this node, adding them as outgoing edges.
     * The [.outgoingEdges] collection is populated, as is the `discoveredEdges` parameter.
     *
     *
     * This method is incremental, and only adds edges to `discoveredEdges` that need to be
     * attached to target nodes, or that have selectors that have changed and therefore need to
     * go through selection again.
     *
     * @param discoveredEdges A collector for visited edges.
     */
    fun visitOutgoingDependenciesAndCollectEdges(discoveredEdges: MutableCollection<EdgeState>) {
        val resolutionFilter = computeModuleResolutionFilter(incomingEdges)
        val ancestorsStrictVersions = this.ancestorsStrictVersions

        doVisitDependencies(resolutionFilter, ancestorsStrictVersions, discoveredEdges)

        assert((previousTraversalExclusions == null) == (previousAncestorsStrictVersions == null))
        this.previousTraversalExclusions = resolutionFilter
        this.previousAncestorsStrictVersions = ancestorsStrictVersions
    }

    private fun doVisitDependencies(resolutionFilter: ExcludeSpec, ancestorsStrictVersions: StrictVersionConstraints, discoveredEdges: MutableCollection<EdgeState>) {
        if (transitiveEdgeCount == 0 && !isRoot() && canIgnoreExternalVariant()) {
            assert(!incomingEdges.isEmpty())

            // This node is part of the graph, but no incoming edges are transitive.
            // Act as if we have no declared dependencies. Remove any outgoing edges we may
            // have from a previous traversal. Virtual platform edges remain in order to
            // maintain version alignment (this behavior differs from non-virtual platform
            // edges, which is confusing and potentially not desired).
            removeOutgoingEdges()
            if (this.ownStrictVersions == null) {
                // Compute our own strict versions here, as we are short-circuiting
                // `visitDependencies`, which usually collects them.
                collectOwnStrictVersions(resolutionFilter)
            }
            visitOwners(resolutionFilter, ancestorsStrictVersions, discoveredEdges)
            return
        }

        // If we have visited our dependencies before, we can in some cases skip a complete visit.
        val sameExcludes = resolutionFilter == previousTraversalExclusions
        if (visitedDependencies
            && !virtualPlatformNeedsRefresh && (sameExcludes || computeFilteredEdges(resolutionFilter) == this.cachedFilteredEdges)
        ) {
            // Our excludes did not change, or after applying new excludes to our outgoing dependencies,
            // the filtered dependencies did not change. We have the same dependencies as the previous traversal.

            if (!sameExcludes) {
                // Our excludes changed. Update our outgoing edges with the new excludes.
                for (outgoingEdge in outgoingEdges) {
                    outgoingEdge.updateTransitiveExcludesAndRequeueTargetNodes(resolutionFilter)
                }
            }

            if (!ancestorsStrictVersions.equals(previousAncestorsStrictVersions)) {
                // Our strict versions changed. Update our outgoing edges with the new strict versions.
                for (outgoingEdge in outgoingEdges) {
                    outgoingEdge.recomputeSelectorAndRequeueTargetNodes(ancestorsStrictVersions, discoveredEdges)
                }
            }

            visitNewAndInvalidatedDependencies(resolutionFilter, ancestorsStrictVersions, discoveredEdges)
            return
        }

        // We are either doing a fresh visit, or we have some prior state from another visit.
        assert(!visitedDependencies || previousTraversalExclusions != null)

        // If we have any prior state, clear it before doing a full visit.
        removeOutgoingEdges()

        visitDependencies(resolutionFilter, ancestorsStrictVersions, discoveredEdges)
        visitOwners(resolutionFilter, ancestorsStrictVersions, discoveredEdges)
    }

    /**
     * Perform a partial visit of the dependencies of this node, only visiting new constraints
     * and edges that need to be recomputed.
     */
    private fun visitNewAndInvalidatedDependencies(resolutionFilter: ExcludeSpec, ancestorsStrictVersions: StrictVersionConstraints, discoveredEdges: MutableCollection<EdgeState>) {
        // Visit any constraints that were previously pending, but are no longer pending.
        if (upcomingNoLongerPendingConstraints != null && potentiallyActivatedConstraints != null) {
            for (moduleId in upcomingNoLongerPendingConstraints) {
                val edges = potentiallyActivatedConstraints!!.get(moduleId)
                if (!edges.isEmpty()) {
                    val module: ModuleResolveState = resolveState.getModule(moduleId)
                    if (module.isPending()) {
                        // The module went back to pending since we were notified that it was no longer pending.
                        module.getPendingDependencies().registerConstraintProvider(this)
                    } else {
                        for (edge in edges) {
                            doLinkOutgoingEdge(edge, discoveredEdges, resolutionFilter, ancestorsStrictVersions, module, false)
                        }
                    }
                }
            }
            upcomingNoLongerPendingConstraints = null
        }

        // Visit any other edges that were determined to need recomputation.
        if (edgesToRecompute != null) {
            discoveredEdges.addAll(edgesToRecompute!!)
            edgesToRecompute = null
        }
    }

    private fun canIgnoreExternalVariant(): Boolean {
        if (!metadata.isExternalVariant()) {
            return true
        }
        // We need to ignore external variants when all edges are artifact ones
        for (incomingEdge in incomingEdges) {
            if (!incomingEdge.isArtifactOnlyEdge()) {
                return false
            }
        }
        return true
    }

    /*
     * When a node exits the graph, its constraints need to be cleaned up.
     * This means:
     * * Rescheduling any deferred selection impacted by a constraint coming from this node
     * * Making sure we no longer are registered as pending interest on nodes pointed by constraints
     */
    private fun cleanupConstraints() {
        // This part covers constraint that were taken into account between a selection being deferred and this node being scheduled for traversal
        if (upcomingNoLongerPendingConstraints != null) {
            for (identifier in upcomingNoLongerPendingConstraints) {
                val module: ModuleResolveState = resolveState.getModule(identifier)
                for (unattachedEdge in module.getUnattachedEdges()) {
                    if (!unattachedEdge.getSelector().isResolved()) {
                        // Unresolved - we have a selector that was deferred but the constraint has been removed in between
                        val from = unattachedEdge.getFrom()
                        from.prepareToRecomputeEdge(unattachedEdge)
                    }
                }
            }
            upcomingNoLongerPendingConstraints = null
        }
        // This part covers constraint that might be triggered in the future if the node they point gains a real edge
        if (cachedFilteredEdges != null && !cachedFilteredEdges!!.isEmpty()) {
            // We may have registered this node as pending if it had constraints.
            // Let's clear that state since it is no longer part of selection
            for (edge in cachedFilteredEdges) {
                if (edge.getDependencyMetadata().isConstraint) {
                    val targetModule: ModuleResolveState = resolveState.getModule(edge.getDependencyState().getModuleIdentifier(resolveState.getComponentSelectorConverter()))
                    if (targetModule.isPending()) {
                        targetModule.unregisterConstraintProvider(this)
                    }
                }
            }
        }
    }

    fun prepareToRecomputeEdge(edgeToRecompute: EdgeState) {
        if (edgesToRecompute == null) {
            edgesToRecompute = LinkedHashSet<EdgeState>()
        }
        edgesToRecompute!!.add(edgeToRecompute)
        resolveState.onMoreSelected(this)
    }

    /**
     * Iterate over the dependencies originating in this node, adding them either as a 'pending' dependency
     * or adding them to the `discoveredEdges` collection (and `this.outgoingEdges`)
     */
    private fun visitDependencies(resolutionFilter: ExcludeSpec, ancestorsStrictVersions: StrictVersionConstraints, discoveredEdges: MutableCollection<EdgeState>) {
        this.potentiallyActivatedConstraints = null
        this.upcomingNoLongerPendingConstraints = null

        var strictVersionsSet = PersistentSet.of<ModuleIdentifier>()
        for (edge in edges(resolutionFilter)) {
            registerOutgoingEdge(resolutionFilter, ancestorsStrictVersions, discoveredEdges, edge)
            strictVersionsSet = Companion.maybeCollectStrictVersions(strictVersionsSet, edge.getDependencyMetadata().selector!!)
        }

        // If there are 'pending' dependencies that share a target with any of these outgoing edges,
        // then reset the state of the node that owns those dependencies.
        // This way, all edges of the node will be re-processed.
        storeOwnStrictVersions(strictVersionsSet)
        this.visitedDependencies = true
    }

    private fun registerOutgoingEdge(
        resolutionFilter: ExcludeSpec,
        ancestorsStrictVersions: StrictVersionConstraints,
        discoveredEdges: MutableCollection<EdgeState>,
        dependencyEdge: EdgeState
    ) {
        val constraint = dependencyEdge.getDependencyMetadata().isConstraint
        val moduleId = dependencyEdge.getDependencyState().getModuleIdentifier(resolveState.getComponentSelectorConverter())
        val module: ModuleResolveState = resolveState.getModule(moduleId)

        var deferSelection = false
        if (constraint) {
            registerActivatingConstraint(dependencyEdge, moduleId)
        } else {
            deferSelection = module.getPendingDependencies().addIncomingHardEdge()
        }

        if (constraint && module.isPending()) {
            // No hard dependency targeting this module. Remember this constraint for later in case we see a hard dependency later.
            module.registerConstraintProvider(this)
        } else {
            // We are a hard edge, or we are a constraint but there is already another hard edge targeting the same module.
            doLinkOutgoingEdge(dependencyEdge, discoveredEdges, resolutionFilter, ancestorsStrictVersions, module, deferSelection)
        }
    }

    private fun registerActivatingConstraint(edge: EdgeState, targetModuleId: ModuleIdentifier) {
        if (potentiallyActivatedConstraints == null) {
            potentiallyActivatedConstraints = LinkedHashMultimap.create<ModuleIdentifier, EdgeState>()
        }
        potentiallyActivatedConstraints!!.put(targetModuleId, edge)
    }

    private fun edges(): MutableList<EdgeState> {
        if (dependenciesMayChange || cachedEdges == null) {
            var dependencies = this.allDependencies
            if (transitiveEdgeCount == 0 && metadata.isExternalVariant()) {
                // there must be a single dependency state because this variant is an "available-at"
                // variant and here we are in the case the "including" component said that transitive
                // should be false so we need to arbitrarily carry that onto the dependency metadata
                assert(dependencies.size == 1)
                dependencies = mutableListOf<DependencyMetadata>(makeNonTransitive(dependencies.get(0)))
            }
            this.cachedEdges = cacheEdges(dependencies)
        }
        return cachedEdges!!
    }

    protected open val allDependencies: MutableList<out DependencyMetadata>
        get() = variantState.getDependencies()

    private fun edges(spec: ExcludeSpec): MutableList<EdgeState> {
        if (dependenciesMayChange || cachedFilteredEdges == null) {
            this.cachedFilteredEdges = computeFilteredEdges(spec)
        }
        return cachedFilteredEdges!!
    }

    /**
     * Apply the given excludes to the list of edges, filtering out any edges
     * that are excluded.
     */
    private fun computeFilteredEdges(spec: ExcludeSpec): MutableList<EdgeState> {
        val from = edges()
        if (from.isEmpty()) {
            return from
        }
        val tmp: MutableList<EdgeState> = ArrayList<EdgeState>(from.size)
        for (edge in from) {
            if (!isExcluded(spec, edge)) {
                tmp.add(edge)
            }
        }
        return tmp
    }

    private fun cacheEdges(dependencies: MutableList<out DependencyMetadata>): MutableList<EdgeState> {
        if (dependencies.isEmpty()) {
            return mutableListOf<EdgeState>()
        }

        val result: MutableList<EdgeState> = ArrayList<EdgeState>(dependencies.size)
        for (dependency in dependencies) {
            result.add(createEdge(dependency))
        }
        return result
    }

    private fun createEdge(dependency: DependencyMetadata): EdgeState {
        val trySubstitution: Try<SubstitutionResult?> = resolveState.getDependencySubstitutionApplicator().applySubstitutions(
            dependency.selector!!,
            dependency.artifacts!!
        )

        if (!trySubstitution.isSuccessful) {
            // Substitution failed
            val resolveFailure = ModuleVersionResolveException(dependency.selector!!, trySubstitution.failure!!.get())
            return EdgeState(this, dependency, dependency.selector!!, ImmutableList.of<ComponentSelectionDescriptorInternal>(), resolveFailure, resolveState)
        }

        // We performed substitution
        val substitution = trySubstitution.get()
        val updatedMetadata: DependencyMetadata = Companion.metadataWithSubstitution(dependency, substitution!!)
        return EdgeState(this, updatedMetadata, dependency.selector!!, substitution.ruleDescriptors, null, resolveState)
    }

    private fun doLinkOutgoingEdge(
        dependencyEdge: EdgeState,
        discoveredEdges: MutableCollection<EdgeState>,
        resolutionFilter: ExcludeSpec,
        ancestorsStrictVersions: StrictVersionConstraints,
        module: ModuleResolveState,
        deferSelection: Boolean
    ) {
        dependencyEdge.updateTransitiveExcludes(resolutionFilter)
        dependencyEdge.computeSelector(ancestorsStrictVersions, deferSelection)
        module.addUnattachedEdge(dependencyEdge)
        discoveredEdges.add(dependencyEdge)
        outgoingEdges.add(dependencyEdge)
    }

    /**
     * If a component declares that it belongs to a platform, we add an edge to the platform.
     * Whether the platform is real or virtual is determined later during component resolution.
     *
     * @param resolutionFilter The excludes inherited from all incoming edges
     * @param ancestorsStrictVersions The strict versions inherited from all incoming edges
     * @param discoveredEdges the collection of edges for this component
     */
    private fun visitOwners(resolutionFilter: ExcludeSpec, ancestorsStrictVersions: StrictVersionConstraints, discoveredEdges: MutableCollection<EdgeState>) {
        val owners = component.getMetadata().getPlatformOwners()
        if (!owners!!.isEmpty()) {
            for (owner in owners) {
                if (owner is ModuleComponentIdentifier) {
                    // Register this module as a participant of the owning virtual platform.
                    // If the platform turns out to be real (published), this is harmless.
                    // If the platform is virtual, this enables the platform component to
                    // be resolved as a virtual platform later during metadata resolution.
                    val platformModule: ModuleResolveState = resolveState.getModule(owner.getModuleIdentifier())
                    platformModule.getPlatformState().participatingModule(component.getModule())

                    val forced = hasStrongOpinion()
                    val selector = newSelector(owner.getModuleIdentifier(), owner.getVersion())
                    val dependencyMetadata: DependencyMetadata = LenientPlatformDependencyMetadata(selector, owner, forced, false)
                    val virtualPlatformEdge = createEdge(dependencyMetadata)

                    registerOutgoingEdge(
                        resolutionFilter,
                        ancestorsStrictVersions,
                        discoveredEdges,
                        virtualPlatformEdge
                    )
                } else {
                    throw IllegalStateException("Expected platform ID to be a module identifier: " + owner)
                }
            }
        }
    }

    private fun hasStrongOpinion(): Boolean {
        for (edgeState in incomingEdges) {
            if (edgeState.getSelector().hasStrongOpinion()) {
                return true
            }
        }
        return false
    }

    private fun isExcluded(excludeSpec: ExcludeSpec, edgeState: EdgeState): Boolean {
        val dependency = edgeState.getDependencyMetadata()
        if (!resolveState.getEdgeFilter().isSatisfiedBy(dependency)) {
            LOGGER.debug("{} is filtered.", dependency)
            return true
        }
        if (excludeSpec === moduleExclusions.nothing()) {
            return false
        }

        val componentSelectorConverter: ComponentSelectorConverter = resolveState.getComponentSelectorConverter()
        val targetModuleId = edgeState.getDependencyState().getModuleIdentifier(componentSelectorConverter)

        if (excludeSpec.excludes(targetModuleId)) {
            LOGGER.debug("{} is excluded from {} by {}.", targetModuleId, this, excludeSpec)
            return true
        }

        // If we were substituted, apply the exclusion to the original selector as well.
        val requestedSelector = edgeState.getDependencyState().getRequested()
        if (requestedSelector !== edgeState.getDependencyState().getDependency().selector) {
            return excludeSpec.excludes(componentSelectorConverter.getModuleVersionId(requestedSelector)!!.getModule())
        }

        return false
    }

    open fun addIncomingEdge(dependencyEdge: EdgeState) {
        if (!incomingEdges.contains(dependencyEdge)) {
            cachedModuleResolutionFilter = null
            incomingEdges.add(dependencyEdge)
            incomingHash += dependencyEdge.hashCode().toLong()
            if (dependencyEdge.isTransitive()) {
                transitiveEdgeCount++
            }
            requeueChildrenOfEndorsingParent(dependencyEdge)

            if (incomingEdges.size == 1) {
                updateAncestorsStrictVersions(getStrictVersionsForEdge(dependencyEdge))
            } else {
                updateAncestorsStrictVersions(ancestorsStrictVersions.intersect(getStrictVersionsForEdge(dependencyEdge)))
            }

            resolveState.onMoreSelected(this)
        }
    }

    fun removeIncomingEdge(dependencyEdge: EdgeState) {
        if (incomingEdges.remove(dependencyEdge)) {
            cachedModuleResolutionFilter = null
            incomingHash -= dependencyEdge.hashCode().toLong()
            if (dependencyEdge.isTransitive()) {
                transitiveEdgeCount--
            }
            requeueChildrenOfEndorsingParent(dependencyEdge)
            recomputeAncestorsStrictVersions()
            resolveState.onFewerSelected(this)
        }
    }

    /**
     * Removes all incoming edges targeting this node. This is faster than individually
     * calling [.removeIncomingEdge] for each incoming edge.
     *
     * @return All removed incoming edges.
     */
    fun removeAllIncomingEdges(): MutableList<EdgeState> {
        if (incomingEdges.isEmpty()) {
            return mutableListOf<EdgeState>()
        }

        val removedEdges: MutableList<EdgeState> = ImmutableList.copyOf<EdgeState>(incomingEdges)
        incomingEdges.clear()
        cachedModuleResolutionFilter = null
        incomingHash = 0
        transitiveEdgeCount = 0

        for (incomingEdge in removedEdges) {
            requeueChildrenOfEndorsingParent(incomingEdge)
        }
        updateAncestorsStrictVersions(StrictVersionConstraints.EMPTY)
        resolveState.onFewerSelected(this)

        return removedEdges
    }

    /**
     * Whenever an incoming edge is added or removed from this node, if that edge is
     * endorsing strict versions and this node has strict versions declared, other children
     * of the source node need to be re-processed in order to ensure they handle the updated
     * endorsed strict versions from their parent.
     */
    private fun requeueChildrenOfEndorsingParent(incomingEdge: EdgeState) {
        if (incomingEdge.getDependencyMetadata().isEndorsingStrictVersions) {
            val sourceNode = incomingEdge.getFrom()
            sourceNode.invalidateEndorsedStrictVersions()
            for (edge in sourceNode.getOutgoingEdges()) {
                for (node in edge.getTargetNodes()) {
                    if (node !== this) {
                        resolveState.onMoreSelected(node)
                    }
                }
            }
        }
    }

    fun clearTransitiveExclusionsAndEnqueue() {
        cachedModuleResolutionFilter = null
        // TODO: We can eagerly compute the exclusions and enqueue only on change
        resolveState.onMoreSelected(this)
    }

    /**
     * Determine if this node should be processed when it is dequeued during traversal, or if its
     * subgraph should be removed from the graph.
     *
     *
     * True if this node has incoming edges and is not in a conflict. We temporarily delay building
     * subgraphs of nodes in conflict while the conflict has not yet been resolved to avoid subgraphs
     * of losing nodes from affecting the graph shape.
     */
    fun shouldBuildSubgraph(): Boolean {
        return isSelected() && !this.isInCapabilityConflict &&  // We need special handling for root since it does not yet have
                // its own module, but should never be considered a conflict participant.
                !(getComponent().getModule().isInModuleConflict() && !isRoot())
    }

    override fun isSelected(): Boolean {
        return !incomingEdges.isEmpty()
    }

    fun markInCapabilityConflict() {
        this.numCapabilityConflicts++
        if (numCapabilityConflicts == 1) {
            resolveState.onFewerSelected(this)
        }
    }

    val isInCapabilityConflict: Boolean
        get() = numCapabilityConflicts > 0

    fun onFilteredFromConflict() {
        numCapabilityConflicts--
        assert(numCapabilityConflicts >= 0)
    }

    /**
     * Resolve a capability conflict this node is involved in.
     */
    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    fun resolveCapabilityConflict(winner: NodeState) {
        numCapabilityConflicts--
        assert(numCapabilityConflicts >= 0)
        if (winner === this) {
            resolveState.onMoreSelected(this)
        } else {
            this.replacement = winner
            restartIncomingEdges()
        }
    }

    val isRejectedForCapabilityConflict: Boolean
        get() = capabilityReject != null

    fun rejectForCapabilityConflict(capability: Capability, conflictedNodes: MutableCollection<NodeState>) {
        if (this.capabilityReject == null) {
            this.capabilityReject = Pair.of(capability, HashSet<E?>(conflictedNodes))
        } else {
            mergeCapabilityRejects(capability, conflictedNodes)
        }

        numCapabilityConflicts--
        assert(numCapabilityConflicts >= 0)
        resolveState.onMoreSelected(this)
    }

    private fun mergeCapabilityRejects(capability: Capability, conflictedNodes: MutableCollection<NodeState>) {
        // Only merge if about the same capability, otherwise last wins
        if (this.capabilityReject!!.left == capability) {
            this.capabilityReject!!.right!!.addAll(conflictedNodes)
        } else {
            this.capabilityReject = Pair.of(capability, HashSet<E?>(conflictedNodes))
        }
    }

    val rejectedErrorMessage: String
        get() {
            checkNotNull(capabilityReject)
            return Companion.formatCapabilityRejectMessage(getComponent().getModule().getId(), capabilityReject!!)
        }

    fun shouldIncludedInGraphResult(): Boolean {
        return isSelected() && !component.getModule().isVirtualPlatform()
    }

    private fun computeModuleResolutionFilter(incomingEdges: MutableList<EdgeState>): ExcludeSpec {
        if (metadata.isExternalVariant()) {
            // If the current node represents an external variant, we must not consider its excludes
            // because it's some form of "delegation"
            return moduleExclusions.excludeAny(
                incomingEdges.stream()
                    .map<ExcludeSpec?> { obj: EdgeState? -> obj!!.getTransitiveExclusions() }
                    .filter { obj: ExcludeSpec? -> Objects.nonNull(obj) }
                    .collect(PersistentSet.toPersistentSet<ExcludeSpec>())
            )
        }
        if (incomingEdges.size == 1) {
            // At the same time if the current node _comes from_ a delegated variant (available-at)
            // then we need to take the exclusion filter from the origin node instead
            val from = incomingEdges.get(0).getFrom()
            if (from.getMetadata().isExternalVariant()) {
                return computeModuleResolutionFilter(from.getIncomingEdges())
            }
        }
        val nodeExclusions = computeNodeExclusions()
        if (incomingEdges.isEmpty()) {
            return nodeExclusions
        }

        return computeExclusionFilter(incomingEdges, nodeExclusions)
    }

    private fun computeNodeExclusions(): ExcludeSpec {
        if (cachedNodeExclusions == null) {
            cachedNodeExclusions = moduleExclusions.excludeAny(variantState.getExcludes())
        }
        return cachedNodeExclusions!!
    }

    private fun computeExclusionFilter(incomingEdges: MutableList<EdgeState>, nodeExclusions: ExcludeSpec): ExcludeSpec {
        val incomingEdgeCount = incomingEdges.size
        if (sameIncomingEdgesAsPreviousPass(incomingEdgeCount)) {
            // if we reach this point it means the node selection was restarted, but
            // effectively it has the same incoming edges as before, so we can return
            // the result we computed last time
            return cachedModuleResolutionFilter!!
        }
        if (incomingEdgeCount == 1) {
            return computeExclusionFilterSingleIncomingEdge(incomingEdges.get(0), nodeExclusions)
        }
        return computeModuleExclusionsManyEdges(incomingEdges, nodeExclusions, incomingEdgeCount)
    }

    private fun computeModuleExclusionsManyEdges(incomingEdges: MutableList<EdgeState>, nodeExclusions: ExcludeSpec, incomingEdgeCount: Int): ExcludeSpec {
        var nodeExclusions = nodeExclusions
        val nothing = moduleExclusions.nothing()
        var edgeExclusions: ExcludeSpec? = null
        var excludedByBoth = PersistentSet.of<ExcludeSpec>()
        var excludedByEither = PersistentSet.of<ExcludeSpec>()
        for (dependencyEdge in incomingEdges) {
            if (dependencyEdge.isTransitive()) {
                if (edgeExclusions !== nothing) {
                    // Transitive dependency
                    val exclusions = dependencyEdge.getExclusions()
                    if (edgeExclusions == null || exclusions === nothing) {
                        edgeExclusions = exclusions
                    } else if (edgeExclusions !== exclusions) {
                        excludedByBoth = excludedByBoth.plus(exclusions)
                    }
                    if (edgeExclusions === nothing) {
                        // if exclusions == nothing, then the intersection will be "nothing"
                        excludedByBoth = PersistentSet.of<ExcludeSpec>()
                    }
                }
            } else if (dependencyEdge.isConstraint()) {
                excludedByEither = collectEdgeConstraint(nodeExclusions, excludedByEither, dependencyEdge, nothing)
            }
        }
        edgeExclusions = intersectEdgeExclusions(edgeExclusions, excludedByBoth)
        nodeExclusions = joinNodeExclusions(nodeExclusions, excludedByEither)!!
        return joinEdgeAndNodeExclusionsThenCacheResult(nodeExclusions, edgeExclusions!!, incomingEdgeCount)
    }

    private fun computeExclusionFilterSingleIncomingEdge(dependencyEdge: EdgeState, nodeExclusions: ExcludeSpec): ExcludeSpec {
        var exclusions: ExcludeSpec? = null
        if (dependencyEdge.isTransitive()) {
            exclusions = dependencyEdge.getExclusions()
        } else if (dependencyEdge.isConstraint()) {
            exclusions = dependencyEdge.getEdgeExclusions()
        }
        if (exclusions == null) {
            exclusions = moduleExclusions.nothing()
        }
        return joinEdgeAndNodeExclusionsThenCacheResult(nodeExclusions, exclusions, 1)
    }

    private fun joinEdgeAndNodeExclusionsThenCacheResult(nodeExclusions: ExcludeSpec, edgeExclusions: ExcludeSpec, incomingEdgeCount: Int): ExcludeSpec {
        val result = moduleExclusions.excludeAny(edgeExclusions, nodeExclusions)
        // We use a set here because for excludes, order of edges is irrelevant
        // so we hit the cache more by using a set
        previousIncomingEdgeCount = incomingEdgeCount
        previousIncomingHash = incomingHash
        cachedModuleResolutionFilter = result
        return result
    }

    private fun joinNodeExclusions(nodeExclusions: ExcludeSpec?, excludedByEither: PersistentSet<ExcludeSpec>): ExcludeSpec? {
        if (excludedByEither.isNotEmpty() && nodeExclusions != null) {
            return moduleExclusions.excludeAny(
                excludedByEither.plus(nodeExclusions)
            )
        }
        return nodeExclusions
    }

    private fun intersectEdgeExclusions(edgeExclusions: ExcludeSpec?, excludedByBoth: PersistentSet<ExcludeSpec>): ExcludeSpec? {
        if (edgeExclusions === moduleExclusions.nothing()) {
            return edgeExclusions
        }
        if (excludedByBoth.isNotEmpty()) {
            return moduleExclusions.excludeAll(
                if (edgeExclusions != null)
                    excludedByBoth.plus(edgeExclusions)
                else
                    excludedByBoth
            )
        }
        return edgeExclusions
    }

    @VisibleForTesting
    fun collectOwnStrictVersions(moduleResolutionFilter: ExcludeSpec) {
        val edges = edges(moduleResolutionFilter)
        var constraintsSet = PersistentSet.of<ModuleIdentifier>()
        for (edge in edges) {
            constraintsSet = Companion.maybeCollectStrictVersions(constraintsSet, edge.getDependencyMetadata().selector!!)
        }
        storeOwnStrictVersions(constraintsSet)
    }

    private fun storeOwnStrictVersions(constraintsSet: PersistentSet<ModuleIdentifier>) {
        val newStrictVersions = StrictVersionConstraints.of(constraintsSet)

        val existingOwnStrictVersions = this.ownStrictVersions
        this.ownStrictVersions = newStrictVersions

        if (existingOwnStrictVersions == null) {
            // If our existing strict versions are null, nobody else has observed them,
            // so their value being initialized for the first time will no invalidate
            // any existing calculated strict versions.
            return
        }

        if (!newStrictVersions.equals(existingOwnStrictVersions)) {
            for (incomingEdge in incomingEdges) {
                if (incomingEdge.getDependencyMetadata().isEndorsingStrictVersions) {
                    // Our own strict versions contribute to the endorsed strict versions of
                    // ancestors that endorse us.
                    incomingEdge.getFrom().invalidateEndorsedStrictVersions()
                    // Our own strict versions contribute to our ancestors strict versions
                    // if our ancestor endorses us.
                    recomputeAncestorsStrictVersions()
                }
            }
            for (outgoingEdge in outgoingEdges) {
                for (targetNode in outgoingEdge.getTargetNodes()) {
                    // Our own strict versions contribute to our descendants strict versions.
                    targetNode.recomputeAncestorsStrictVersions()
                }
            }
        }
    }

    /**
     * Recompute the strict versions inherited from ancestors,
     * propagating the new value to all descendants.
     */
    @VisibleForTesting
    fun recomputeAncestorsStrictVersions() {
        updateAncestorsStrictVersions(collectAncestorsStrictVersions())
    }

    /**
     * Set the strict versions inherited from ancestors,
     * propagating the new value to all descendants.
     */
    private fun updateAncestorsStrictVersions(newAncestorsStrictVersions: StrictVersionConstraints) {
        if (newAncestorsStrictVersions.equals(this.ancestorsStrictVersions)) {
            // No change, no need to propagate further.
            return
        }

        this.ancestorsStrictVersions = newAncestorsStrictVersions

        for (outgoingEdge in outgoingEdges) {
            for (targetNode in outgoingEdge.getTargetNodes()) {
                // The ancestors strict versions of this node contribute to the
                // ancestors strict versions of our children.
                targetNode.recomputeAncestorsStrictVersions()
            }
        }
    }

    /**
     * Determines all strict versions inherited from ancestors. When a node declares strict
     * versions, either through its own dependencies, or by endorsement, those strict versions apply
     * to all descendants of that node's exclusive subgraph. If a given node belongs to multiple
     * subgraphs, a strict version is only inherited if all parent subgraphs provide a
     * strict version for that module. For this reason, we compute the intersection of strict
     * versions coming from all incoming edges.
     */
    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private fun collectAncestorsStrictVersions(): StrictVersionConstraints {
        if (incomingEdges.isEmpty()) {
            return StrictVersionConstraints.EMPTY
        }

        if (incomingEdges.size == 1) {
            val dependencyEdge = incomingEdges.get(0)
            if (dependencyEdge.getFrom().isSelected()) {
                return getStrictVersionsForEdge(dependencyEdge)
            } else {
                return StrictVersionConstraints.EMPTY
            }
        }

        var ancestorsStrictVersions: StrictVersionConstraints? = null
        for (dependencyEdge in incomingEdges) {
            if (!dependencyEdge.getFrom().isSelected()) {
                continue
            }
            val allEdgeStrictVersions = getStrictVersionsForEdge(dependencyEdge)

            ancestorsStrictVersions = if (ancestorsStrictVersions == null)
                allEdgeStrictVersions
            else
                ancestorsStrictVersions.intersect(allEdgeStrictVersions)

            if (ancestorsStrictVersions === StrictVersionConstraints.EMPTY) {
                // No need to continue. Empty intersected with anything is empty.
                break
            }
        }
        return if (ancestorsStrictVersions != null) ancestorsStrictVersions else StrictVersionConstraints.EMPTY
    }

    /**
     * Determine the strict versions inherited through a given edge.
     */
    private fun getStrictVersionsForEdge(dependencyEdge: EdgeState): StrictVersionConstraints {
        val from = dependencyEdge.getFrom()
        val parentStrongStrictVersions = from.strongStrictVersions
        val parentEndorsedStrictVersions = from.endorsedStrictVersions

        // If the source node endorses us, then we might be the source of a strict version that it
        // endorses. For this reason, we inherit a parent's endorsed strict versions only if we may
        // not be the source of that strict version.
        val filteredEndorsedStrictVersions: StrictVersionConstraints?
        if (dependencyEdge.getDependencyMetadata().isEndorsingStrictVersions) {
            filteredEndorsedStrictVersions = parentEndorsedStrictVersions.minus(ownStrictVersions!!)
        } else {
            filteredEndorsedStrictVersions = parentEndorsedStrictVersions
        }

        return parentStrongStrictVersions.union(filteredEndorsedStrictVersions)
    }

    private val strongStrictVersions: StrictVersionConstraints
        /**
         * Get the strong strict versions of this node -- the strict versions that are sourced from higher up
         * in the graph. These strong strict versions take precedence over endorsed strict versions.
         */
        get() {
            // This method assumes that `ownStrictVersions` has already been
            // computed for the source node. If `ownStrictVersions` ever changes,
            // we must ensure this node is re-processed.
            checkNotNull(ownStrictVersions)
            return ownStrictVersions!!.union(ancestorsStrictVersions)
        }

    /**
     * Invalidate the cached strict versions endorsed by this node,
     * propagating the invalidation to all descendants.
     */
    private fun invalidateEndorsedStrictVersions() {
        this.cachedEndorsedStrictVersions = null

        for (outgoingEdge in outgoingEdges) {
            for (targetNode in outgoingEdge.getTargetNodes()) {
                // The endorsed strict versions of this node contributes to the
                // ancestors strict versions of our children.
                targetNode.recomputeAncestorsStrictVersions()
            }
        }
    }

    private val endorsedStrictVersions: StrictVersionConstraints
        /**
         * Get the strict versions endorsed by this node, calculating the value if necessary.
         */
        get() {
            if (cachedEndorsedStrictVersions == null) {
                this.cachedEndorsedStrictVersions = computeEndorsedStrictVersions()
            }
            return this.cachedEndorsedStrictVersions
        }

    /**
     * Determine all strict versions endorsed by this node.
     */
    private fun computeEndorsedStrictVersions(): StrictVersionConstraints {
        var endorsedStrictVersions = StrictVersionConstraints.EMPTY
        for (edgeState in outgoingEdges) {
            if (edgeState.getDependencyMetadata().isEndorsingStrictVersions) {
                for (endorsedNode in edgeState.getTargetNodes()) {
                    if (endorsedNode.ownStrictVersions == null) {
                        // The node's dependencies were not yet visited. Compute them now.
                        endorsedNode.collectOwnStrictVersions(endorsedNode.computeModuleResolutionFilter(endorsedNode.incomingEdges))
                    }
                    endorsedStrictVersions = endorsedStrictVersions.union(endorsedNode.ownStrictVersions!!)
                }
            }
        }
        return endorsedStrictVersions
    }

    private fun sameIncomingEdgesAsPreviousPass(incomingEdgeCount: Int): Boolean {
        // This is a heuristic, more than truth: it is possible that the 2 long hashs
        // are identical AND that the sizes of collections are identical, but it's
        // extremely unlikely (never happened on test cases even on large dependency graph)
        return cachedModuleResolutionFilter != null && previousIncomingHash == incomingHash && previousIncomingEdgeCount == incomingEdgeCount
    }

    val isDisconnected: Boolean
        /**
         * Returns true if [.visitOutgoingDependenciesAndCollectEdges]
         * has never been called, or if it has been called but [.removeOutgoingEdges]
         * has been called since then.
         *
         *
         * If this returns true, this node has no outgoing edges in the graph, and therefore does
         * not affect the rest of the graph.
         */
        get() = previousTraversalExclusions == null && !visitedDependencies

    /**
     * This method is effectively the inverse of [.visitOutgoingDependenciesAndCollectEdges].
     *
     *
     * Cleans up the outgoing state of this node, undoing any effects this node has on the graph.
     * To be called when this node is removed from the graph.
     */
    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    fun removeOutgoingEdges() {
        if (previousTraversalExclusions == null) {
            return
        }

        if (!outgoingEdges.isEmpty()) {
            for (outgoingEdge in outgoingEdges) {
                disconnectOutgoingEdge(outgoingEdge)
            }
            outgoingEdges.clear()
        }
        cleanupConstraints()
        previousTraversalExclusions = null
        previousAncestorsStrictVersions = null
        visitedDependencies = false
        cachedFilteredEdges = null
        edgesToRecompute = null
        virtualPlatformNeedsRefresh = false
    }

    private fun disconnectOutgoingEdge(outgoingEdge: EdgeState) {
        outgoingEdge.detachFromTargetNodes()
        outgoingEdge.getSelector().getTargetModule().disconnectIncomingEdge(this, outgoingEdge)
    }

    /**
     * Retarget all incoming edges of this node. Called in two contexts:
     *
     *  * On losing nodes of capability conflicts, to move their edges to the winner.
     *  * On nodes (and their replacements) of components losing version or module conflicts
     * in [ModuleResolveState.changeSelection], to retarget edges to the new selection.
     *
     * In the second case, the node may be a capability conflict replacement that has its own
     * legitimate incoming edges from other modules. Those edges re-attach to this node after
     * retargeting, so the node may still have incoming edges when this method returns.
     */
    fun restartIncomingEdges() {
        if (incomingEdges.size == 1) {
            val singleEdge = incomingEdges.get(0)
            singleEdge.retarget()
            // The edge should have retargeted away from this node, unless it targets
            // this node's own module and re-attached (replacement during version change).
            assert(!singleEdge.getTargetNodes().contains(this) || singleEdge.getSelector().getTargetModule() === getComponent().getModule())
        } else if (incomingEdges.size > 1) {
            for (edge in ArrayList<EdgeState?>(incomingEdges)) {
                edge!!.retarget()
                assert(!edge.getTargetNodes().contains(this) || edge.getSelector().getTargetModule() === getComponent().getModule())
            }
        }
    }

    fun prepareForConstraintNoLongerPending(moduleIdentifier: ModuleIdentifier) {
        if (upcomingNoLongerPendingConstraints == null) {
            upcomingNoLongerPendingConstraints = LinkedHashSet<ModuleIdentifier>()
        }
        upcomingNoLongerPendingConstraints!!.add(moduleIdentifier)
        // Trigger a replay on this node, to add new constraints to graph
        resolveState.onFewerSelected(this)
    }

    fun markForVirtualPlatformRefresh() {
        assert(component.getModule().isVirtualPlatform())
        virtualPlatformNeedsRefresh = true
        resolveState.onFewerSelected(this)
    }

    fun removeOutgoingEdge(edge: EdgeState) {
        outgoingEdges.remove(edge)
        edge.clearSelector()
    }

    /**
     * Determine if this node provides a capability with the given group and name.
     * If so, return it. Otherwise, return null.
     */
    fun findCapability(group: String, name: String): ImmutableCapability? {
        val capabilities = metadata.getCapabilities()
        if (capabilities!!.isEmpty) {
            // No capabilities declared. Use the component's implicit capability.
            if (component.getId().getGroup() == group && component.getId().getName() == name) {
                return component.getImplicitCapability()
            }
        } else {
            for (capability in capabilities) {
                if (capability!!.getGroup() == group && capability.getName() == name) {
                    return capability
                }
            }
        }
        return null
    }

    val isAttachedToVirtualPlatform: Boolean
        get() {
            for (incomingEdge in incomingEdges) {
                if (incomingEdge.getDependencyMetadata() is LenientPlatformDependencyMetadata) {
                    return true
                }
            }
            return false
        }

    fun hasShadowedCapability(): Boolean {
        for (capability in metadata.getCapabilities()!!.asSet()) {
            if (capability is ShadowedCapability) {
                return true
            }
        }
        return false
    }

    fun isSelectedByVariantAwareResolution(): Boolean {
        // the order is strange logically but here for performance optimization
        return selectedByVariantAwareResolution && isSelected()
    }

    override fun getExternalVariant(): ResolvedGraphVariant? {
        if (canIgnoreExternalVariant()) {
            return null
        }
        if (findingExternalVariants) {
            // There is a cycle in the external variants
            LOGGER.warn("Detecting cycle in external variants for :\n" + computePathToRoot())
            findingExternalVariants = false
            return null
        }
        findingExternalVariants = true
        // An external variant must have exactly one outgoing edge
        // corresponding to the dependency to the external module
        // can be 0 if the selected variant also happens to be excluded
        // for example via configuration excludes
        assert(outgoingEdges.size <= 1)
        try {
            for (outgoingEdge in outgoingEdges) {
                return outgoingEdge.getFirstTargetNode()
            }
            return null
        } finally {
            findingExternalVariants = false
        }
    }

    private fun computePathToRoot(): String {
        val formatter = TreeFormatter()
        formatter.node(getDisplayName())
        var from: NodeState? = this
        var depth = 0
        do {
            from = getFromNode(from!!)
            if (from != null) {
                formatter.startChildren()
                formatter.node(getDisplayName())
                depth++
            }
        } while (from != null && from !is RootNode)
        for (i in 0..<depth) {
            formatter.endChildren()
        }
        formatter.node("Dependency resolution has ignored the cycle to produce a result. It is recommended to resolve the cycle by upgrading one or more dependencies.")
        return formatter.toString()
    }

    private fun getFromNode(from: NodeState): NodeState? {
        val incomingEdges = from.getIncomingEdges()
        if (incomingEdges.isEmpty()) {
            return null
        }
        return incomingEdges.get(0).getFrom()
    }

    val reachableNodes: MutableSet<NodeState>
        get() {
            val visited: MutableSet<NodeState> =
                HashSet<NodeState>()
            dependsTransitivelyOn(visited)
            return visited
        }

    private fun dependsTransitivelyOn(visited: MutableSet<NodeState>) {
        for (outgoingEdge in getOutgoingEdges()) {
            for (nodeState in outgoingEdge.getTargetNodes()) {
                if (visited.add(nodeState)) {
                    nodeState.dependsTransitivelyOn(visited)
                }
            }
        }
    }

    private class NonTransitiveVariantDependencyMetadata(private val dependencyMetadata: DependencyMetadata) : DelegatingDependencyMetadata(
        dependencyMetadata
    ) {
        public override fun withTarget(target: ComponentSelector): DependencyMetadata {
            return Companion.makeNonTransitive(dependencyMetadata.withTarget(target)!!)
        }

        public override fun withTargetAndArtifacts(target: ComponentSelector, artifacts: ImmutableList<IvyArtifactName>): DependencyMetadata {
            return Companion.makeNonTransitive(dependencyMetadata.withTargetAndArtifacts(target, artifacts)!!)
        }

        public override fun isTransitive(): Boolean {
            return false
        }

        public override fun withReason(reason: String): DependencyMetadata {
            return Companion.makeNonTransitive(dependencyMetadata.withReason(reason)!!)
        }

        override fun toString(): String {
            return "Non transitive dependency for external variant " + dependencyMetadata
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(NodeState::class.java)
        private fun makeNonTransitive(dependencyMetadata: DependencyMetadata): DependencyMetadata {
            return NonTransitiveVariantDependencyMetadata(dependencyMetadata)
        }

        private fun metadataWithSubstitution(dependency: DependencyMetadata, substitution: SubstitutionResult): DependencyMetadata {
            val target = substitution.target
            val artifacts = substitution.artifacts
            if (target == null && artifacts == null) {
                return dependency
            }

            val actualTarget: ComponentSelector = (if (target != null) target else dependency.selector)!!
            return (if (artifacts == null)
                dependency.withTarget(actualTarget)
            else
                dependency.withTargetAndArtifacts(actualTarget, artifacts))!!
        }

        private fun formatCapabilityRejectMessage(id: ModuleIdentifier, capabilityConflict: Pair<Capability?, MutableCollection<NodeState>?>): String {
            return "Module '" + id + "' has been rejected:\n" +
                    "   Cannot select module with conflict on capability '" + Companion.formatCapability(capabilityConflict.left!!) + "' also provided by " +
                    capabilityConflict.right!!.stream().map<String> { obj: NodeState? -> obj!!.getDisplayName() }.sorted().collect(Collectors.toList())
        }

        private fun formatCapability(capability: Capability): String {
            return capability.getGroup() + ":" + capability.getName() + ":" + capability.getVersion()
        }

        private fun collectEdgeConstraint(nodeExclusions: ExcludeSpec, excludedByEither: PersistentSet<ExcludeSpec>, dependencyEdge: EdgeState, nothing: ExcludeSpec): PersistentSet<ExcludeSpec> {
            // Constraint: only consider explicit exclusions declared for this constraint
            val constraintExclusions = dependencyEdge.getEdgeExclusions()
            if (constraintExclusions !== nothing && constraintExclusions !== nodeExclusions) {
                return excludedByEither.plus(constraintExclusions)
            }
            return excludedByEither
        }

        private fun maybeCollectStrictVersions(constraintsSet: PersistentSet<ModuleIdentifier>, selector: ComponentSelector): PersistentSet<ModuleIdentifier> {
            var constraintsSet = constraintsSet
            if (selector is ModuleComponentSelector) {
                val mcs = selector
                if (!StringUtils.isEmpty(mcs.getVersionConstraint().getStrictVersion())) {
                    constraintsSet = constraintsSet.plus(mcs.getModuleIdentifier())
                }
            }
            return constraintsSet
        }
    }
}
