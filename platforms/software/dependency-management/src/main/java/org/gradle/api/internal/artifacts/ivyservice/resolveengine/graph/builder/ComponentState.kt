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
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictResolutionDetails
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.of
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.root
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata.Companion.forDependency
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult
import java.lang.Long
import java.util.LinkedList
import java.util.function.Consumer
import java.util.stream.StreamSupport
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.checkNotNull
import kotlin.concurrent.Volatile

/**
 * Resolution state for a given component
 */
class ComponentState internal constructor(
    private val resultId: Long,
    val module: ModuleResolveState,
    val id: ModuleVersionIdentifier,
    private val componentIdentifier: ComponentIdentifier,
    private val resolver: ComponentMetaDataResolver
) : ComponentResolutionState, DependencyGraphComponent {
    val nodes: MutableList<NodeState> = LinkedList<NodeState>()
    private val selectionCauses: MutableList<ComponentSelectionDescriptorInternal> = ArrayList<ComponentSelectionDescriptorInternal>()
    private val hashCode: Int

    @Volatile
    private var resolveState: ComponentGraphResolveState? = null

    @Volatile
    private var graphResolveState: ComponentGraphSpecificResolveState? = null

    /**
     * An evicted component has been evicted and will never (*), ever be chosen starting from the moment it is evicted.
     * Either because it has been excluded, or because conflict resolution selected a different version.
     *
     * // TODO: (*) This invariant was originally stated, but existing logic in practice
     * violated this condition. We should clarify whether we intend for this invariant to hold
     * and if so how we can enforce it.
     */
    private var evicted = false

    var metadataResolveFailure: ModuleVersionResolveException? = null
        private set
    private var selectors: ModuleSelectors<SelectorState>? = null
    var visitState: DependencyGraphBuilder.VisitState = DependencyGraphBuilder.VisitState.NotSeen

    var isRejected: Boolean = false
        private set
    var isRoot: Boolean = false
        private set

    override fun toString(): String {
        return id.toString()
    }

    override fun getVersion(): String {
        return id.getVersion()
    }

    override fun getResultId(): Long {
        return resultId
    }

    override fun getRepositoryName(): String? {
        return graphResolveState!!.getRepositoryName()
    }

    override fun getModuleVersion(): ModuleVersionIdentifier {
        return id
    }

    override fun getMetadataOrNull(): ComponentGraphResolveMetadata? {
        resolve()
        if (resolveState == null) {
            return null
        } else {
            return resolveState!!.getMetadata()
        }
    }

    val metadata: ComponentGraphResolveMetadata
        get() {
            resolve()
            return resolveState!!.getMetadata()
        }

    override fun getResolveState(): ComponentGraphResolveState {
        resolve()
        checkNotNull(resolveState)
        return resolveState!!
    }

    val resolveStateOrNull: ComponentGraphResolveState?
        get() {
            resolve()
            return resolveState
        }

    override fun getComponentId(): ComponentIdentifier {
        // Use the resolved component id if available: this ensures that Maven Snapshot ids are correctly reported
        if (resolveState != null) {
            return resolveState!!.getId()!!
        }
        return componentIdentifier
    }

    fun setSelectors(selectors: ModuleSelectors<SelectorState>) {
        this.selectors = selectors
    }

    /**
     * Returns true if this module version can be resolved quickly (already resolved or local)
     *
     * @return true if it has been resolved in a cheap way
     */
    fun alreadyResolved(): Boolean {
        return resolveState != null || metadataResolveFailure != null
    }

    fun resolve() {
        if (alreadyResolved()) {
            return
        }

        if (module.isVirtualPlatform()) {
            // Modules have registered as participants of this platform via belongsTo.
            // This means the platform is virtual. Resolve with synthetic virtual platform
            // metadata without attempting a real download.
            resolveAsVirtualPlatform()
            return
        }

        val componentOverrideMetadata: ComponentOverrideMetadata?
        if (selectors != null && selectors!!.size() > 0) {
            // Taking the first selector here to determine the 'changing' status is our best bet to get the selector that will most likely be chosen in the end.
            // As selectors are sorted accordingly (see ModuleSelectors.SELECTOR_COMPARATOR).
            val firstSelector = selectors!!.first()

            componentOverrideMetadata = forDependency(firstSelector!!.isChanging(), selectors!!.getFirstDependencyArtifact())
        } else {
            componentOverrideMetadata = DefaultComponentOverrideMetadata.EMPTY
        }

        val result = DefaultBuildableComponentResolveResult()
        resolver.resolve(componentIdentifier, componentOverrideMetadata, result)

        if (result.getFailure() != null) {
            metadataResolveFailure = result.getFailure()
            return
        }
        resolveState = result.getState()
        graphResolveState = result.getGraphState()
    }

    /**
     * Create a new virtual platform state and resolve this component using that state.
     */
    fun resolveAsVirtualPlatform() {
        val resolveState = module.getResolveState()
        val idGenerator = resolveState.getIdGenerator()
        val metadata = LenientPlatformResolveMetadata(componentIdentifier as ModuleComponentIdentifier, id)
        val virtualPlatformState = LenientPlatformGraphResolveState(
            idGenerator.nextComponentId(),
            idGenerator.nextVariantId(),
            metadata,
            module.getPlatformState(),
            resolveState
        )

        setState(virtualPlatformState, ComponentGraphSpecificResolveState.EMPTY_STATE)
    }

    fun setState(state: ComponentGraphResolveState, graphState: ComponentGraphSpecificResolveState) {
        this.resolveState = state
        this.graphResolveState = graphState
        this.metadataResolveFailure = null
    }

    fun addNode(node: NodeState) {
        nodes.add(node)
    }

    private var cachedReason: ComponentSelectionReasonInternal? = null

    init {
        this.hashCode = 31 * id.hashCode() xor Long.hashCode(resultId)
    }

    override fun getSelectionReason(): ComponentSelectionReasonInternal {
        if (cachedReason == null) {
            cachedReason = computeReason()
        }
        return cachedReason!!
    }

    private fun computeReason(): ComponentSelectionReasonInternal {
        if (this.isRoot) {
            return root()
        }

        val builder = ImmutableSet.builder<ComponentSelectionDescriptorInternal>()
        for (selectorState in module.getSelectors()) {
            if (selectorState.getFailure() == null) {
                selectorState.visitSelectionReasons(Consumer { element: ComponentSelectionDescriptorInternal? -> builder.add(element!!) })
            }
        }

        module.visitAllIncomingEdges(Consumer { incomingEdge: EdgeState? -> incomingEdge!!.visitSelectionReasons(Consumer { element: ComponentSelectionDescriptorInternal? -> builder.add(element!!) }) })

        builder.addAll(VersionConflictResolutionDetails.Companion.mergeCauses(selectionCauses))
        return of(builder.build())
    }

    fun hasStrongOpinion(): Boolean {
        return StreamSupport.stream<SelectorState>(module.getSelectors().spliterator(), false)
            .filter { s: SelectorState? -> s!!.getFailure() == null }
            .anyMatch { obj: SelectorState? -> obj!!.hasStrongOpinion() }
    }

    override fun addCause(componentSelectionDescriptor: ComponentSelectionDescriptorInternal) {
        selectionCauses.add(componentSelectionDescriptor)
    }

    fun setRoot() {
        this.isRoot = true
    }

    override fun getSelectedVariants(): MutableList<ResolvedGraphVariant> {
        val builder = ImmutableList.builder<ResolvedGraphVariant>()
        addSelectedVariants(Consumer { element: ResolvedGraphVariant -> builder.add(element) })
        return builder.build()
    }

    private fun addSelectedVariants(consumer: Consumer<ResolvedGraphVariant>) {
        for (node in nodes) {
            if (node.isSelected()) {
                consumer.accept(node)
            }
        }
    }

    override fun getDependents(): MutableList<ComponentState> {
        val incoming: MutableList<ComponentState> = ArrayList<ComponentState>(nodes.size)
        for (node in nodes) {
            for (dependencyEdge in node.getIncomingEdges()) {
                incoming.add(dependencyEdge.getFrom().getComponent())
            }
        }
        return incoming
    }

    val isNotEvicted: Boolean
        get() = !evicted

    fun evict() {
        this.evicted = true
    }

    fun unEvict() {
        this.evicted = false
    }

    override fun reject() {
        this.isRejected = true
    }

    val rejectedErrorMessage: String
        get() = ComponentRejectedMessageBuilder().buildFailureMessage(module)

    val platformOwners: MutableSet<VirtualPlatformState>
        get() = module.getPlatformOwners()

    val platformState: VirtualPlatformState
        get() = module.getPlatformState()

    val implicitCapability: ImmutableCapability
        get() = resolveState!!.getDefaultCapability()

    fun hasMoreThanOneSelectedNodeUsingVariantAwareResolution(): Boolean {
        var count = 0
        for (node in nodes) {
            if (node.isSelectedByVariantAwareResolution()) {
                count++
                if (count == 2) {
                    return true
                }
            }
        }
        return false
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as ComponentState

        return that.resultId == resultId
    }

    override fun hashCode(): Int {
        return hashCode
    }
}
