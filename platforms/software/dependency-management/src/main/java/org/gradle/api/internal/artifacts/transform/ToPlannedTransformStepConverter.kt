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

import org.gradle.execution.plan.Node
import org.gradle.execution.plan.ToPlannedNodeConverter
import org.gradle.internal.taskgraph.NodeIdentity
import org.gradle.operations.dependencies.transforms.PlannedTransformStepIdentity
import org.jspecify.annotations.NullMarked

/**
 * A converter from [TransformStepNode] to [PlannedNode].
 */
@NullMarked
class ToPlannedTransformStepConverter : ToPlannedNodeConverter {
    override fun getSupportedNodeType(): Class<out Node> {
        return TransformStepNode::class.java
    }

    override fun getConvertedNodeType(): NodeIdentity.NodeType {
        return NodeIdentity.NodeType.TRANSFORM_STEP
    }

    override fun getNodeIdentity(node: Node): PlannedTransformStepIdentity {
        val transformStepNode = node as TransformStepNode
        return transformStepNode.getNodeIdentity()
    }

    override fun isInSamePlan(node: Node): Boolean {
        return true
    }

    override fun convert(node: Node, nodeDependencies: MutableList<out NodeIdentity>): DefaultPlannedTransformStep {
        val transformStepNode = node as TransformStepNode
        return DefaultPlannedTransformStep(transformStepNode.getNodeIdentity(), nodeDependencies)
    }

    override fun toString(): String {
        return "ToPlannedTransformStepConverter(" + getSupportedNodeType().getSimpleName() + ")"
    }
}
