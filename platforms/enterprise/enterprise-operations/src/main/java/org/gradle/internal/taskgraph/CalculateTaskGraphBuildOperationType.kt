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
package org.gradle.internal.taskgraph

import org.gradle.internal.operations.BuildOperationType

/**
 * Computing the execution plan for a given build in the build tree based on the inputs and build configuration.
 *
 *
 * Despite the name, the execution plan is not limited to tasks. It can also include artifact transforms.
 * See [NodeIdentity.NodeType] for the full list of possible node types in the plan.
 *
 * @since 4.0
 */
class CalculateTaskGraphBuildOperationType private constructor() : BuildOperationType<CalculateTaskGraphBuildOperationType.Details?, CalculateTaskGraphBuildOperationType.Result?> {
    /**
     * An identifiable node in the execution graph with its dependencies.
     *
     * @since 8.1
     */
    interface PlannedNode {
        val nodeIdentity: NodeIdentity?

        /**
         * Returns the dependencies of this node.
         *
         *
         * Note that dependencies are not necessarily located in the same execution plan.
         */
        val nodeDependencies: MutableList<out NodeIdentity>?
    }

    /**
     * Identity of a local task node in the execution graph.
     *
     * @since 6.2
     */
    interface TaskIdentity : NodeIdentity {
        /**
         * The path of the build this task belongs to.
         */
        val buildPath: String?

        /**
         * The path of this task in the build.
         */
        val taskPath: String?

        /**
         * See `org.gradle.api.internal.project.taskfactory.TaskIdentity#uniqueId`.
         */
        val taskId: Long
    }

    /**
     * A [PlannedNode] for a task in the execution graph.
     *
     * @since 6.2
     */
    interface PlannedTask : PlannedNode {
        val task: TaskIdentity?

        @get:Deprecated("Use {@link #getNodeDependencies()} instead.")
        val dependencies: MutableList<TaskIdentity>?

        val mustRunAfter: MutableList<TaskIdentity>?

        val shouldRunAfter: MutableList<TaskIdentity>?

        val finalizedBy: MutableList<TaskIdentity>?
    }

    interface Details {
        /**
         * The build path the calculated task graph belongs too.
         * Never null.
         *
         * @since 4.5
         */
        val buildPath: String?
    }

    interface Result {
        /**
         * Lexicographically sorted.
         * Never null.
         * Never contains duplicates.
         */
        val requestedTaskPaths: MutableList<String>?

        /**
         * Lexicographically sorted.
         * Never null.
         * Never contains duplicates.
         */
        val excludedTaskPaths: MutableList<String>?

        /**
         * Capturing task execution plan details.
         *
         * @since 6.2
         */
        val taskPlan: MutableList<PlannedTask>?

        /**
         * Returns an execution plan consisting of nodes of the given types.
         *
         *
         * The graph is represented as a list of nodes (in no particular order) and their [dependencies][PlannedNode.getNodeDependencies].
         * The dependencies of each node are the closest nodes in the plan whose type is in the given set.
         *
         * @param types an inclusive range-subset of node types starting with the [TASK][NodeIdentity.NodeType.TASK], such as `[TASK, TRANSFORM_STEP]`
         * @since 8.1
         */
        fun getExecutionPlan(types: MutableSet<NodeIdentity.NodeType>): MutableList<PlannedNode>?
    }
}
