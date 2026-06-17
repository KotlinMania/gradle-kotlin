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

import org.gradle.internal.taskgraph.NodeIdentity
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity
import org.gradle.operations.dependencies.transforms.PlannedTransformStepIdentity
import org.gradle.operations.dependencies.variants.Capability
import org.gradle.operations.dependencies.variants.ComponentIdentifier
import java.util.Objects

class DefaultPlannedTransformStepIdentity(
    val consumerBuildPath: String,
    val consumerProjectPath: String,
    val componentId: ComponentIdentifier,
    val sourceAttributes: MutableMap<String, String>,
    val targetAttributes: MutableMap<String, String>,
    private val capabilities: MutableList<Capability>,
    val artifactName: String,
    private val dependenciesConfigurationIdentity: ConfigurationIdentity?,
    val transformStepNodeId: Long
) : PlannedTransformStepIdentity {
    val nodeType: NodeIdentity.NodeType
        get() = NodeIdentity.NodeType.TRANSFORM_STEP

    override fun getCapabilities(): MutableList<out Capability> {
        return capabilities
    }

    override fun getDependenciesConfigurationIdentity(): ConfigurationIdentity {
        return dependenciesConfigurationIdentity!!
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o !is DefaultPlannedTransformStepIdentity) {
            return false
        }
        val that = o
        return transformStepNodeId == that.transformStepNodeId
    }

    override fun hashCode(): Int {
        return Objects.hash(transformStepNodeId)
    }

    override fun toString(): String {
        return "Transform '" + componentId + "' to " + targetAttributes
    }
}
