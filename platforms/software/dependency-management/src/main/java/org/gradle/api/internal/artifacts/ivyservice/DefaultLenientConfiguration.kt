/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.UnresolvedDependency
import org.gradle.api.internal.artifacts.DefaultResolvedDependency
import org.gradle.api.internal.artifacts.configurations.ResolutionHost
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.LocalDependencyFiles
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSetResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.resolve.ArtifactResolveException
import java.util.Deque
import java.util.LinkedList
import java.util.function.Supplier

class DefaultLenientConfiguration(
    private val resolutionHost: ResolutionHost,
    private val graphResults: VisitedGraphResults,
    private val artifactResults: VisitedArtifactSet,
    private val graphStructureSupplier: Supplier<GraphStructure>,
    private val artifactSetResolver: ResolvedArtifactSetResolver,
    val implicitSelectionSpec: ArtifactSelectionSpec,
    private val buildOperationExecutor: BuildOperationExecutor
) : LenientConfigurationInternal {
    // Selected for the configuration
    private var artifactsForThisConfiguration: SelectedArtifactResults? = null
    private var root: DefaultResolvedDependency? = null
        get() {
            if (field == null) {
                val structure = graphStructureSupplier.get()
                val nodes = structure.nodes()
                val components = structure.components()
                val edges = structure.edges()
                val artifactsByNodeId = this.selectedArtifacts

                val allNodes: MutableList<DefaultResolvedDependency> =
                    ArrayList<DefaultResolvedDependency>(nodes.count())
                for (i in 0..<nodes.count()) {
                    val owner = nodes.owner(i)
                    val artifacts = artifactsByNodeId.getArtifactsWithId(i)
                    val node = DefaultResolvedDependency(
                        nodes.variantName(i),
                        components.moduleVersionId(owner),
                        buildOperationExecutor,
                        resolutionHost
                    )
                    node.addModuleArtifacts(artifacts)
                    allNodes.add(node)
                }

                for (i in 0..<nodes.count()) {
                    val parent = allNodes.get(i)
                    for (e in edges.start(i)..<edges.end(i)) {
                        if (!edges.constraint(e)) {
                            val target = edges.targetNode(e)
                            if (target != -1) {
                                // Resolved/LenientConfiguration only expose
                                // successful, non-constraint edges.
                                parent.addChild(allNodes.get(target))
                            }
                        }
                    }
                }

                field = allNodes.get(nodes.root())
            }
            return field
        }

    private val selectedArtifacts: SelectedArtifactResults
        get() {
            if (artifactsForThisConfiguration == null) {
                artifactsForThisConfiguration = artifactResults.selectLegacy(implicitSelectionSpec)
            }
            return artifactsForThisConfiguration
        }

    override fun getUnresolvedModuleDependencies(): MutableSet<UnresolvedDependency?> {
        return graphResults.getUnresolvedDependencies()
    }

    override fun getFirstLevelModuleDependencies(): ImmutableSet<ResolvedDependency?> {
        return this.root!!.getChildren()
    }

    override fun getAllModuleDependencies(): MutableSet<ResolvedDependency?> {
        val resolvedElements: MutableSet<ResolvedDependency?> = LinkedHashSet<ResolvedDependency?>()
        val workQueue: Deque<ResolvedDependency> = LinkedList<ResolvedDependency>(this.root!!.getChildren())
        while (!workQueue.isEmpty()) {
            val item = workQueue.removeFirst()
            if (resolvedElements.add(item)) {
                val children: MutableSet<ResolvedDependency?> = item.getChildren()
                workQueue.addAll(children)
            }
        }
        return resolvedElements
    }

    override fun getArtifacts(): MutableSet<ResolvedArtifact?> {
        val visitor = LenientArtifactCollectingVisitor()
        artifactSetResolver.visitArtifacts(this.selectedArtifacts.artifacts, visitor, resolutionHost)
        resolutionHost.rethrowFailuresAndReportProblems("artifacts", visitor.getFailures())
        return visitor.artifacts
    }

    private class LenientArtifactCollectingVisitor : ArtifactVisitor {
        private val artifacts: MutableSet<ResolvedArtifact?> = LinkedHashSet<ResolvedArtifact?>()
        private var failures: MutableList<Throwable?>? = null

        override fun visitArtifact(
            artifactSetName: DisplayName,
            sourceVariantId: VariantIdentifier,
            attributes: ImmutableAttributes,
            capabilities: ImmutableCapabilities,
            artifact: ResolvableArtifact
        ) {
            try {
                val resolvedArtifact = artifact.toPublicView()

                // Attempt to download the file
                resolvedArtifact.getFile()

                // Only record the artifact if the file is accessible
                artifacts.add(resolvedArtifact)
            } catch (e: ArtifactResolveException) {
                // Ignore
                // TODO: Would be nice to not use exceptions for control flow
            } catch (e: Exception) {
                visitFailure(e)
            }
        }

        override fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType {
            if (source is LocalDependencyFiles) {
                return FileCollectionStructureVisitor.VisitType.NoContents
            }
            return FileCollectionStructureVisitor.VisitType.Visit
        }

        override fun requireArtifactFiles(): Boolean {
            // This is false so that we can download the artifact in `visitArtifact` and ignore missing files
            return false
        }

        override fun visitFailure(failure: Throwable) {
            if (failures == null) {
                failures = ArrayList<Throwable?>()
            }
            failures!!.add(failure)
        }

        fun getFailures(): MutableList<Throwable?> {
            return (if (failures != null) failures else kotlin.collections.mutableListOf<kotlin.Throwable?>())!!
        }
    }
}
