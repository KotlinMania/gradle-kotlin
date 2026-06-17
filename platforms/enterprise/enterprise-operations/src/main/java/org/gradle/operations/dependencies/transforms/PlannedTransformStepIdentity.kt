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
package org.gradle.operations.dependencies.transforms

import org.gradle.internal.taskgraph.NodeIdentity

/**
 * Identity of a transform step node in an execution plan.
 *
 * @since 8.1
 */
interface PlannedTransformStepIdentity : NodeIdentity {
    /**
     * Path of an included build of the consumer project.
     */
    val consumerBuildPath: String?

    /**
     * Consumer project path within the build.
     */
    val consumerProjectPath: String?

    /**
     * The component identifier of the transformed artifact.
     */
    val componentId: ComponentIdentifier?

    /**
     * Full set of attributes of the artifact before the transform.
     */
    val sourceAttributes: MutableMap<String, String>?

    /**
     * Target attributes of the transformed artifact.
     *
     *
     * The attributes include all source attributes of the artifact before the transform,
     * values for some of which have been changed by the transform.
     */
    val targetAttributes: MutableMap<String, String>?

    /**
     * Capabilities of the variant of the transformed artifact.
     *
     *
     * Artifact transforms can only change attributes, so the capabilities remain unchanged throughout the transform chain.
     */
    val capabilities: MutableList<out Capability>?

    /**
     * Name of the source artifact being transformed.
     *
     *
     * This name remains the same throughout the transform chain.
     */
    val artifactName: String?

    /**
     * Configuration that contains transitive dependencies of the input artifact.
     *
     *
     * Present only if the artifact transform implementation declares a dependency on the input artifact dependencies.
     */
    val dependenciesConfigurationIdentity: ConfigurationIdentity?

    /**
     * An opaque identifier distinguishes between different transform step nodes in case other identity properties are the same.
     */
    val transformStepNodeId: Long
}
