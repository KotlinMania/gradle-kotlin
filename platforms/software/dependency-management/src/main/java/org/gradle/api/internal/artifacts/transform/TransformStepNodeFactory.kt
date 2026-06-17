/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.internal.id.ConfigurationCacheableIdFactory
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import javax.annotation.concurrent.ThreadSafe

/**
 * Transform step node factory that ensures that unique ids are used correctly
 * when creating new instances and/or loading from the configuration cache.
 */
@ThreadSafe
@ServiceScope(Scope.BuildTree::class)
class TransformStepNodeFactory(private val idFactory: ConfigurationCacheableIdFactory) {
    /**
     * Create an initial transform step node.
     */
    fun createInitial(
        targetComponentVariant: ComponentVariantIdentifier,
        sourceAttributes: AttributeContainer,
        initial: TransformStep,
        artifact: ResolvableArtifact,
        upstreamDependencies: TransformUpstreamDependencies,
        buildOperationRunner: BuildOperationRunner,
        calculatedValueContainerFactory: CalculatedValueContainerFactory
    ): TransformStepNode.InitialTransformStepNode {
        val transformStepNodeId = idFactory.createId()
        return TransformStepNode.InitialTransformStepNode(
            transformStepNodeId,
            targetComponentVariant,
            sourceAttributes,
            initial,
            artifact,
            upstreamDependencies,
            buildOperationRunner,
            calculatedValueContainerFactory
        )
    }

    /**
     * Create an initial transform step node.
     *
     *
     * Should only be used when loading from the configuration cache to set the node id.
     */
    fun recreateInitial(
        transformStepNodeId: Long,
        targetComponentVariant: ComponentVariantIdentifier,
        sourceAttributes: AttributeContainer,
        initial: TransformStep,
        artifact: ResolvableArtifact,
        upstreamDependencies: TransformUpstreamDependencies,
        buildOperationRunner: BuildOperationRunner,
        calculatedValueContainerFactory: CalculatedValueContainerFactory
    ): TransformStepNode.InitialTransformStepNode {
        idFactory.idRecreated()
        return TransformStepNode.InitialTransformStepNode(
            transformStepNodeId,
            targetComponentVariant,
            sourceAttributes,
            initial,
            artifact,
            upstreamDependencies,
            buildOperationRunner,
            calculatedValueContainerFactory
        )
    }

    /**
     * Create a chained transform step node.
     */
    fun createChained(
        targetComponentVariant: ComponentVariantIdentifier,
        sourceAttributes: AttributeContainer,
        current: TransformStep,
        previous: TransformStepNode,
        upstreamDependencies: TransformUpstreamDependencies,
        buildOperationExecutor: BuildOperationRunner,
        calculatedValueContainerFactory: CalculatedValueContainerFactory
    ): TransformStepNode.ChainedTransformStepNode {
        val transformStepNodeId = idFactory.createId()
        return TransformStepNode.ChainedTransformStepNode(
            transformStepNodeId,
            targetComponentVariant,
            sourceAttributes,
            current,
            previous,
            upstreamDependencies,
            buildOperationExecutor,
            calculatedValueContainerFactory
        )
    }

    /**
     * Create a chained transform step node.
     *
     *
     * Should only be used when loading from the configuration cache to set the node id.
     */
    fun recreateChained(
        transformStepNodeId: Long,
        targetComponentVariant: ComponentVariantIdentifier,
        sourceAttributes: AttributeContainer,
        current: TransformStep,
        previous: TransformStepNode,
        upstreamDependencies: TransformUpstreamDependencies,
        buildOperationExecutor: BuildOperationRunner,
        calculatedValueContainerFactory: CalculatedValueContainerFactory
    ): TransformStepNode.ChainedTransformStepNode {
        idFactory.idRecreated()
        return TransformStepNode.ChainedTransformStepNode(
            transformStepNodeId,
            targetComponentVariant,
            sourceAttributes,
            current,
            previous,
            upstreamDependencies,
            buildOperationExecutor,
            calculatedValueContainerFactory
        )
    }
}
