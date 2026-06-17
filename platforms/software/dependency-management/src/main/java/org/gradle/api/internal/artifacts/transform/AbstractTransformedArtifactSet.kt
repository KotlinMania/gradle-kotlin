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
package org.gradle.api.internal.artifacts.transform

import com.google.common.collect.ImmutableList
import org.gradle.api.Action
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.EndCollection
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.tasks.NodeExecutionContext
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.model.CalculatedValueContainer
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.ValueCalculator

/**
 * Transformed artifact set that performs the transform itself when visited.
 */
abstract class AbstractTransformedArtifactSet : TransformedArtifactSet, FileCollectionInternal.Source {
    val result: CalculatedValueContainer<ImmutableList<ResolvedArtifactSet.Artifacts>, CalculateArtifacts>

    constructor(
        componentIdentifier: ComponentIdentifier,
        sourceVariantId: VariantIdentifier,
        delegate: ResolvedArtifactSet,
        targetVariantAttributes: ImmutableAttributes,
        capabilities: ImmutableCapabilities,
        transformChain: TransformChain,
        dependenciesResolver: TransformUpstreamDependenciesResolver,
        calculatedValueContainerFactory: CalculatedValueContainerFactory
    ) {
        val builder = ImmutableList.builder<BoundTransformStep>()
        transformChain.visitTransformSteps(Action { step: TransformStep? -> builder.add(BoundTransformStep(step!!, dependenciesResolver.dependenciesFor(componentIdentifier, step))) })
        val steps = builder.build()
        this.result = calculatedValueContainerFactory.create<ImmutableList<ResolvedArtifactSet.Artifacts>, CalculateArtifacts>(
            Describables.of(componentIdentifier),
            CalculateArtifacts(componentIdentifier, sourceVariantId, delegate, targetVariantAttributes, capabilities, steps)
        )
    }

    constructor(result: CalculatedValueContainer<ImmutableList<ResolvedArtifactSet.Artifacts>, CalculateArtifacts>) {
        this.result = result
    }

    override fun visit(visitor: ResolvedArtifactSet.Visitor) {
        val visitType = visitor.prepareForVisit(this)
        if (visitType == FileCollectionStructureVisitor.VisitType.NoContents) {
            visitor.visitArtifacts(EndCollection(this))
            return
        }

        // Calculate the artifacts now
        result.finalizeIfNotAlready()
        for (artifacts in result.get()) {
            artifacts.prepareForVisitingIfNotAlready()
            visitor.visitArtifacts(artifacts)
        }
        // Need to fire an "end collection" event. Should clean this up so it is not necessary
        visitor.visitArtifacts(EndCollection(this))
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
        result.visitDependencies(context)
    }

    override fun visitTransformSources(visitor: ResolvedArtifactSet.TransformSourceVisitor) {
        // Should never be called
        throw IllegalStateException()
    }

    override fun visitExternalArtifacts(visitor: Action<ResolvableArtifact>) {
        // Should never be called
        throw IllegalStateException()
    }

    class CalculateArtifacts(
        val ownerId: ComponentIdentifier,
        val sourceVariantId: VariantIdentifier,
        val delegate: ResolvedArtifactSet,
        val targetVariantAttributes: ImmutableAttributes,
        val capabilities: ImmutableCapabilities,
        val steps: ImmutableList<BoundTransformStep>
    ) : ValueCalculator<ImmutableList<ResolvedArtifactSet.Artifacts>> {
        override fun visitDependencies(context: TaskDependencyResolveContext) {
            for (step in steps) {
                context.add(step.getUpstreamDependencies())
            }
        }

        override fun calculateValue(context: NodeExecutionContext): ImmutableList<ResolvedArtifactSet.Artifacts> {
            // Isolate the transform parameters, if not already done
            for (step in steps) {
                step.getTransformStep().isolateParametersIfNotAlready()
                step.getUpstreamDependencies().finalizeIfNotAlready()
            }

            val builder = ImmutableList.builderWithExpectedSize<ResolvedArtifactSet.Artifacts>(1)
            delegate.visit(TransformingAsyncArtifactListener(steps, targetVariantAttributes, capabilities, builder))
            return builder.build()
        }
    }
}
