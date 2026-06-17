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

import com.google.common.collect.Sets
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.configurations.ConflictResolution
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.CandidateModule
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.SelectorStateResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints.equals
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributeMergingException
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.model.ComponentGraphResolveMetadata.getId
import org.gradle.internal.component.model.ComponentGraphResolveState.getId
import org.gradle.internal.component.model.ForcingDependencyMetadata
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getId
import org.gradle.internal.deprecation.DeprecationLogger.deprecateAction
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.LinkedList
import java.util.function.Consumer

/**
 * Resolution state for a given module.
 */
class ModuleResolveState internal constructor(
    val resolveState: ResolveState,
    private val id: ModuleIdentifier,
    private val metaDataResolver: ComponentMetaDataResolver,
    private val attributesFactory: AttributesFactory,
    private val versionComparator: Comparator<Version>,
    private val versionParser: VersionParser,
    private var selectorStateResolver: SelectorStateResolver<ComponentState>,
    val resolveOptimizations: ResolveOptimizations,
    private val rootModule: Boolean,
    private val conflictResolution: ConflictResolution
) : CandidateModule {
    val unattachedEdges: MutableList<EdgeState> = LinkedList<EdgeState>()
    private val versions: MutableMap<ModuleVersionIdentifier, ComponentState> = LinkedHashMap<ModuleVersionIdentifier, ComponentState>()
    val selectors: ModuleSelectors<SelectorState>
    private val pendingDependencies: PendingDependencies
    var selected: ComponentState? = null
        private set
    private var mergedConstraintAttributes: ImmutableAttributes = ImmutableAttributes.EMPTY
    private var attributeMergingError: AttributeMergingException? = null
    private var platformState: VirtualPlatformState? = null
    private var overriddenSelection = false
    private var platformOwners: MutableSet<VirtualPlatformState>? = null
    private var replaced = false

    /**
     * True if this module is part of a module conflict, false otherwise.
     */
    var isInModuleConflict: Boolean = false
        private set
    private var selectionChangedCounter = 0

    init {
        this.pendingDependencies = PendingDependencies(id)
        this.selectors = ModuleSelectors<SelectorState>(
            versionComparator,
            versionParser
        )
    }

    fun setSelectorStateResolver(selectorStateResolver: SelectorStateResolver<ComponentState>) {
        this.selectorStateResolver = selectorStateResolver
    }

    fun registerPlatformOwner(owner: VirtualPlatformState) {
        if (platformOwners == null) {
            platformOwners = Sets.newHashSetWithExpectedSize<VirtualPlatformState>(1)
        }
        platformOwners!!.add(owner)
    }

    fun getPlatformOwners(): MutableSet<VirtualPlatformState> {
        return (if (platformOwners == null) kotlin.collections.mutableSetOf<org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.VirtualPlatformState>() else platformOwners)!!
    }

    override fun toString(): String {
        return id.toString()
    }

    override fun getId(): ModuleIdentifier {
        return id
    }

    override fun getVersions(): MutableCollection<ComponentState> {
        if (this.versions.isEmpty()) {
            return mutableListOf<ComponentState>()
        }
        val values: MutableCollection<ComponentState> = this.versions.values
        if (areAllCandidatesForSelection(values)) {
            return values
        }
        val versions: MutableList<ComponentState> = ArrayList<ComponentState>(values.size)
        for (componentState in values) {
            if (componentState.isNotEvicted()) {
                versions.add(componentState)
            }
        }
        return versions
    }

    val allVersions: MutableCollection<ComponentState>
        /**
         * Get all versions of this module that have been seen during graph resolution,
         * even those which are no longer candidates for selection.
         */
        get() = this.versions.values

    /**
     * Selects the target component for this module for the first time.
     * Any existing versions will be evicted.
     */
    fun select(selected: ComponentState) {
        assert(this.selected == null)
        this.selected = selected
        this.replaced = false

        evictOtherComponents(selected)
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private fun evictOtherComponents(selected: ComponentState) {
        for (version in versions.values) {
            if (version !== selected) {
                version.evict()
            } else {
                // TODO: It is suspicious if an evicted component became selected. Once evicted, a component should not be able to be selected.
                version.unEvict()
            }
        }
    }

    /**
     * Marks this module as being part of a module conflict, queueing up all nodes
     * of the current selected component so their subgraphs are deconstructed.
     */
    fun markInModuleConflict() {
        this.isInModuleConflict = true
        checkNotNull(selected)
        for (node in selected!!.getNodes()) {
            resolveState.onFewerSelected(node)
        }

        replaced = false
    }

    /**
     * Resolve a module conflict this module is involved in.
     */
    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    fun resolveModuleConflict(newSelection: ComponentState) {
        assert(this.isInModuleConflict)
        this.isInModuleConflict = false
        if (newSelection.getModule() === this) {
            assert(this.selected === newSelection)
            for (node in newSelection.getNodes()) {
                resolveState.onMoreSelected(node)
            }
            attachUnattachedEdges()
        } else {
            changeSelection(newSelection)
        }
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    override fun changeSelection(newSelection: ComponentState) {
        val oldSelected = this.selected
        this.selected = newSelection
        this.replaced = newSelection.getModule().getId() != getId()

        if (replaced) {
            this.overriddenSelection = true
            newSelection.getModule().getPendingDependencies().retarget(pendingDependencies)
        }

        evictOtherComponents(newSelection)

        if (oldSelected != null && oldSelected !== newSelection) {
            for (node in oldSelected.getNodes()) {
                node.maybeResolveReplacement().restartIncomingEdges()
            }
        }
        for (selector in selectors) {
            selector.overrideSelection(newSelection)
        }
        attachUnattachedEdges()
    }

    private fun attachUnattachedEdges() {
        if (unattachedEdges.size == 1) {
            val singleEdge = unattachedEdges.get(0)
            singleEdge.retarget()
        } else if (!unattachedEdges.isEmpty()) {
            for (edge in ArrayList<EdgeState?>(unattachedEdges)) {
                edge!!.retarget()
            }
        }
    }

    fun addUnattachedEdge(edge: EdgeState) {
        if (!edge.isUnattached()) {
            unattachedEdges.add(edge)
            edge.markUnattached()
        }
    }

    fun removeUnattachedEdge(edge: EdgeState) {
        if (unattachedEdges.remove(edge)) {
            edge.markNotUnattached()
        }
    }

    fun getVersion(id: ModuleVersionIdentifier, componentIdentifier: ComponentIdentifier): ComponentState {
        assert(id.getModule() == this.id)
        val componentState: ComponentState =
            versions.computeIfAbsent(id) { k: ModuleVersionIdentifier? -> ComponentState(resolveState.getIdGenerator().nextGraphNodeId(), this, id, componentIdentifier, metaDataResolver) }

        // Starting in Gradle 10, the root component's module identity will no longer
        // be the module identity of the project performing dependency resolution.
        // In Gradle 10, attempting to resolve the root component using its old module coordinates will no
        // longer resolve the project component of the project performing resolution, but will
        // instead attempt to resolve the component from external repositories.
        if (componentIdentifier is ModuleComponentIdentifier && componentState.isRoot()) {
            deprecateAction("Depending on the resolving project's module coordinates")
                .withAdvice("Use a project dependency instead.")!!
                .willBecomeAnErrorInGradle10()
                .withUpgradeGuideSection(9, "module_identity_for_root_component")!!
                .nagUser()
        }

        return componentState
    }

    fun addSelector(selector: SelectorState, deferSelection: Boolean) {
        selectors.add(selector, deferSelection)
        mergedConstraintAttributes = appendAttributes(mergedConstraintAttributes, selector)
        if (overriddenSelection) {
            checkNotNull(selected) { "An overridden module cannot have selected == null" }
            selector.overrideSelection(selected!!)
        }
    }

    fun removeSelector(selector: SelectorState) {
        selectors.remove(selector)
        mergedConstraintAttributes = ImmutableAttributes.EMPTY
        for (selectorState in selectors) {
            mergedConstraintAttributes = appendAttributes(mergedConstraintAttributes, selectorState)
        }
    }

    fun getMergedConstraintAttributes(): ImmutableAttributes {
        if (attributeMergingError != null) {
            throw IncompatibleDependencyAttributesException(this, attributeMergingError!!)
        }
        return mergedConstraintAttributes
    }

    private fun appendAttributes(dependencyAttributes: ImmutableAttributes, selectorState: SelectorState): ImmutableAttributes {
        var dependencyAttributes = dependencyAttributes
        try {
            val dependencyMetadata = selectorState.getDependencyMetadata()
            val constraint = dependencyMetadata.isConstraint
            if (constraint) {
                val selector: ComponentSelector = dependencyMetadata.selector!!
                val attributes = (selector.getAttributes() as AttributeContainerInternal).asImmutable()
                dependencyAttributes = attributesFactory.safeConcat(attributes, dependencyAttributes)
            }
        } catch (e: AttributeMergingException) {
            attributeMergingError = e
        }
        return dependencyAttributes
    }

    fun visitIncomingEdges(visitor: Consumer<in EdgeState>) {
        if (selected != null) {
            for (node in selected!!.getNodes()) {
                for (incomingEdge in node.getIncomingEdges()) {
                    visitor.accept(incomingEdge)
                }
            }
        }
    }

    fun getPlatformState(): VirtualPlatformState {
        if (platformState == null) {
            platformState = VirtualPlatformState(versionComparator, versionParser, this, resolveOptimizations)
        }
        return platformState!!
    }

    override fun isVirtualPlatform(): Boolean {
        return platformState != null && !platformState!!.getParticipatingModules().isEmpty()
    }

    fun disconnectIncomingEdge(removalSource: NodeState, incomingEdge: EdgeState) {
        // Remove the unattached edge first, as clearing the selector may trigger re-selection and mutate the unattached edge
        removeUnattachedEdge(incomingEdge)
        val needsSelection = incomingEdge.clearSelector()
        var isPending = false
        if (!incomingEdge.isConstraint()) {
            pendingDependencies.decreaseHardEdgeCount()
            if (pendingDependencies.isPending()) {
                // We are back to pending, since we no longer have any hard edges targeting us.
                // All incoming constraint edges must now be removed, as we are no longer part of the graph.
                clearIncomingAttachedConstraints(removalSource)
                clearIncomingUnattachedConstraints(removalSource)
                isPending = true
            }
        }
        // We removed an edge targeting this module, which dropped an existing selector, requiring this
        // module to select a new target component.
        if (needsSelection && !isPending && this.selectors.size() != 0 && this.selected != null) {
            maybeUpdateSelection()
        }
    }

    private fun clearIncomingAttachedConstraints(removalSource: NodeState) {
        if (selected != null) {
            for (node in selected!!.getNodes()) {
                val removedEdges = node.removeAllIncomingEdges()
                for (incomingEdge in removedEdges) {
                    disconnectIncomingConstraint(removalSource, incomingEdge)
                }
            }
        }
    }

    private fun clearIncomingUnattachedConstraints(removalSource: NodeState) {
        for (unattachedEdge in unattachedEdges) {
            disconnectIncomingConstraint(removalSource, unattachedEdge)
            unattachedEdge.markNotUnattached()
        }
        unattachedEdges.clear()
    }

    private fun disconnectIncomingConstraint(removalSource: NodeState, incomingEdge: EdgeState) {
        // Since we are back to pending, any edges targeting this module must be a constraint.
        assert(incomingEdge.getDependencyMetadata().isConstraint)

        val from = incomingEdge.getFrom()
        if (from !== removalSource) {
            // Only remove edges that come from a different node than the source of the dependency going
            // back to pending. The source of the removal is already removing outgoing edges from itself.
            from.removeOutgoingEdge(incomingEdge)
        }
        pendingDependencies.registerConstraintProvider(from)
    }

    val isPending: Boolean
        get() = pendingDependencies.isPending()

    fun getPendingDependencies(): PendingDependencies {
        if (replaced) {
            return selected.getModule().getPendingDependencies()
        }
        return pendingDependencies
    }

    fun registerConstraintProvider(node: NodeState) {
        pendingDependencies.registerConstraintProvider(node)
    }

    fun unregisterConstraintProvider(nodeState: NodeState) {
        pendingDependencies.unregisterConstraintProvider(nodeState)
    }

    //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    fun maybeUpdateSelection() {
        if (replaced) {
            // Never update selection for a replaced module
            return
        }
        if (!rootModule && selectors.checkDeferSelection()) {
            // Selection deferred as we know another selector will be added soon
            return
        }
        val newSelected = selectorStateResolver.selectBest(getId(), selectors)
        newSelected.setSelectors(selectors)
        if (selected == null) {
            select(newSelected)
        } else if (newSelected !== selected) {
            if (++selectionChangedCounter > MAX_SELECTION_CHANGE) {
                // Let's ignore modules that are changing selection way too much, by keeping the highest version
                if (maybeSkipSelectionChange(newSelected)) {
                    // TODO: selectBest updates state, but we ignore that. We should do something with newSelected here
                    // or reset the selectors to before the selectBest call. Alternatively, we should fail here and ask
                    // the user to add a version constraint.
                    return
                }
            }
            changeSelection(newSelected)
        }
    }

    private fun maybeSkipSelectionChange(newSelected: ComponentState): Boolean {
        if (selectionChangedCounter == MAX_SELECTION_CHANGE + 1) {
            LOGGER.warn(
                "The dependency resolution engine wasn't able to find a version of module {} which satisfied all requirements because the graph wasn't stable enough. " +
                        "The highest version was selected in order to stabilize selection.\n" +
                        "Features available in a stable graph like version alignment are not guaranteed in this case.", id
            )
        }
        var newSelectedIsProject = false
        if (conflictResolution == ConflictResolution.preferProjectModules) {
            if (newSelected.componentId is ProjectComponentIdentifier) {
                // Keep the project selected
                newSelectedIsProject = true
            }
        }
        val newVersion = versionParser.transform(newSelected.getVersion())
        val currentVersion = versionParser.transform(selected!!.getVersion())
        return !newSelectedIsProject && versionComparator.compare(newVersion, currentVersion) <= 0
    }

    fun maybeFindForcedPlatformVersion(): String? {
        val selected = this.selected
        for (node in selected!!.getNodes()) {
            if (node.isSelected()) {
                for (incomingEdge in node.getIncomingEdges()) {
                    val dependencyMetadata = incomingEdge.getDependencyMetadata()
                    if (dependencyMetadata !is LenientPlatformDependencyMetadata && dependencyMetadata is ForcingDependencyMetadata) {
                        if (dependencyMetadata.isForce()) {
                            return selected.getVersion()
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * Visit all edges targeting this module, including those which were not successfully
     * attached to a node.
     */
    fun visitAllIncomingEdges(visitor: Consumer<in EdgeState>) {
        visitIncomingEdges(visitor)
        this.unattachedEdges.forEach(visitor)
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(ModuleResolveState::class.java)
        private const val MAX_SELECTION_CHANGE = 1000

        private fun areAllCandidatesForSelection(values: MutableCollection<ComponentState>): Boolean {
            var allCandidates = true
            for (value in values) {
                if (!value.isNotEvicted()) {
                    allCandidates = false
                    break
                }
            }
            return allCandidates
        }
    }
}
