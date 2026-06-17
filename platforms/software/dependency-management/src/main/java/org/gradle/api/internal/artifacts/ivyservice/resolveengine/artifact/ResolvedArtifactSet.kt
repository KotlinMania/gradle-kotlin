/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.internal.artifacts.transform.TransformStepNode
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation

/**
 * A container for a set of files or artifacts. May or may not be immutable, and may require building and further resolution.
 *
 *
 * There are no guarantees of uniqueness of visited artifacts. This would be better named `ResolvedArtifactCollection`.
 */
interface ResolvedArtifactSet : TaskDependencyContainer {
    /**
     * Visits the contents of the set, adding any remaining work to finalise the set of artifacts to the given queue.
     */
    fun visit(visitor: Visitor)

    fun visitTransformSources(visitor: TransformSourceVisitor)

    interface TransformSourceVisitor {
        fun visitArtifact(artifact: ResolvableArtifact)

        fun visitTransform(source: TransformStepNode)
    }

    /**
     * Visits the external artifacts of this set.
     */
    fun visitExternalArtifacts(visitor: Action<ResolvableArtifact>)

    interface Artifacts {
        /**
         * Called before any of the other methods are called on this set.
         */
        fun prepareForVisitingIfNotAlready() {}

        /**
         * Queues up any work still remaining to finalize the set of artifacts contained in this set.
         */
        fun startFinalization(actions: BuildOperationQueue<RunnableBuildOperation>, requireFiles: Boolean)

        /**
         * Invoked once all async work as completed, to visit the final result. The result is visited using the current thread and in the relevant order.
         */
        fun visit(visitor: ArtifactVisitor)
    }

    /**
     * A listener that is notified as artifacts are made available while visiting the contents of a set. Implementations must be thread safe as they are notified from multiple threads concurrently.
     */
    interface Visitor {
        /**
         * Called prior to scheduling resolution of a set of the given type. Should be called in result order.
         */
        fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType?

        /**
         * Visits zero or more artifacts.
         */
        fun visitArtifacts(artifacts: Artifacts)
    }

    companion object {
        @JvmField
        val EMPTY: ResolvedArtifactSet = object : ResolvedArtifactSet {
            override fun visit(visitor: Visitor) {
            }

            override fun visitTransformSources(visitor: TransformSourceVisitor) {
            }

            override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact>) {
            }

            override fun visitDependencies(context: TaskDependencyResolveContext) {
            }
        }
    }
}
