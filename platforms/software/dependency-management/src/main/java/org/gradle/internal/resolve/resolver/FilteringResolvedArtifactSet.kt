/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.resolve.resolver

import org.gradle.api.Action
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.transform.TransformStepNode
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.RunnableBuildOperation
import java.util.function.Predicate

/**
 * A [ResolvedArtifactSet] that applies a filter to the artifacts of a delegate [ResolvedArtifactSet].
 *
 *
 * The filter is applied **after** build dependencies are calculated, meaning the filter is not
 * applied to build dependencies. This is because the filter may be a function of the resolved artifact files,
 * which are not known until after build dependencies are executed.
 */
class FilteringResolvedArtifactSet(private val artifacts: ResolvedArtifactSet, private val filter: Predicate<ResolvableArtifact>) : ResolvedArtifactSet {
    override fun visit(visitor: ResolvedArtifactSet.Visitor) {
        artifacts.visit(FilteringVisitor(filter, visitor))
    }

    override fun visitTransformSources(visitor: ResolvedArtifactSet.TransformSourceVisitor) {
        artifacts.visitTransformSources(FilteringTransformSourceVisitor(filter, visitor))
    }

    override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact>) {
        artifacts.visitExternalArtifacts(FilteringArtifactAction(filter, visitor))
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
        // Do not apply exclusions to build dependencies, in order to permit filters that are
        // a function of the artifact files.
        // This means that we might build some filtered artifacts, but we filter those later.
        artifacts.visitDependencies(context)
    }

    private class FilteringArtifactVisitor(private val filter: Predicate<ResolvableArtifact>, private val visitor: ArtifactVisitor) : ArtifactVisitor {
        override fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType {
            return visitor.prepareForVisit(source)
        }

        override fun visitArtifact(
            artifactSetName: DisplayName,
            sourceVariantId: VariantIdentifier,
            attributes: ImmutableAttributes,
            capabilities: ImmutableCapabilities,
            artifact: ResolvableArtifact
        ) {
            if (filter.test(artifact)) {
                visitor.visitArtifact(artifactSetName, sourceVariantId, attributes, capabilities, artifact)
            }
        }

        override fun requireArtifactFiles(): Boolean {
            return visitor.requireArtifactFiles()
        }

        override fun visitFailure(failure: Throwable) {
            visitor.visitFailure(failure)
        }

        override fun endVisitCollection(source: FileCollectionInternal.Source) {
            visitor.endVisitCollection(source)
        }
    }

    private class FilteringArtifacts(private val filter: Predicate<ResolvableArtifact>, private val artifacts: ResolvedArtifactSet.Artifacts) : ResolvedArtifactSet.Artifacts {
        override fun prepareForVisitingIfNotAlready() {
            artifacts.prepareForVisitingIfNotAlready()
        }

        override fun startFinalization(actions: BuildOperationQueue<RunnableBuildOperation>, requireFiles: Boolean) {
            artifacts.startFinalization(actions, requireFiles)
        }

        override fun visit(visitor: ArtifactVisitor) {
            artifacts.visit(FilteringArtifactVisitor(filter, visitor))
        }
    }

    private class FilteringVisitor(private val filter: Predicate<ResolvableArtifact>, private val visitor: ResolvedArtifactSet.Visitor) : ResolvedArtifactSet.Visitor {
        override fun prepareForVisit(source: FileCollectionInternal.Source): FileCollectionStructureVisitor.VisitType {
            return visitor.prepareForVisit(source)
        }

        override fun visitArtifacts(artifacts: ResolvedArtifactSet.Artifacts) {
            visitor.visitArtifacts(FilteringArtifacts(filter, artifacts))
        }
    }

    private class FilteringTransformSourceVisitor(private val filter: Predicate<ResolvableArtifact>, private val visitor: ResolvedArtifactSet.TransformSourceVisitor) :
        ResolvedArtifactSet.TransformSourceVisitor {
        override fun visitArtifact(artifact: ResolvableArtifact) {
            if (filter.test(artifact)) {
                visitor.visitArtifact(artifact)
            }
        }

        override fun visitTransform(source: TransformStepNode) {
            if (filter.test(source.getInputArtifact())) {
                visitor.visitTransform(source)
            }
        }
    }

    private class FilteringArtifactAction(private val filter: Predicate<ResolvableArtifact>, private val visitor: Action<ResolvableArtifact>) : Action<ResolvableArtifact> {
        override fun execute(artifact: ResolvableArtifact) {
            if (filter.test(artifact)) {
                visitor.execute(artifact)
            }
        }
    }
}
