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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import org.gradle.api.Action
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation

/**
 * A wrapper that prepares artifacts in parallel when visiting the delegate.
 * This is done by collecting all artifacts to prepare and/or visit in a first step.
 * The collected artifacts are prepared in parallel and subsequently visited in sequence.
 */
abstract class ParallelResolveArtifactSet {
    abstract fun visit(visitor: ArtifactVisitor)

    private class EmptySet : ParallelResolveArtifactSet() {
        override fun visit(visitor: ArtifactVisitor) {
        }
    }

    private class VisitingSet(private val artifacts: ResolvedArtifactSet, private val buildOperationProcessor: BuildOperationExecutor) : ParallelResolveArtifactSet() {
        override fun visit(visitor: ArtifactVisitor) {
            // Start preparing the result
            val visitAction: StartVisitAction = VisitingSet.StartVisitAction(visitor)

            // TODO: Ideally we'd execute this work on an unconstrained executor, allowing us to download
            // more artifacts in parallel than the number of worker leases. However, if there are artifact
            // transforms in this artifact set that have not yet executed, they will execute here on-demand.
            // We need a way to split the downloading work and transform computations into separate queues,
            // potentially allowing `Artifact#startFinalization` to submit work to separate queues -- one for
            // CPU-bound work and one for IO-bound work.
            buildOperationProcessor.runAll<RunnableBuildOperation>(visitAction)

            // Now visit the result in order
            visitAction.visitResults()
        }

        private inner class StartVisitAction(private val visitor: ArtifactVisitor) : Action<BuildOperationQueue<RunnableBuildOperation?>?>, ResolvedArtifactSet.Visitor {
            private val results: MutableList<ResolvedArtifactSet.Artifacts> = ArrayList<ResolvedArtifactSet.Artifacts>()
            private var queue: BuildOperationQueue<RunnableBuildOperation>? = null

            override fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType {
                return visitor.prepareForVisit(source)
            }

            override fun visitArtifacts(artifacts: ResolvedArtifactSet.Artifacts) {
                artifacts.startFinalization(queue!!, visitor.requireArtifactFiles())
                results.add(artifacts)
            }

            override fun execute(buildOperationQueue: BuildOperationQueue<RunnableBuildOperation>) {
                this.queue = buildOperationQueue
                artifacts.visit(this)
            }

            fun visitResults() {
                for (result in results) {
                    result.visit(visitor)
                }
            }
        }
    }

    companion object {
        private val EMPTY = EmptySet()

        @JvmStatic
        fun wrap(artifacts: ResolvedArtifactSet, buildOperationProcessor: BuildOperationExecutor): ParallelResolveArtifactSet {
            if (artifacts === ResolvedArtifactSet.Companion.EMPTY) {
                return EMPTY
            }
            return VisitingSet(artifacts, buildOperationProcessor)
        }
    }
}
