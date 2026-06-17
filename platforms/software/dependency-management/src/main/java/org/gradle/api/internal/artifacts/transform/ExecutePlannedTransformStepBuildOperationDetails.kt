/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.ImmutableMap
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization
import org.gradle.operations.dependencies.transforms.ExecutePlannedTransformStepBuildOperationType

class ExecutePlannedTransformStepBuildOperationDetails(val transformStepNode: TransformStepNode, val transformerName: String, val subjectName: String) :
    ExecutePlannedTransformStepBuildOperationType.Details, CustomOperationTraceSerialization {
    val plannedTransformStepIdentity: PlannedTransformStepIdentity
        get() = transformStepNode.getNodeIdentity()

    val transformActionClass: Class<*>
        get() = transformStepNode.getTransformStep().getTransform().getImplementationClass()

    override fun getCustomOperationTraceSerializableModel(): Any {
        val builder = ImmutableMap.Builder<String, Any>()
        builder.put("plannedTransformStepIdentity", plannedTransformStepIdentity)
        builder.put("transformActionClass", transformActionClass)
        builder.put("transformerName", transformerName)
        builder.put("subjectName", subjectName)
        return builder.build()
    }
}
