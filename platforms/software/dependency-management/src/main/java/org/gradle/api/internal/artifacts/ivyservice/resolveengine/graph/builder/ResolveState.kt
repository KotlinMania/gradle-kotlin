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
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ComponentSelectorConverter
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint
import org.gradle.api.internal.artifacts.configurations.ConflictResolution
import org.gradle.api.internal.artifacts.dependencies.DefaultResolvedVersionConstraint
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.CapabilitiesConflictHandler
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultCapabilitiesConflictHandler
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultModuleConflictHandler
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.ModuleConflictHandler
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.ComponentStateFactory
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.SelectorStateResolver
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.AttributeSchemaServices
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.specs.Spec
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState
import org.gradle.internal.component.model.ComponentIdGenerator
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.GraphVariantSelector
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver
import java.lang.Boolean
import java.util.ArrayDeque
import java.util.Deque
import kotlin.Any
import kotlin.Comparator
import kotlin.Int
import kotlin.String
import kotlin.math.ln
import kotlin.math.max

/**
 * Global resolution state.
 */
class ResolveState(
    val idGenerator: ComponentIdGenerator,
    rootComponentState: LocalComponentGraphResolveState,
    rootVariant: LocalVariantGraphResolveState,
    private val idResolver: DependencyToComponentIdResolver,
    val componentMetadataResolver: ComponentMetaDataResolver,
    val edgeFilter: Spec<in DependencyMetadata?>,
    val moduleExclusions: ModuleExclusions,
    val componentSelectorConverter: ComponentSelectorConverter,
    val attributesFactory: AttributesFactory,
    val attributeSchemaServices: AttributeSchemaServices,
    val attributeDesugaring: AttributeDesugaring,
    val dependencySubstitutionApplicator: DependencySubstitutionApplicator,
    private val versionSelectorScheme: VersionSelectorScheme,
    versionComparator: VersionComparator,
    private val versionParser: VersionParser,
    private val conflictResolution: ConflictResolution,
    syntheticDependencies: MutableList<out DependencyMetadata>,
    moduleConflictResolver: ModuleConflictResolver<ComponentState?>,
    moduleReplacements: ImmutableModuleReplacements,
    capabilityResolutionRules: ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule>,
    val variantSelector: GraphVariantSelector
) : ComponentStateFactory<ComponentState> {
    private val modules: MutableMap<ModuleIdentifier, ModuleResolveState>
    private val nodes: MutableMap<VariantIdentifier, NodeState>
    private val selectors: MutableMap<SelectorCacheKey, SelectorState>
    val root: RootNode
    private val queue: Deque<NodeState>
    val consumerAttributes: ImmutableAttributes
    val consumerSchema: ImmutableAttributesSchema
    private val versionComparator: Comparator<Version>
    private val selectorStateResolver: SelectorStateResolver<ComponentState>
    val resolveOptimizations: ResolveOptimizations
    private val resolvedVersionConstraints: MutableMap<VersionConstraint, ResolvedVersionConstraint> = HashMap<VersionConstraint, ResolvedVersionConstraint>()
    val moduleConflictHandler: ModuleConflictHandler
    val capabilitiesConflictHandler: CapabilitiesConflictHandler

    init {
        this.versionComparator = versionComparator.asVersionComparator()
        this.resolveOptimizations = ResolveOptimizations()

        this.moduleConflictHandler = DefaultModuleConflictHandler(moduleConflictResolver, moduleReplacements, this)
        this.capabilitiesConflictHandler = DefaultCapabilitiesConflictHandler(capabilityResolutionRules, this)

        val rootModuleVersionId: ModuleVersionIdentifier = rootComponentState.moduleVersionId
        val rootComponentId = rootComponentState.getId()
        this.consumerAttributes = rootVariant.attributes
        this.consumerSchema = rootComponentState.getMetadata()!!.getAttributesSchema()

        val graphSize: Int = estimateGraphSize(rootVariant)
        this.modules = LinkedHashMap<ModuleIdentifier, ModuleResolveState>(graphSize)
        this.nodes = LinkedHashMap<VariantIdentifier, NodeState>(3 * graphSize / 2)
        this.selectors = LinkedHashMap<SelectorCacheKey, SelectorState>(5 * graphSize / 2)
        this.queue = ArrayDeque<NodeState>(graphSize)

        // Create root component and module
        val rootModule = getModule(rootModuleVersionId.getModule(), true)
        val rootComponent = rootModule.getVersion(rootModuleVersionId, rootComponentId!!)
        rootComponent.setRoot()
        rootComponent.setState(rootComponentState, ComponentGraphSpecificResolveState.EMPTY_STATE)
        rootModule.select(rootComponent)

        this.selectorStateResolver = SelectorStateResolver<ComponentState>(
            moduleConflictHandler.getResolver(), this, rootComponent, resolveOptimizations, this.versionComparator,
            versionParser
        )
        rootModule.setSelectorStateResolver(selectorStateResolver)

        // Create root node
        this.root = RootNode(idGenerator.nextGraphNodeId(), rootComponent, this, syntheticDependencies, rootVariant)
        rootComponent.addNode(this.root)
        nodes.put(this.root.getId(), this.root)
    }

    fun getModules(): MutableCollection<ModuleResolveState> {
        return modules.values
    }

    fun findModule(moduleId: ModuleIdentifier): ModuleResolveState? {
        return modules.get(moduleId)
    }

    fun getModule(id: ModuleIdentifier): ModuleResolveState {
        return getModule(id, false)
    }

    private fun getModule(id: ModuleIdentifier, rootModule: Boolean): ModuleResolveState {
        return modules.computeIfAbsent(id) { mid: ModuleIdentifier? ->
            ModuleResolveState(
                this, id,
                this.componentMetadataResolver, attributesFactory, versionComparator, versionParser, selectorStateResolver, resolveOptimizations, rootModule, conflictResolution
            )
        }
    }

    override fun getRevision(componentIdentifier: ComponentIdentifier, id: ModuleVersionIdentifier, state: ComponentGraphResolveState, graphState: ComponentGraphSpecificResolveState): ComponentState {
        val componentState = getModule(id.getModule()).getVersion(id, componentIdentifier)
        if (!componentState.alreadyResolved()) {
            componentState.setState(state, graphState)
        }
        return componentState
    }

    fun getNodes(): MutableCollection<NodeState> {
        return nodes.values
    }

    fun getNode(component: ComponentState, variant: VariantGraphResolveState, selectedByVariantAwareResolution: Boolean): NodeState {
        return nodes.computeIfAbsent(variant.getMetadata()!!.getId()!!) { id: VariantIdentifier? ->
            val node = NodeState(idGenerator.nextGraphNodeId(), component, this, variant, selectedByVariantAwareResolution)
            component.addNode(node)
            node
        }
    }

    fun getSelectors(): MutableCollection<SelectorState> {
        return selectors.values
    }

    fun computeSelectorFor(dependencyState: DependencyState, ignoreVersion: Boolean): SelectorState {
        val isVirtualPlatformEdge = dependencyState.getDependency() is LenientPlatformDependencyMetadata
        val selectorState: SelectorState = selectors.computeIfAbsent(SelectorCacheKey(dependencyState.getRequested(), ignoreVersion, isVirtualPlatformEdge)) { req: SelectorCacheKey? ->
            val moduleIdentifier = dependencyState.getModuleIdentifier(
                this.componentSelectorConverter
            )
            SelectorState(dependencyState, idResolver, this, moduleIdentifier, ignoreVersion)
        }
        selectorState.update(dependencyState)
        return selectorState
    }

    fun peek(): NodeState? {
        return if (queue.isEmpty()) null else queue.getFirst()
    }

    fun pop(): NodeState {
        val next = queue.removeFirst()
        return next.dequeue()
    }

    /**
     * Called when a change is made to a configuration node, such that its dependency graph *may* now be larger than it previously was, and the node should be visited.
     */
    fun onMoreSelected(node: NodeState) {
        // Add to the end of the queue, so that we traverse the graph in breadth-wise order to pick up as many conflicts as
        // possible before attempting to resolve them
        if (node.enqueue()) {
            queue.addLast(node)
        }
    }

    /**
     * Called when a change is made to a configuration node, such that its dependency graph *may* now be smaller than it previously was, and the node should be visited.
     */
    fun onFewerSelected(node: NodeState) {
        // Add to the front of the queue, to flush out configurations that are no longer required.
        if (node.enqueue()) {
            queue.addFirst(node)
        }
    }

    fun resolveVersionConstraint(selector: ComponentSelector): ResolvedVersionConstraint? {
        if (selector is ModuleComponentSelector) {
            return resolveVersionConstraint(selector.getVersionConstraint())
        }
        return null
    }

    fun resolveVersionConstraint(vc: VersionConstraint): ResolvedVersionConstraint {
        return resolvedVersionConstraints.computeIfAbsent(vc) { key: VersionConstraint? -> DefaultResolvedVersionConstraint(key!!, versionSelectorScheme) }
    }

    fun desugarSelector(requested: ComponentSelector): ComponentSelector {
        return attributeDesugaring.desugarSelector(requested)
    }

    private class SelectorCacheKey(
        private val componentSelector: ComponentSelector,
        private val ignoreVersion: Boolean,
        private val virtualPlatformEdge: Boolean
    ) {
        private val hashCode: Int

        init {
            this.hashCode = computeHashCode(
                componentSelector,
                ignoreVersion,
                virtualPlatformEdge
            )
        }

        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as SelectorCacheKey
            return ignoreVersion == that.ignoreVersion && virtualPlatformEdge == that.virtualPlatformEdge &&
                    componentSelector == that.componentSelector
        }

        override fun hashCode(): Int {
            return hashCode
        }

        companion object {
            private fun computeHashCode(
                componentSelector: ComponentSelector,
                ignoreVersion: Boolean,
                virtualPlatformEdge: Boolean
            ): Int {
                var result = componentSelector.hashCode()
                result = 31 * result + Boolean.hashCode(ignoreVersion)
                result = 31 * result + Boolean.hashCode(virtualPlatformEdge)
                return result
            }
        }
    }

    override fun toString(): String {
        return root.getDisplayName() + " resolve state"
    }

    companion object {
        /**
         * This method is a heuristic that gives an idea of the "size" of the graph. The larger
         * the graph is, the higher the risk of internal resizes exists, so we try to estimate
         * the size of the graph to avoid maps resizing.
         */
        private fun estimateGraphSize(rootVariant: VariantGraphResolveState): Int {
            val numDependencies = rootVariant.getDependencies()!!.size

            // TODO #24641: Why are the numbers and operations here the way they are?
            //  Are they up-to-date? We should be able to test if these values are still optimal.
            val estimate = (512 * ln(numDependencies.toDouble())).toInt()
            return max(10, estimate)
        }
    }
}
