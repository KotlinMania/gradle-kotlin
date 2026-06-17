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
package org.gradle.api.internal.artifacts.transform

import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.ints.IntStack
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolutionStrategy
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.ResolverResults
import org.gradle.api.internal.artifacts.configurations.ResolutionBackedFileCollection
import org.gradle.api.internal.artifacts.configurations.ResolutionHost
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.lambdas.SerializableLambdas
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.internal.tasks.WorkNodeAction
import org.gradle.api.specs.Spec
import org.gradle.execution.plan.PostExecutionNodeAwareActionNode
import org.gradle.execution.plan.TaskNode
import org.gradle.execution.plan.TaskNodeFactory
import org.gradle.internal.Describables
import org.gradle.internal.Try
import org.gradle.internal.model.CalculatedValue
import org.gradle.internal.model.CalculatedValueContainer
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.ValueCalculator
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity
import java.util.Optional

class DefaultTransformUpstreamDependenciesResolver : TransformUpstreamDependenciesResolver {
    private val resolutionHost: ResolutionHost
    private val configurationIdentity: ConfigurationIdentity?
    private val requestAttributes: ImmutableAttributes
    private val artifactDependencySortOrder: ResolutionStrategy.SortOrder

    private val initialVisitedGraph: VisitedGraphResults
    private val initialVisitedArtifacts: VisitedArtifactSet
    private val completeGraphResults: CalculatedValue<VisitedGraphResults>
    private val completeArtifactResults: CalculatedValue<VisitedArtifactSet>

    // Services
    private val owner: DomainObjectContext
    private val calculatedValueContainerFactory: CalculatedValueContainerFactory
    private val attributesFactory: AttributesFactory
    private val taskDependencyFactory: TaskDependencyFactory

    /**
     * Construct a resolver used to resolve build dependencies of upstream dependencies for an artifact transform.
     *
     *
     * The `fullGraphResults` are required to calculate the true build dependencies of transforms
     * with dependencies, as the incomplete graph used to initially determine upstream transforms does
     * not represent the final dependency graph.
     *
     *
     * See [org.gradle.integtests.resolve.transform.ArtifactTransformWithDependenciesParallelIntegrationTest]
     * for the test that exercises the scenario that necessitates this behavior.
     */
    constructor(
        resolutionHost: ResolutionHost,
        configurationIdentity: ConfigurationIdentity?,
        requestAttributes: ImmutableAttributes,
        artifactDependencySortOrder: ResolutionStrategy.SortOrder,

        partialVisitedGraph: VisitedGraphResults,
        partialVisitedArtifacts: VisitedArtifactSet,
        fullGraphResults: CalculatedValue<ResolverResults>,  // Services

        owner: DomainObjectContext,
        calculatedValueContainerFactory: CalculatedValueContainerFactory,
        attributesFactory: AttributesFactory,
        taskDependencyFactory: TaskDependencyFactory
    ) {
        this.resolutionHost = resolutionHost
        this.configurationIdentity = configurationIdentity
        this.requestAttributes = requestAttributes
        this.artifactDependencySortOrder = artifactDependencySortOrder

        this.initialVisitedArtifacts = partialVisitedArtifacts
        this.initialVisitedGraph = partialVisitedGraph
        this.completeGraphResults = calculatedValueContainerFactory.create<VisitedGraphResults, ValueCalculator<out VisitedGraphResults>>(
            Describables.of("complete graph results for", resolutionHost.getDisplayName()),
            ValueCalculator { context: NodeExecutionContext? ->
                // TODO: We should acquire the project lock here, since this will resolve a configuration, which requires a project lock.
                fullGraphResults.finalizeIfNotAlready()
                fullGraphResults.get().visitedGraph
            })
        this.completeArtifactResults = calculatedValueContainerFactory.create<VisitedArtifactSet, ValueCalculator<out VisitedArtifactSet>>(
            Describables.of("complete artifact results for", resolutionHost.getDisplayName()),
            ValueCalculator { context: NodeExecutionContext? ->
                // TODO: We should acquire the project lock here, since this will resolve a configuration, which requires a project lock.
                fullGraphResults.finalizeIfNotAlready()
                fullGraphResults.get().visitedArtifacts
            })

        this.owner = owner
        this.attributesFactory = attributesFactory
        this.calculatedValueContainerFactory = calculatedValueContainerFactory
        this.taskDependencyFactory = taskDependencyFactory
    }

    /**
     * Construct a resolver used to resolve the complete set of upstream dependencies for an artifact transform.
     */
    constructor(
        resolutionHost: ResolutionHost,
        configurationIdentity: ConfigurationIdentity?,
        requestAttributes: ImmutableAttributes,
        artifactDependencySortOrder: ResolutionStrategy.SortOrder,

        visitedGraph: VisitedGraphResults,
        visitedArtifacts: VisitedArtifactSet,  // Services

        owner: DomainObjectContext,
        calculatedValueContainerFactory: CalculatedValueContainerFactory,
        attributesFactory: AttributesFactory,
        taskDependencyFactory: TaskDependencyFactory
    ) {
        this.resolutionHost = resolutionHost
        this.configurationIdentity = configurationIdentity
        this.requestAttributes = requestAttributes
        this.artifactDependencySortOrder = artifactDependencySortOrder

        this.initialVisitedGraph = visitedGraph
        this.initialVisitedArtifacts = visitedArtifacts
        this.completeGraphResults = calculatedValueContainerFactory.create<VisitedGraphResults, ValueCalculator<out VisitedGraphResults>>(
            Describables.of("complete graph results for", resolutionHost.getDisplayName()),
            ValueCalculator { context: NodeExecutionContext? -> visitedGraph })
        this.completeArtifactResults = calculatedValueContainerFactory.create<VisitedArtifactSet, ValueCalculator<out VisitedArtifactSet>>(
            Describables.of("complete artifact results for", resolutionHost.getDisplayName()),
            ValueCalculator { context: NodeExecutionContext? -> visitedArtifacts })

        this.owner = owner
        this.attributesFactory = attributesFactory
        this.calculatedValueContainerFactory = calculatedValueContainerFactory
        this.taskDependencyFactory = taskDependencyFactory
    }

    override fun dependenciesFor(componentId: ComponentIdentifier, transformStep: TransformStep): TransformUpstreamDependencies {
        if (!transformStep.requiresDependencies()) {
            return NO_DEPENDENCIES
        }
        return DefaultTransformUpstreamDependenciesResolver.TransformUpstreamDependenciesImpl(
            componentId,
            configurationIdentity,
            transformStep,
            calculatedValueContainerFactory,
            initialVisitedGraph,
            initialVisitedArtifacts
        )
    }

    private fun getCompleteTransformDependencies(componentId: ComponentIdentifier, fromAttributes: ImmutableAttributes): FileCollectionInternal {
        completeGraphResults.finalizeIfNotAlready()
        completeArtifactResults.finalizeIfNotAlready()

        val selectedArtifacts = selectDependencyArtifacts(
            componentId,
            fromAttributes,
            completeGraphResults.get(),
            completeArtifactResults.get()
        )

        return ResolutionBackedFileCollection(
            selectedArtifacts,
            false,
            resolutionHost,
            taskDependencyFactory
        )
    }

    private fun selectDependencyArtifacts(
        componentId: ComponentIdentifier,
        fromAttributes: ImmutableAttributes,
        visitedGraph: VisitedGraphResults,
        visitedArtifacts: VisitedArtifactSet
    ): SelectedArtifactSet {
        val dependencyComponents: MutableSet<ComponentIdentifier> = computeDependencies(componentId, visitedGraph)
        val filter: Spec<ComponentIdentifier?> = SerializableLambdas.spec<ComponentIdentifier>(SerializableLambdas.SerializableSpec { o: Any? -> dependencyComponents.contains(o) })

        val fullAttributes = attributesFactory.concat(requestAttributes, fromAttributes)
        return visitedArtifacts.select(
            ArtifactSelectionSpec(
                fullAttributes, filter, false, false, artifactDependencySortOrder
            )
        )
    }

    /**
     * Represents a work node that prepares the upstream dependencies of a particular transform applied to a particular artifact.
     * This is a separate node so that this work can access project state to do the resolution and to discover additional dependencies for the transform
     * during resolution of upstream dependencies. It also allows the work of resolution to be attributed separately to the work of the transform.
     */
    abstract class FinalizeTransformDependencies : ValueCalculator<TransformDependencies> {
        abstract fun selectedArtifacts(): FileCollection

        override fun calculateValue(context: NodeExecutionContext): TransformDependencies {
            val files = selectedArtifacts()
            // Trigger resolution, including any failures
            files.getFiles()
            return DefaultTransformDependencies(files)
        }
    }

    /**
     * A work node used in builds where the upstream dependencies must be resolved. This implementation is not used when the work graph is loaded from the configuration cache,
     * as the dependencies have already been resolved in that case.
     */
    inner class FinalizeTransformDependenciesFromSelectedArtifacts(
        private val componentId: ComponentIdentifier,
        private val fromAttributes: ImmutableAttributes,
        private val initialVisitedGraph: VisitedGraphResults,
        private val initialVisitedArtifacts: VisitedArtifactSet
    ) : FinalizeTransformDependencies() {
        override fun selectedArtifacts(): FileCollectionInternal {
            return getCompleteTransformDependencies(componentId, fromAttributes)
        }

        override fun usesMutableProjectState(): Boolean {
            return owner.getProject() != null
        }

        override fun getOwningProject(): ProjectInternal {
            return owner.getProject()!!
        }

        override fun getPreExecutionAction(): WorkNodeAction? {
            // TODO: If the initial visited graph/artifacts are from a complete resolution,
            // we do not need to finalize the transform dependencies here, as our dependencies are
            // already derived from a complete graph.

            // Before resolving, need to determine the full set of upstream dependencies that need to be built.
            // The full set is usually known when the work graph is built. However, in certain cases where a project dependency conflicts with an external dependency, this is not known
            // until the full graph resolution, which can happen at execution time.

            return FinalizeTransformDependenciesFromSelectedArtifacts.CalculateFinalDependencies()
        }

        override fun visitDependencies(context: TaskDependencyResolveContext) {
            // If the initial visited graph/artifacts are derived from a partial graph resolution,
            // these dependencies will only represent an approximate set of build dependencies.
            context.add(
                selectDependencyArtifacts(
                    componentId,
                    fromAttributes,
                    initialVisitedGraph,
                    initialVisitedArtifacts
                )
            )
        }

        inner class CalculateFinalDependencies : PostExecutionNodeAwareActionNode {
            val tasks: MutableList<TaskNode> = ArrayList<TaskNode>()

            override fun usesMutableProjectState(): Boolean {
                return this@FinalizeTransformDependenciesFromSelectedArtifacts.usesMutableProjectState()
            }

            override fun getOwningProject(): Project? {
                return this@FinalizeTransformDependenciesFromSelectedArtifacts.getOwningProject()
            }

            override fun run(context: NodeExecutionContext) {
                val taskNodeFactory = context.getService<TaskNodeFactory>(TaskNodeFactory::class.java)
                selectedArtifacts().visitDependencies(CollectingTaskDependencyResolveContext(tasks, taskNodeFactory))
            }

            override fun getPostExecutionNodes(): MutableList<TaskNode> {
                return tasks
            }
        }
    }

    private class CollectingTaskDependencyResolveContext(private val tasks: MutableCollection<TaskNode>, private val taskNodeFactory: TaskNodeFactory) : TaskDependencyResolveContext {
        override fun add(dependency: Any) {
            if (dependency is Task) {
                tasks.add(taskNodeFactory.getNode(dependency)!!)
            }
        }

        override fun visitFailure(failure: Throwable) {
        }

        override fun getTask(): Task? {
            return null
        }
    }

    private inner class TransformUpstreamDependenciesImpl(
        private val componentId: ComponentIdentifier,
        private val configurationIdentity: ConfigurationIdentity?,
        transformStep: TransformStep,
        calculatedValueContainerFactory: CalculatedValueContainerFactory,
        initialVisitedGraph: VisitedGraphResults,
        initialVisitedArtifacts: VisitedArtifactSet
    ) : TransformUpstreamDependencies {
        private val transformDependencies: CalculatedValueContainer<TransformDependencies, FinalizeTransformDependencies>
        private val fromAttributes: ImmutableAttributes

        init {
            this.fromAttributes = transformStep.getFromAttributes()
            this.transformDependencies = calculatedValueContainerFactory.create<TransformDependencies, FinalizeTransformDependencies>(
                Describables.of(
                    "dependencies for",
                    componentId, fromAttributes
                ),
                DefaultTransformUpstreamDependenciesResolver.FinalizeTransformDependenciesFromSelectedArtifacts(
                    componentId,
                    transformStep.getFromAttributes(),
                    initialVisitedGraph,
                    initialVisitedArtifacts
                )
            )
        }

        override fun getConfigurationIdentity(): ConfigurationIdentity? {
            return configurationIdentity
        }

        override fun selectedArtifacts(): FileCollection {
            return getCompleteTransformDependencies(componentId, fromAttributes)
        }

        override fun computeArtifacts(): Try<TransformDependencies?> {
            return transformDependencies.getValue()
        }

        override fun visitDependencies(context: TaskDependencyResolveContext) {
            context.add(transformDependencies)
        }

        override fun finalizeIfNotAlready() {
            transformDependencies.finalizeIfNotAlready()
        }
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultTransformUpstreamDependenciesResolver
        return resolutionHost == that.resolutionHost
    }

    override fun hashCode(): Int {
        return resolutionHost.hashCode()
    }

    companion object {
        val NO_RESULT: TransformDependencies = object : TransformDependencies {
            override fun getFiles(): Optional<FileCollection> {
                return Optional.of<FileCollection>(FileCollectionFactory.empty())
            }
        }
        val NO_DEPENDENCIES: TransformUpstreamDependencies = object : TransformUpstreamDependencies {
            override fun getConfigurationIdentity(): ConfigurationIdentity? {
                return null
            }

            override fun selectedArtifacts(): FileCollection {
                return FileCollectionFactory.empty()
            }

            override fun finalizeIfNotAlready() {
            }

            override fun computeArtifacts(): Try<TransformDependencies?> {
                return Try.successful(NO_RESULT)
            }

            override fun visitDependencies(context: TaskDependencyResolveContext) {
            }
        }

        private fun computeDependencies(componentId: ComponentIdentifier, visitedGraph: VisitedGraphResults): MutableSet<ComponentIdentifier> {
            val graph = visitedGraph.getGraphStructureSource().get()
            val edges = graph.edges()
            val components = graph.components()
            val nodes = graph.nodes()

            val seen: IntSet = IntOpenHashSet()
            val queue: IntStack = IntArrayList()

            // Search through all components to find the target component.
            var targetComponent = -1
            for (i in 0..<components.count()) {
                if (components.id(i) == componentId) {
                    targetComponent = i
                    break
                }
            }

            if (targetComponent == -1) {
                throw AssertionError("Could not find component " + componentId + " in provided results.")
            }

            // TODO: This is not quite desired behavior. The purpose of this class is to resolve all
            //  dependencies of an artifact transform. An artifact transform is derived from the artifacts
            //  of a _node_ of the graph. We are only given `componentId`, the owning component of the node
            //  we're interested in. So, we traverse starting from all nodes of that component since we do
            //  not have enough information to find the node of interest.
            for (i in 0..<nodes.count()) {
                if (nodes.owner(i) == targetComponent) {
                    queue.push(i)
                    seen.add(i)
                }
            }

            val buildDependencies: MutableSet<ComponentIdentifier> = HashSet<ComponentIdentifier>()
            while (!queue.isEmpty()) {
                val node = queue.popInt()
                for (i in edges.start(node)..<edges.end(node)) {
                    val constraint = edges.constraint(i)
                    val targetNodeIndex = edges.targetNode(i)
                    // Only visit hard, non-failing edges
                    if (!constraint && targetNodeIndex != -1 && seen.add(targetNodeIndex)) {
                        val owner = nodes.owner(targetNodeIndex)
                        buildDependencies.add(components.id(owner))
                        queue.push(targetNodeIndex)
                    }
                }
            }
            return buildDependencies
        }
    }
}
