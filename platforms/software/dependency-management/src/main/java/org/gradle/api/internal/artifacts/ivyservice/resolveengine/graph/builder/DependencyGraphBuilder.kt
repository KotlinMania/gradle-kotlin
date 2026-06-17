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

import com.google.common.base.Predicate
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import com.google.common.collect.Sets
import org.apache.commons.lang3.tuple.ImmutablePair
import org.apache.commons.lang3.tuple.Pair
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ComponentSelectorConverter
import org.gradle.api.internal.artifacts.configurations.ConflictResolution
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements
import org.gradle.api.internal.artifacts.ivyservice.ResolutionParameters
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.Conflict
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictException
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints.equals
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.specs.Spec
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState
import org.gradle.internal.component.model.ComponentGraphResolveMetadata.getId
import org.gradle.internal.component.model.ComponentGraphResolveState.getId
import org.gradle.internal.component.model.ComponentIdGenerator
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.GraphVariantSelector
import org.gradle.internal.component.model.VariantGraphResolveMetadata
import org.gradle.internal.component.model.VariantGraphResolveMetadata.getId
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler
import org.gradle.internal.operations.BuildOperationConstraint
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.LinkedList
import java.util.function.Consumer
import java.util.stream.Collectors
import javax.inject.Inject

@ServiceScope(Scope.Project::class)
class DependencyGraphBuilder @Inject constructor(
    private val moduleExclusions: ModuleExclusions,
    private val attributesFactory: AttributesFactory,
    private val attributeSchemaServices: AttributeSchemaServices,
    private val attributeDesugaring: AttributeDesugaring,
    private val versionSelectorScheme: VersionSelectorScheme,
    private val versionComparator: VersionComparator,
    private val idGenerator: ComponentIdGenerator,
    private val versionParser: VersionParser,
    private val variantSelector: GraphVariantSelector,
    private val buildOperationExecutor: BuildOperationExecutor
) {
    fun resolve(
        rootComponent: LocalComponentGraphResolveState,
        rootVariant: LocalVariantGraphResolveState,
        syntheticDependencies: MutableList<out DependencyMetadata>,
        edgeFilter: Spec<in DependencyMetadata?>,
        componentSelectorConverter: ComponentSelectorConverter,
        componentIdResolver: DependencyToComponentIdResolver,
        componentMetaDataResolver: ComponentMetaDataResolver,
        moduleReplacements: ImmutableModuleReplacements,
        dependencySubstitutionApplicator: DependencySubstitutionApplicator,
        moduleConflictResolver: ModuleConflictResolver<ComponentState?>,
        capabilityResolutionRules: ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule>,
        conflictResolution: ConflictResolution,
        failingOnDynamicVersions: Boolean,
        failingOnChangingVersions: Boolean,
        failureResolutions: ResolutionParameters.FailureResolutions,
        modelVisitor: DependencyGraphVisitor
    ) {
        val resolveState = ResolveState(
            idGenerator,
            rootComponent,
            rootVariant,
            componentIdResolver,
            componentMetaDataResolver,
            edgeFilter,
            moduleExclusions,
            componentSelectorConverter,
            attributesFactory,
            attributeSchemaServices,
            attributeDesugaring,
            dependencySubstitutionApplicator,
            versionSelectorScheme,
            versionComparator,
            versionParser,
            conflictResolution,
            syntheticDependencies,
            moduleConflictResolver,
            moduleReplacements,
            capabilityResolutionRules,
            variantSelector
        )

        traverseGraph(resolveState)

        validateGraph(resolveState, failingOnDynamicVersions, failingOnChangingVersions, conflictResolution, failureResolutions)

        assembleResult(resolveState, modelVisitor)
    }

    /**
     * Traverses the dependency graph, resolving conflicts and building the paths from the root configuration.
     */
    private fun traverseGraph(resolveState: ResolveState) {
        resolveState.onMoreSelected(resolveState.getRoot())
        val edges: MutableList<EdgeState> = ArrayList<EdgeState>()

        val moduleConflictHandler = resolveState.getModuleConflictHandler()
        val capabilitiesConflictHandler = resolveState.getCapabilitiesConflictHandler()

        while (resolveState.peek() != null || moduleConflictHandler.hasConflicts() || capabilitiesConflictHandler.hasConflicts()) {
            if (resolveState.peek() != null) {
                val node = resolveState.pop()

                if (!node.shouldBuildSubgraph()) {
                    node.removeOutgoingEdges()
                    continue
                }

                // This node is part of the graph. Check if it conflicts with any other node in the graph.
                if (capabilitiesConflictHandler.registerCandidate(node)) {
                    // We have a conflict, so we need to resolve it first, since this node may not win the conflict.
                    // There is no reason to continue processing this node otherwise.
                    continue
                }

                // This node is part of the graph and is not in conflict. Process its outgoing dependencies.
                edges.clear()
                node.visitOutgoingDependenciesAndCollectEdges(edges)
                resolveEdges(node, edges, resolveState)
            } else {
                // We have some batched up conflicts. Resolve the first, and continue traversing the graph
                if (moduleConflictHandler.hasConflicts()) {
                    moduleConflictHandler.resolveNextConflict()
                } else {
                    capabilitiesConflictHandler.resolveNextConflict()
                }
            }
        }
    }

    private fun resolveEdges(
        node: NodeState,
        dependencies: MutableList<EdgeState>,
        resolveState: ResolveState
    ) {
        if (dependencies.isEmpty()) {
            return
        }

        performSelectionSerially(dependencies, resolveState)
        maybeDownloadMetadataInParallel(node, dependencies, buildOperationExecutor, resolveState.getComponentMetadataResolver())
        attachToTargetRevisionsSerially(dependencies)
    }

    internal enum class VisitState {
        NotSeen, Visiting, Visited
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DependencyGraphBuilder::class.java)

        private fun performSelectionSerially(edges: MutableList<EdgeState>, resolveState: ResolveState) {
            for (edge in edges) {
                // Selection of prior edges can cause the source node to enter a module conflict, thus
                // causing further edges to be released.
                if (edge.isUsed()) {
                    val selector = edge.getSelector()
                    val module = selector.getTargetModule()

                    // TODO: It is odd that we have to check module.getSelectors().size() here.
                    //       We already have a selector, its module should know about it.
                    if (selector.canAffectSelection() && module.getSelectors().size() > 0) {
                        // Have an unprocessed/new selector for this module. Need to re-select the target version (if there are any selectors that can be used).
                        performSelection(resolveState, module)
                    }
                }
            }
        }

        /**
         * Attempts to resolve a target `ComponentState` for the given dependency.
         * On successful resolve, a `ComponentState` is constructed for the identifier, recorded as [ModuleResolveState.getSelected],
         * and added to the graph.
         * On resolve failure, the failure is recorded and no `ComponentState` is selected.
         */
        private fun performSelection(resolveState: ResolveState, module: ModuleResolveState) {
            val currentSelection = module.getSelected()

            try {
                module.maybeUpdateSelection()
            } catch (e: ModuleVersionResolveException) {
                // Ignore: All selectors failed, and will have failures recorded
                return
            }

            // If no current selection for module, just use the candidate.
            if (currentSelection == null) {
                // This is the first time we've seen the module, so register with conflict resolver.
                resolveState.getModuleConflictHandler().registerCandidate(module)
            }
        }

        /**
         * Prepares the resolution of edges, either serially or concurrently.
         * It uses a simple heuristic to determine if we should perform concurrent resolution, based on the number of edges, and whether they have unresolved metadata.
         */
        private fun maybeDownloadMetadataInParallel(
            node: NodeState,
            edges: MutableList<EdgeState>,
            buildOperationExecutor: BuildOperationExecutor,
            componentMetaDataResolver: ComponentMetaDataResolver
        ) {
            var requiringDownload: MutableList<ComponentState>? = null
            for (edge in edges) {
                val targetComponent = edge.getTargetComponent()
                if (targetComponent != null && targetComponent.isNotEvicted() && !targetComponent.alreadyResolved()) {
                    if (!componentMetaDataResolver.isFetchingMetadataCheap(targetComponent.componentId)) {
                        // Avoid initializing the list if there are no components requiring download (a common case)
                        if (requiringDownload == null) {
                            requiringDownload = ArrayList<ComponentState>()
                        }
                        requiringDownload.add(targetComponent)
                    }
                }
            }
            // Only download in parallel if there is more than 1 component to download
            if (requiringDownload != null && requiringDownload.size > 1) {
                val toDownloadInParallel = ImmutableList.copyOf<ComponentState>(requiringDownload)
                LOGGER.debug("Submitting {} metadata files to resolve in parallel for {}", toDownloadInParallel.size, node)
                buildOperationExecutor.runAll<RunnableBuildOperation>(Action { buildOperationQueue: BuildOperationQueue<RunnableBuildOperation>? ->
                    for (componentState in toDownloadInParallel) {
                        buildOperationQueue!!.add(DownloadMetadataOperation(componentState))
                    }
                }, BuildOperationConstraint.UNCONSTRAINED)
            }
        }

        private fun attachToTargetRevisionsSerially(edges: MutableList<EdgeState>) {
            // the following only needs to be done serially to preserve ordering of dependencies in the graph: we have visited the edges
            // but we still didn't add the result to the queue. Doing it from resolve threads would result in non-reproducible graphs, where
            // edges could be added in different order. To avoid this, the addition of new edges is done serially.
            for (edge in edges) {
                edge.attachToTargetNodes()
            }
        }

        private fun validateGraph(
            resolveState: ResolveState,
            denyDynamicSelectors: Boolean,
            denyChangingModules: Boolean,
            conflictResolution: ConflictResolution,
            failureResolutions: ResolutionParameters.FailureResolutions
        ) {
            val consumerSchema = resolveState.getConsumerSchema()
            for (module in resolveState.getModules()) {
                val selected = module.getSelected()
                if (selected != null) {
                    val resolutionFailureHandler = resolveState.getVariantSelector().failureHandler
                    if (selected.isRejected) {
                        val conflictResolutions: MutableList<String> = buildConflictResolutions(selected, failureResolutions).getRight()
                        val error: GradleException = resolutionFailureHandler.componentRejected(selected, conflictResolutions)
                        // We need to attach failures on unattached dependencies too, in case a node wasn't selected
                        // at all, but we still want to see an error message for it.
                        module.visitAllIncomingEdges(Consumer { edge: EdgeState? -> edge!!.failWith(error) })
                    } else if (Iterables.any<NodeState>(selected.getNodes(), Predicate { node: NodeState -> node.getReplacement() == null })) {
                        for (node in selected.getNodes()) {
                            if (node.isRejectedForCapabilityConflict()) {
                                val error: GradleException = resolutionFailureHandler.nodeRejected(node)
                                node.getIncomingEdges().forEach(Consumer { edge: EdgeState? -> edge!!.failWith(error) })
                            }
                        }
                        if (module.isVirtualPlatform()) {
                            attachMultipleForceOnPlatformFailureToEdges(module)
                        } else if (selected.hasMoreThanOneSelectedNodeUsingVariantAwareResolution()) {
                            validateMultipleNodeSelection(consumerSchema, module, selected, resolutionFailureHandler, resolveState.getAttributeSchemaServices())
                        }
                        if (denyDynamicSelectors) {
                            validateDynamicSelectors(selected)
                        }
                        if (denyChangingModules) {
                            validateChangingVersions(selected)
                        }
                        if (conflictResolution == ConflictResolution.strict) {
                            validateVersionConflicts(selected, failureResolutions)
                        }
                    }
                } else if (module.isVirtualPlatform()) {
                    attachMultipleForceOnPlatformFailureToEdges(module)
                }
            }

            assertHasValidGraphStructure(resolveState)
        }

        /**
         * Tests for fundamentally broken graphs. Only enabled when assertions are enabled,
         * as we do not expect any user-constructed graphs to fail these assertions. All valid
         * and invalid graphs (those with version/module/capability conflicts, or resolution failures)
         * should pass the assertions in this method.
         */
        private fun assertHasValidGraphStructure(resolveState: ResolveState) {
            if (!areAssertsEnabled()) {
                return
            }

            for (module in resolveState.getModules()) {
                // TODO: This condition currently fails, but should pass!
//            if (!module.getUnattachedEdges().isEmpty()) {
//                throw new IllegalStateException(String.format(
//                    "Module %s has unattached edges: [%s]",
//                    module,
//                    module.getUnattachedEdges().stream().map(EdgeState::toString).collect(Collectors.joining(", "))
//                ));
//            }
                for (component in module.getVersions()) {
                    for (node in component.getNodes()) {
                        for (incomingEdge in node.getIncomingEdges()) {
                            val from = incomingEdge.getFrom()
                            check(from.getOutgoingEdges().contains(incomingEdge)) {
                                String.format(
                                    "Node %s has incoming edge from %s, but source node does not declare outgoing edge.",
                                    node.getDisplayName(),
                                    from.getDisplayName()
                                )
                            }
                            check(from.isSelected()) {
                                String.format(
                                    "Node %s has an incoming edge from %s, but source node is not part of the graph.",
                                    from.getDisplayName(),
                                    node.getDisplayName()
                                )
                            }
                        }
                        for (outgoingEdge in node.getOutgoingEdges()) {
                            for (target in outgoingEdge.getTargetNodes()) {
                                check(target.getIncomingEdges().contains(outgoingEdge)) {
                                    String.format(
                                        "Node %s has an outgoing edge to node %s, but target node does not declare incoming edge.",
                                        node.getDisplayName(),
                                        target.getDisplayName()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        fun areAssertsEnabled(): Boolean {
            var assertsEnabled = false
            assert(true.also { assertsEnabled = it })
            return assertsEnabled
        }

        private fun isDynamic(selector: SelectorState): Boolean {
            val versionConstraint = selector.getVersionConstraint()
            if (versionConstraint != null) {
                return versionConstraint.isDynamic
            }
            return false
        }

        private fun validateDynamicSelectors(selected: ComponentState) {
            val selectors: MutableList<SelectorState> = ImmutableList.copyOf<SelectorState>(selected.getModule().getSelectors())
            if (!selectors.isEmpty()) {
                if (selectors.stream().allMatch { selector: SelectorState? -> Companion.isDynamic(selector!!) }) {
                    // when all selectors are dynamic, result is undoubtedly unstable
                    markDeniedDynamicVersions(selected)
                } else if (selectors.stream().anyMatch { selector: SelectorState? -> Companion.isDynamic(selector!!) }) {
                    checkIfDynamicVersionAllowed(selected, selectors)
                }
            }
        }

        private fun checkIfDynamicVersionAllowed(selected: ComponentState, selectors: MutableList<SelectorState>) {
            val version: String = selected.getId().getVersion()
            // There must be at least one non dynamic selector agreeing with the selection
            // for the resolution result to be stable
            // and for dynamic selectors, only the "stable" ones work, which is currently
            // only ranges because those are the only ones which accept a selection without
            // upgrading
            var accept = false
            for (selector in selectors) {
                val versionConstraint = selector.getVersionConstraint()
                if (!versionConstraint!!.isDynamic) {
                    // this selector is not dynamic, let's see if it agrees with the selection
                    if (versionConstraint.accepts(version)) {
                        accept = true
                    }
                } else if (!versionConstraint.canBeStable()) {
                    accept = false
                    break
                }
            }
            if (!accept) {
                markDeniedDynamicVersions(selected)
            }
        }

        private fun markDeniedDynamicVersions(cs: ComponentState) {
            for (node in cs.getNodes()) {
                val incomingEdges = node.getIncomingEdges()
                for (incomingEdge in incomingEdges) {
                    val selector = incomingEdge.getSelector().getSelector()
                    incomingEdge.failWith(
                        ModuleVersionResolveException(
                            selector,
                            org.gradle.internal.Factory { String.format("Could not resolve %s: Resolution strategy disallows usage of dynamic versions", selector) })
                    )
                }
            }
        }

        private fun validateChangingVersions(selected: ComponentState) {
            val metadata = selected.metadataOrNull
            val moduleIsChanging = metadata != null && metadata.isChanging()
            for (node in selected.getNodes()) {
                val incomingEdges = node.getIncomingEdges()
                for (incomingEdge in incomingEdges) {
                    if (moduleIsChanging || incomingEdge.getDependencyMetadata().isChanging) {
                        val selector = incomingEdge.getSelector().getSelector()
                        incomingEdge.failWith(
                            ModuleVersionResolveException(
                                selector,
                                org.gradle.internal.Factory { String.format("Could not resolve %s: Resolution strategy disallows usage of changing versions", selector) })
                        )
                    }
                }
            }
        }

        /**
         * Verify the given component was not selected via version conflict resolution.
         * In other words, ensure only one version of this component was requested.
         */
        private fun validateVersionConflicts(
            selected: ComponentState,
            failureResolutions: ResolutionParameters.FailureResolutions
        ) {
            if (!selected.getSelectionReason().isConflictResolution()) {
                return
            }

            // This component was selected due to version conflict resolution.
            // Fail all incoming edges.
            val resolutions: Pair<Conflict, MutableList<String>> = buildConflictResolutions(selected, failureResolutions)
            val failure = VersionConflictException(resolutions.getLeft(), resolutions.getRight())

            for (node in selected.getNodes()) {
                for (incomingEdge in node.getIncomingEdges()) {
                    incomingEdge.failWith(failure)
                }
            }
        }

        private fun buildConflictResolutions(selected: ComponentState, failureResolutions: ResolutionParameters.FailureResolutions): Pair<Conflict, MutableList<String>> {
            val participants: ImmutableList<Conflict.Participant> = selected.getModule().getAllVersions().stream()
                .map<Conflict.Participant> { component: ComponentState? -> Conflict.Participant(component.getId().getVersion(), component!!.componentId) }
                .collect(ImmutableList.toImmutableList<Conflict.Participant>())

            val conflict = Conflict(
                participants,
                selected.getModuleVersion().getModule(),
                selected.getSelectionReason()
            )

            return ImmutablePair<Conflict, MutableList<String>>(conflict, failureResolutions.forVersionConflict(conflict))
        }

        /**
         * Validates that all selected nodes of a single component have compatible attributes,
         * when using variant aware resolution.
         */
        private fun validateMultipleNodeSelection(
            consumerSchema: ImmutableAttributesSchema,
            module: ModuleResolveState,
            selected: ComponentState,
            resolutionFailureHandler: ResolutionFailureHandler,
            attributeSchemaServices: AttributeSchemaServices
        ) {
            val selectedNodes = selected.getNodes().stream()
                .filter { n: NodeState? -> n!!.isSelected() && !n.isAttachedToVirtualPlatform() && !n.hasShadowedCapability() && !n.isRejectedForCapabilityConflict() }
                .collect(Collectors.toSet())

            if (selectedNodes.size < 2) {
                return
            }

            val combinations = Sets.combinations<NodeState>(selectedNodes, 2)
            val incompatibleNodes: MutableSet<NodeState> = HashSet<NodeState>()

            val matcher = attributeSchemaServices.getMatcher(consumerSchema, selected.getMetadata().getAttributesSchema()!!)
            for (combination in combinations) {
                val it = combination.iterator()
                val first = it.next()
                val second = it.next()

                if (!matcher.areMutuallyCompatible(first.getMetadata().getAttributes()!!, second.getMetadata().getAttributes()!!)) {
                    incompatibleNodes.add(first)
                    incompatibleNodes.add(second)
                }
            }

            if (!incompatibleNodes.isEmpty()) {
                val incompatibleNodeMetadatas = incompatibleNodes.stream()
                    .map<VariantGraphResolveMetadata> { obj: NodeState? -> obj!!.getMetadata() }
                    .collect(Collectors.toSet())
                val variantsSelectionException = resolutionFailureHandler.incompatibleMultipleNodesValidationFailure(matcher, selected.getMetadata(), incompatibleNodeMetadatas)
                module.visitIncomingEdges(Consumer { edge: EdgeState? -> edge!!.failWith(variantsSelectionException) })
            }
        }

        private fun attachMultipleForceOnPlatformFailureToEdges(module: ModuleResolveState) {
            var forcedEdges: MutableList<EdgeState>? = null
            var hasMultipleVersions = false
            var currentVersion = module.maybeFindForcedPlatformVersion()
            val participatingModules = module.getPlatformState().getParticipatingModules()
            for (participatingModule in participatingModules) {
                val selected = participatingModule.getSelected()
                if (selected != null) {
                    for (nodeState in selected.getNodes()) {
                        for (incomingEdge in nodeState.getIncomingEdges()) {
                            val selector = incomingEdge.getSelector()
                            if (isPlatformForcedEdge(selector)) {
                                val componentSelector = selector.getSelector()
                                if (componentSelector is ModuleComponentSelector) {
                                    val mcs = componentSelector
                                    if (incomingEdge.getFrom().getComponent().getModule() != module) {
                                        if (forcedEdges == null) {
                                            forcedEdges = ArrayList<EdgeState>()
                                        }
                                        forcedEdges.add(incomingEdge)
                                        if (currentVersion == null) {
                                            currentVersion = mcs.getVersion()
                                        } else {
                                            if (currentVersion != mcs.getVersion()) {
                                                hasMultipleVersions = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (hasMultipleVersions) {
                val failure = GradleException("Multiple forces on different versions for virtual platform " + module.getId())
                forcedEdges!!.forEach(Consumer { edge: EdgeState? -> edge!!.failWith(failure) })
            }
        }

        private fun isPlatformForcedEdge(selector: SelectorState): Boolean {
            return selector.isForce() && !selector.isSoftForce()
        }

        /**
         * Populates the result from the graph traversal state.
         */
        private fun assembleResult(resolveState: ResolveState, visitor: DependencyGraphVisitor) {
            visitor.start(resolveState.getRoot())

            // Visit the nodes prior to visiting the edges
            for (nodeState in resolveState.getNodes()) {
                if (nodeState.shouldIncludedInGraphResult()) {
                    visitor.visitNode(nodeState)
                }
            }

            // Collect the components to sort in consumer-first order
            val queue = LinkedList<ComponentState>()
            for (module in resolveState.getModules()) {
                if (module.getSelected() != null && !module.isVirtualPlatform()) {
                    queue.add(module.getSelected()!!)
                }
            }

            // Visit the edges after sorting the components in consumer-first order
            while (!queue.isEmpty()) {
                val component = queue.peekFirst()
                if (component.getVisitState() == VisitState.NotSeen) {
                    component.setVisitState(VisitState.Visiting)
                    var pos = 0
                    for (node in component.getNodes()) {
                        if (!node.isSelected()) {
                            continue
                        }
                        for (edge in node.getIncomingEdges()) {
                            val owner = edge.getFrom().getOwner()
                            if (owner.getVisitState() == VisitState.NotSeen && !owner.getModule().isVirtualPlatform()) {
                                queue.add(pos, owner)
                                pos++
                            } // else, already visited or currently visiting (which means a cycle), skip
                        }
                    }
                    if (pos == 0) {
                        // have visited all consumers, so visit this node
                        component.setVisitState(VisitState.Visited)
                        queue.removeFirst()
                        for (node in component.getNodes()) {
                            if (node.isSelected()) {
                                visitor.visitEdges(node)
                            }
                        }
                    }
                } else if (component.getVisitState() == VisitState.Visiting) {
                    // have visited all consumers, so visit this node
                    component.setVisitState(VisitState.Visited)
                    queue.removeFirst()
                    for (node in component.getNodes()) {
                        if (node.isSelected()) {
                            visitor.visitEdges(node)
                        }
                    }
                } else {
                    // else, already visited previously, skip
                    queue.removeFirst()
                }
            }

            visitor.finish(resolveState.getRoot())
        }
    }
}
