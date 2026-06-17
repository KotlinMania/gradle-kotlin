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

import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.scan.NotUsedByScanPlugin

/**
 * A [BuildOperationType] for executing a scheduled transform step.
 *
 *
 * Encompasses the execution of a transform step node.
 * The node runs only one transform, though possibly on multiple files.
 *
 * @since 8.1
 */
class ExecutePlannedTransformStepBuildOperationType : BuildOperationType<ExecutePlannedTransformStepBuildOperationType.Details?, ExecutePlannedTransformStepBuildOperationType.Result?> {
    interface Details {
        /**
         * The identity of the transform step executed in this operation.
         */
        val plannedTransformStepIdentity: PlannedTransformStepIdentity?

        /**
         * Class of the transformer action implementation provided as part of transform registration.
         */
        val transformActionClass: Class<*>?

        @get:NotUsedByScanPlugin
        val transformerName: String?

        @get:NotUsedByScanPlugin
        val subjectName: String?
    }

    interface Result
}
