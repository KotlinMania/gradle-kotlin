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
import org.gradle.operations.execution.FilePropertyVisitor

/**
 * Represents the computation of capturing the before execution state and resolving the caching state for transforms.
 *
 *
 * Must occur as a child of [org.gradle.operations.execution.ExecuteWorkBuildOperationType].
 *
 * @since 8.3
 */
class SnapshotTransformInputsBuildOperationType : BuildOperationType<SnapshotTransformInputsBuildOperationType.Details?, SnapshotTransformInputsBuildOperationType.Result?> {
    interface Details

    /**
     * The hashes of the inputs.
     *
     *
     * If the inputs were not snapshotted, all fields are null.
     */
    interface Result {
        /**
         * The overall hash value for the inputs.
         *
         *
         * Null if the overall key was not calculated because the inputs were invalid.
         */
        val hashBytes: ByteArray?

        /**
         * The hash of the classloader that loaded the transform action implementation.
         *
         *
         * Null if the classloader is not managed by Gradle.
         */
        val classLoaderHashBytes: ByteArray?

        /**
         * The class name of the transform's action.
         */
        val implementationClassName: String?

        /**
         * The input value property hashes.
         *
         *
         * key = property name
         *
         *
         * Ordered by key, lexicographically.
         * No null keys or values.
         * Never empty.
         */
        val inputValueHashesBytes: MutableMap<String, ByteArray>?

        /**
         * Traverses the input properties that are file types (e.g. File, FileCollection, FileTree, List of File).
         *
         *
         * If there are no input file properties, visitor will not be called at all.
         *
         *
         * This is using the visitor from [SnapshotTaskInputsBuildOperationType] since there is no difference
         * between tasks and transforms in this regard. Later we can unify the transform and the task build operation type.
         */
        fun visitInputFileProperties(visitor: FilePropertyVisitor)

        /**
         * The names of the output properties.
         *
         *
         * No duplicate values.
         * Ordered lexicographically.
         * Never empty.
         */
        val outputPropertyNames: MutableList<String>?
    }
}
