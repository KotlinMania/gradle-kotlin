/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.api.internal.tasks

import org.gradle.internal.operations.BuildOperationType
import org.gradle.operations.execution.FilePropertyVisitor
import org.gradle.operations.execution.FilePropertyVisitor.VisitState

/**
 * Represents the computation of the task artifact state and the task output caching state.
 *
 *
 * This operation is executed only when the build cache is enabled or when the Develocity plugin is applied.
 * Must occur as a child of [org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType].
 *
 * @since 4.0
 */
class SnapshotTaskInputsBuildOperationType private constructor() : BuildOperationType<SnapshotTaskInputsBuildOperationType.Details, SnapshotTaskInputsBuildOperationType.Result> {
    interface Details

    /**
     * The hashes of the inputs.
     *
     *
     * If the inputs were not snapshotted, all fields are null.
     * This may occur if the task had no outputs.
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
         * The hash of the classloader that loaded the task implementation.
         *
         *
         * Null if the classloader is not managed by Gradle.
         */
        val classLoaderHashBytes: ByteArray?


        /**
         * The hashes of the classloader that loaded each of the task's actions.
         *
         *
         * May contain duplicates.
         * Order corresponds to execution order of the actions.
         * Never empty.
         * May contain nulls (non Gradle managed classloader)
         */
        val actionClassLoaderHashesBytes: MutableList<ByteArray?>?

        /**
         * The class names of each of the task's actions.
         *
         *
         * May contain duplicates.
         * Order corresponds to execution order of the actions.
         * Never empty.
         */
        val actionClassNames: MutableList<String?>?

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
         * Null if the task has no inputs.
         */
        val inputValueHashesBytes: MutableMap<String?, ByteArray?>?

        /**
         * The consuming visitor for file property inputs.
         *
         *
         * Properties are visited depth-first lexicographical.
         * Roots are visited in semantic order (i.e. the order in which they make up the file collection)
         * Files and directories are depth-first lexicographical.
         *
         *
         * For roots that are a file, they are also visited with [.file].
         */
        interface InputFilePropertyVisitor {
            /**
             * Called once per file property.
             *
             *
             * Only getProperty*() state methods may be called during.
             */
            fun preProperty(state: VisitState?)

            /**
             * Called for each root of the current property.
             *
             *
             * [VisitState.getName] and [VisitState.getPath] may be called during.
             */
            fun preRoot(state: VisitState?)

            /**
             * Called before entering a directory.
             *
             *
             * [VisitState.getName] and [VisitState.getPath] may be called during.
             */
            fun preDirectory(state: VisitState?)

            /**
             * Called when visiting a non-directory file.
             *
             *
             * [VisitState.getName], [VisitState.getPath], [VisitState.getHashBytes] and [VisitState.getLength] may be called during.
             */
            fun file(state: VisitState?)

            /**
             * Called when exiting a directory.
             */
            fun postDirectory()

            /**
             * Called when exiting a root.
             */
            fun postRoot()

            /**
             * Called when exiting a property.
             */
            fun postProperty()
        }

        /**
         * Provides information about the current location in the visit.
         *
         *
         * Consumers should expect this to be mutable.
         * Calling any method on this outside of a method that received it has undefined behavior.
         */
        interface VisitState : FilePropertyVisitor.VisitState {
            @get:Deprecated("since 7.3, superseded by {@link #getPropertyAttributes()}")
            val propertyNormalizationStrategyName: String?
        }

        /**
         * Traverses the input properties that are file types (e.g. File, FileCollection, FileTree, List of File).
         *
         *
         * If there are no input file properties, visitor will not be called at all.
         */
        fun visitInputFileProperties(visitor: InputFilePropertyVisitor?)

        @get:Deprecated("Always null, since we don't capture inputs when anything is loaded by an unknown classloader.")
        val inputPropertiesLoadedByUnknownClassLoader: MutableSet<String?>?

        /**
         * The names of the output properties.
         *
         *
         * No duplicate values.
         * Ordered lexicographically.
         * Never empty.
         */
        val outputPropertyNames: MutableList<String?>?
    }
}
