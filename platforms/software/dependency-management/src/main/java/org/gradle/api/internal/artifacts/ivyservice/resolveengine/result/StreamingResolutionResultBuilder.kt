/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import com.google.common.collect.ImmutableList
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.UnresolvedDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.result.ComponentSelectionCause
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ModuleVersionIdentifierSerializer
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.cache.internal.BinaryStore
import org.gradle.cache.internal.Store
import org.gradle.internal.Describables
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector.Companion.newSelector
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.caching.DesugaringAttributeContainerSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.time.Time.startTimer
import java.io.IOException
import java.util.function.Supplier
import java.util.stream.Collectors

class StreamingResolutionResultBuilder(
    private val store: BinaryStore,
    private val cache: Store<GraphStructure>,
    private val graphElementStore: ThisBuildTreeOnlyGraphElementStore,
    private val attributeDesugaring: AttributeDesugaring,
    capabilitySelectorSerializer: CapabilitySelectorSerializer,
    componentSelectionDescriptorFactory: ComponentSelectionDescriptorFactory,
    moduleIdentifierFactory: ImmutableModuleIdentifierFactory,
    attributeFactory: AttributesFactory,
    namedDomainObjectInstantiator: NamedObjectInstantiator,
    includeAllSelectableVariantResults: Boolean
) : DependencyGraphVisitor {
    // Serializers
    private val statefulSerializersFactory: Supplier<StatefulSerializers>
    private val reasonSerializer: ComponentSelectionReasonSerializer
    private val componentIdSerializer: Serializer<ComponentIdentifier?>
    private val attributeContainerSerializer: AttributeContainerSerializer
    private val capabilitySerializer: ImmutableCapabilitiesSerializer
    private val moduleVersionIdSerializer: Serializer<ModuleVersionIdentifier?>
    private val componentSelectorSerializer: Serializer<ComponentSelector?>

    // State
    private var mayHaveVirtualPlatforms = false
    private var rootAttributes: ImmutableAttributes? = null
    private val visitedComponents: LongSet = LongOpenHashSet()
    private val failures: MutableList<ModuleVersionResolveException> = ArrayList<ModuleVersionResolveException>()
    private var allSelectableVariantResults: MutableList<MutableList<ResolvedVariantResult>>? = null

    init {
        // These deduplicating serializers reduce the size overhead of the serialized
        // graphs and their de-serialized in-memory representation.
        // However, since they are stateful, we must create a new instance each time we
        // serialize and deserialize a graph.
        this.statefulSerializersFactory = Supplier {
            StatefulSerializers.Companion.create(
                capabilitySelectorSerializer,
                attributeFactory,
                namedDomainObjectInstantiator
            )
        }

        val statefulSerializers = statefulSerializersFactory.get()
        this.reasonSerializer = ComponentSelectionReasonSerializer(componentSelectionDescriptorFactory)
        this.componentIdSerializer = ComponentIdentifierSerializer()
        this.attributeContainerSerializer = statefulSerializers.attributeContainerSerializer
        this.capabilitySerializer = ImmutableCapabilitiesSerializer()
        this.moduleVersionIdSerializer = ModuleVersionIdentifierSerializer(moduleIdentifierFactory)
        this.componentSelectorSerializer = statefulSerializers.componentSelectorSerializer

        if (includeAllSelectableVariantResults) {
            this.allSelectableVariantResults = ArrayList<MutableList<ResolvedVariantResult>>()
        }
    }

    fun getResolvedDependencyGraph(dependencyLockingFailures: MutableSet<UnresolvedDependency>): ResolvedDependencyGraph {
        val data = store.done()
        val graphSource = GraphFactory(data, cache, failures, dependencyLockingFailures, Supplier {
            val statefulSerializers = statefulSerializersFactory.get()
            GraphDeserializer(
                statefulSerializers.componentSelectorSerializer,
                statefulSerializers.attributeContainerSerializer,
                graphElementStore,
                reasonSerializer,
                componentIdSerializer,
                capabilitySerializer,
                moduleVersionIdSerializer,
                attributeDesugaring
            )
        })
        checkNotNull(rootAttributes) { "Cannot get graph structure before graph is visited." }
        return ResolvedDependencyGraph(rootAttributes!!, Supplier { graphSource.create() }, allSelectableVariantResults)
    }

    override fun start(root: RootGraphNode) {
        this.rootAttributes = root.getMetadata().getAttributes()
        this.mayHaveVirtualPlatforms = root.getResolveOptimizations().mayHaveVirtualPlatforms()
        // TODO: We should write the size of the graph at the beginning of traversal
        // so we can initialize the GraphStructureBuilder to avoid resizes/copying
        store.write(BinaryStore.WriteAction { encoder: Encoder? ->
            encoder!!.writeSmallLong(root.getNodeId())
        })
    }

    override fun finish(root: RootGraphNode) {
        store.write(BinaryStore.WriteAction { encoder: Encoder? ->
            encoder!!.writeByte(END)
        })
    }

    override fun visitEdges(node: DependencyGraphNode) {
        store.write(BinaryStore.WriteAction { encoder: Encoder? ->
            val component = node.getOwner()
            val adhoc = component.getResolveState().isAdHoc()
            if (visitedComponents.add(component.getResultId())) {
                writeComponent(encoder!!, component, adhoc)
            }
            writeNode(node, encoder!!, adhoc)
        })
    }

    @Throws(Exception::class)
    private fun writeComponent(encoder: Encoder, component: DependencyGraphComponent, adhoc: Boolean) {
        encoder.writeByte(COMPONENT)
        encoder.writeSmallLong(component.getResultId())
        reasonSerializer.write(encoder, component.getSelectionReason())
        encoder.writeNullableString(component.getRepositoryName())

        encoder.writeBoolean(adhoc)
        if (adhoc) {
            val componentState = component.getResolveState()
            componentIdSerializer.write(encoder, componentState.getId())
            moduleVersionIdSerializer.write(encoder, componentState.getMetadata()!!.getModuleVersionId())
        } else {
            val instanceId = graphElementStore.storeComponentReference(component.getResolveState())
            encoder.writeSmallLong(instanceId)
        }

        if (allSelectableVariantResults != null) {
            allSelectableVariantResults!!.add(getAllSelectableVariantResults(component.getResolveState()))
        }
    }

    private fun getAllSelectableVariantResults(component: ComponentGraphResolveState): MutableList<ResolvedVariantResult> {
        return component.getCandidatesForGraphVariantSelection()!!
            .getVariantsForAttributeMatching()!!
            .stream()
            .flatMap { variant: VariantGraphResolveState? -> variant!!.prepareForArtifactResolution()!!.getArtifactVariants()!!.stream() }
            .map<DefaultResolvedVariantResult> { artifactSet: VariantResolveMetadata? ->
                DefaultResolvedVariantResult(
                    component.getId()!!,
                    Describables.of(artifactSet!!.name!!),
                    attributeDesugaring.desugar(artifactSet.attributes.asImmutable()),
                    capabilitiesFor(artifactSet.capabilities, component),
                    null
                )
            }
            .collect(Collectors.toList())
    }

    @Throws(Exception::class)
    private fun writeNode(node: DependencyGraphNode, encoder: Encoder, adhoc: Boolean) {
        val component = node.getOwner()

        encoder.writeByte(NODE)
        encoder.writeSmallLong(node.getNodeId())
        encoder.writeSmallLong(component.getResultId())

        encoder.writeBoolean(adhoc)
        if (adhoc) {
            encoder.writeString(node.getMetadata().getName())
            attributeContainerSerializer.write(encoder, node.getMetadata().getAttributes())
            capabilitySerializer.write(encoder, node.getMetadata().getCapabilities())
        } else {
            val instanceId = graphElementStore.storeVariantReference(node.getResolveState())
            encoder.writeSmallLong(instanceId)

            val externalVariant = node.getExternalVariant()
            if (externalVariant != null) {
                encoder.writeBoolean(true)
                encoder.writeSmallLong(externalVariant.getNodeId())
            } else {
                encoder.writeBoolean(false)
            }
        }

        encoder.writeSmallInt(node.getOutgoingEdges().size)
        for (dependency in node.getOutgoingEdges()) {
            writeEdge(encoder, dependency)
        }
    }

    @Throws(Exception::class)
    private fun writeEdge(encoder: Encoder, edge: DependencyGraphEdge) {
        val failure = edge.getFailure()
        val constraint = edge.isConstraint()
        if (failure == null) {
            val targetNodes = edge.getTargetNodes()
            check(!targetNodes.isEmpty()) { "Edge " + edge + " has no target nodes." }
            if (constraint) {
                writeConstraintEdge(encoder, edge)
            } else {
                writeHardEdge(encoder, edge)
            }
        } else {
            encoder.writeSmallInt(-1)
            encoder.writeBoolean(constraint)
            componentSelectorSerializer.write(encoder, edge.getRequested())
            reasonSerializer.write(encoder, edge.getReason())
            failures.add(failure)
        }
    }

    @Throws(Exception::class)
    private fun writeConstraintEdge(encoder: Encoder, edge: DependencyGraphEdge) {
        // Only write the first target node for constraints, as this is historical
        // behavior. Eventually, we should model constraints differently in the public
        // API so they do not report a target node at all, as constraints conceptually
        // only target components.
        val firstTargetNode: DependencyGraphNode = edge.getTargetNodes().get(0)
        if (!mayHaveVirtualPlatforms || !firstTargetNode.getComponent().module.isVirtualPlatform()) {
            encoder.writeSmallInt(1)
            encoder.writeBoolean(true)
            componentSelectorSerializer.write(encoder, edge.getRequested())
            encoder.writeSmallLong(firstTargetNode.getNodeId())
        } else {
            encoder.writeSmallInt(0)
        }
    }

    @Throws(Exception::class)
    private fun writeHardEdge(encoder: Encoder, edge: DependencyGraphEdge) {
        val targetNodes = edge.getTargetNodes()

        var size = 0
        if (mayHaveVirtualPlatforms) {
            for (targetNode in targetNodes) {
                if (!targetNode.getComponent().module.isVirtualPlatform()) {
                    size++
                }
            }
        } else {
            size = targetNodes.size
        }
        encoder.writeSmallInt(size)
        if (size > 0) {
            encoder.writeBoolean(false)
            componentSelectorSerializer.write(encoder, edge.getRequested())
            for (targetNode in targetNodes) {
                if (!mayHaveVirtualPlatforms || !targetNode.getComponent().module.isVirtualPlatform()) {
                    encoder.writeSmallLong(targetNode.getNodeId())
                }
            }
        }
    }

    @JvmRecord
    private data class StatefulSerializers(
        val attributeContainerSerializer: AttributeContainerSerializer,
        val componentSelectorSerializer: Serializer<ComponentSelector?>
    ) {
        companion object {
            fun create(
                capabilitySelectorSerializer: CapabilitySelectorSerializer,
                attributesFactory: AttributesFactory,
                namedObjectInstantiator: NamedObjectInstantiator
            ): StatefulSerializers {
                val deduplicatingAttributeContainerSerializer: AttributeContainerSerializer = DeduplicatingAttributeContainerSerializer(
                    DesugaringAttributeContainerSerializer(
                        attributesFactory,
                        namedObjectInstantiator
                    )
                )
                val deduplicatingComponentSelectorSerializer = DeduplicatingComponentSelectorSerializer(
                    ComponentSelectorSerializer(
                        deduplicatingAttributeContainerSerializer,
                        capabilitySelectorSerializer
                    )
                )

                return StreamingResolutionResultBuilder.StatefulSerializers(
                    deduplicatingAttributeContainerSerializer,
                    deduplicatingComponentSelectorSerializer
                )
            }
        }
    }

    private class GraphFactory(
        private val data: BinaryStore.BinaryData,
        private val cache: Store<GraphStructure>,
        private val failures: MutableList<ModuleVersionResolveException>,
        private val dependencyLockingFailures: MutableSet<UnresolvedDependency>,
        private val deserializerFactory: Supplier<GraphDeserializer>
    ) {
        private val lock = Any()

        fun create(): GraphStructure {
            synchronized(lock) {
                return cache.load(Supplier {
                    try {
                        data.use { reader ->
                            return@load reader.read<GraphStructure>(BinaryStore.ReadAction { decoder: Decoder? ->
                                deserializerFactory.get().deserializeFrom(decoder!!, failures, dependencyLockingFailures)
                            }
                            )
                        }
                    } catch (e: IOException) {
                        throw throwAsUncheckedException(e)
                    }
                })
            }
        }
    }

    private class GraphDeserializer(
        private val componentSelectorSerializer: Serializer<ComponentSelector?>,
        private val attributeContainerSerializer: AttributeContainerSerializer,
        private val graphElementStore: ThisBuildTreeOnlyGraphElementStore,
        private val reasonSerializer: ComponentSelectionReasonSerializer,
        private val componentIdSerializer: Serializer<ComponentIdentifier?>,
        private val capabilitySerializer: ImmutableCapabilitiesSerializer,
        private val moduleVersionIdSerializer: Serializer<ModuleVersionIdentifier?>,
        private val attributeDesugaring: AttributeDesugaring
    ) {
        private val builder = GraphStructureBuilder()
        private var rootNodeId: Long = 0
        private var failureIndex = 0

        fun deserializeFrom(decoder: Decoder, edgeFailures: MutableList<ModuleVersionResolveException>, dependencyLockingFailures: MutableSet<UnresolvedDependency>): GraphStructure {
            var valuesRead = 0
            var type: Byte = -1
            val clock = startTimer()
            try {
                this.rootNodeId = decoder.readSmallLong()
                builder.start(rootNodeId)

                while (true) {
                    type = decoder.readByte()
                    valuesRead++
                    when (type) {
                        COMPONENT -> readComponent(decoder)
                        NODE -> readNode(decoder, edgeFailures, dependencyLockingFailures)
                        END -> {
                            return builder.build()
                        }

                        else -> throw IOException("Unknown value type read from stream: " + type)
                    }
                }
            } catch (e: Exception) {
                throw RuntimeException(
                    ("Problems loading the resolution results (" + clock.elapsed + "). "
                            + "Read " + valuesRead + " values, last was: " + type), e
                )
            }
        }

        @Throws(Exception::class)
        fun readComponent(decoder: Decoder) {
            val id = decoder.readSmallLong()
            val selectionReason = reasonSerializer.read(decoder)
            val repositoryName = decoder.readNullableString()

            val componentIdentifier: ComponentIdentifier?
            val moduleVersionId: ModuleVersionIdentifier?
            if (decoder.readBoolean()) {
                componentIdentifier = componentIdSerializer.read(decoder)
                moduleVersionId = moduleVersionIdSerializer.read(decoder)
            } else {
                val instanceId = decoder.readSmallLong()
                val component = graphElementStore.getComponent(instanceId)
                componentIdentifier = component.getId()
                moduleVersionId = component.getMetadata()!!.getModuleVersionId()
            }

            builder.addComponent(
                id,
                selectionReason,
                repositoryName,
                componentIdentifier!!,
                moduleVersionId!!
            )
        }

        @Throws(Exception::class)
        fun readNode(
            decoder: Decoder,
            edgeFailures: MutableList<ModuleVersionResolveException>,
            dependencyLockingFailures: MutableSet<UnresolvedDependency>
        ) {
            val nodeId = decoder.readSmallLong()
            val ownerId = decoder.readSmallLong()

            val variantName: String?
            val attributes: ImmutableAttributes?
            val rawCapabilities: ImmutableCapabilities?
            var externalVariantId: Long = -1
            if (decoder.readBoolean()) {
                variantName = decoder.readString()
                attributes = attributeContainerSerializer.read(decoder)
                rawCapabilities = capabilitySerializer.read(decoder)
            } else {
                val instanceId = decoder.readSmallLong()
                val variant = graphElementStore.getVariant(instanceId)
                variantName = variant.getMetadata()!!.getName()
                attributes = attributeDesugaring.desugar(variant.getMetadata()!!.getAttributes()!!)
                rawCapabilities = variant.getMetadata()!!.getCapabilities()

                if (decoder.readBoolean()) {
                    externalVariantId = decoder.readSmallLong()
                }
            }

            builder.addNode(
                nodeId,
                ownerId,
                attributes,
                rawCapabilities!!,
                variantName!!,
                externalVariantId
            )

            readEdges(decoder, edgeFailures)

            // TODO: These failures should be injected way earlier while validating the graph
            // rather than side-loading them when building the graph structure representation.
            if (nodeId == rootNodeId) {
                for (failure in dependencyLockingFailures) {
                    val failureSelector = failure.getSelector()
                    val failureComponentSelector = newSelector(failureSelector.getModule(), failureSelector.getVersion())
                    builder.addFailedEdge(
                        failureComponentSelector,
                        true,
                        ComponentSelectionReasons.of(DEPENDENCY_LOCKING),
                        ModuleVersionResolveException(failureComponentSelector, org.gradle.internal.Factory { "Dependency lock state out of date" }, failure.getProblem())
                    )
                }
            }
        }

        @Throws(Exception::class)
        fun readEdges(decoder: Decoder, edgeFailures: MutableList<ModuleVersionResolveException>) {
            val edges = decoder.readSmallInt()
            for (i in 0..<edges) {
                val targetCount = decoder.readSmallInt()
                if (targetCount == -1) {
                    val constraint = decoder.readBoolean()
                    val selector = componentSelectorSerializer.read(decoder)
                    val reason = reasonSerializer.read(decoder)
                    builder.addFailedEdge(
                        selector!!,
                        constraint,
                        reason,
                        edgeFailures.get(failureIndex++)
                    )
                } else if (targetCount != 0) {
                    val constraint = decoder.readBoolean()
                    val selector = componentSelectorSerializer.read(decoder)
                    for (j in 0..<targetCount) {
                        val targetNodeId = decoder.readSmallLong()
                        builder.addSuccessfulEdge(
                            selector!!,
                            constraint,
                            targetNodeId
                        )
                    }
                }
            }
        }

        companion object {
            private val DEPENDENCY_LOCKING = DefaultComponentSelectionDescriptor(ComponentSelectionCause.CONSTRAINT, Describables.of("Dependency locking"))
        }
    }

    companion object {
        private const val COMPONENT: Byte = 1
        private const val NODE: Byte = 2
        private const val END: Byte = 3

        // TODO: Would probably be better if a node already knew its real capabilities instead of
        // correcting for them at the API surface.
        private fun capabilitiesFor(capabilities: ImmutableCapabilities, component: ComponentGraphResolveState): ImmutableList<out Capability> {
            if (!capabilities.asSet().isEmpty()) {
                return capabilities.asSet().asList()
            }

            return ImmutableList.of<DefaultImmutableCapability>(DefaultImmutableCapability.defaultCapabilityForComponent(component.getMetadata()!!.getModuleVersionId()!!))
        }
    }
}
