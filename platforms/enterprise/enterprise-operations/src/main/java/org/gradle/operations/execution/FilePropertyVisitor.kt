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
package org.gradle.operations.execution

/**
 * The consuming visitor for file properties on work.
 *
 *
 * Properties are visited depth-first lexicographical.
 * Roots are visited in semantic order (i.e. the order in which they make up the file collection)
 * Files and directories are depth-first lexicographical.
 *
 *
 * For roots that are a file, they are also visited with [.file].
 *
 * @since 8.3
 */
interface FilePropertyVisitor {
    /**
     * Called once per file property.
     *
     *
     * Only getProperty*() state methods may be called during.
     */
    fun preProperty(state: VisitState)

    /**
     * Called for each root of the current property.
     *
     *
     * [VisitState.getName] and [VisitState.getPath] may be called during.
     */
    fun preRoot(state: VisitState)

    /**
     * Called before entering a directory.
     *
     *
     * [VisitState.getName] and [VisitState.getPath] may be called during.
     */
    fun preDirectory(state: VisitState)

    /**
     * Called when visiting a non-directory file.
     *
     *
     * [VisitState.getName], [VisitState.getPath], [VisitState.getHashBytes] and [VisitState.getLength] may be called during.
     */
    fun file(state: VisitState)

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

    /**
     * Provides information about the current location in the visit.
     *
     *
     * Consumers should expect this to be mutable.
     * Calling any method on this outside of a method that received it has undefined behavior.
     */
    interface VisitState {
        /**
         * Returns the currently visited property name. Each property has a unique name.
         */
        val propertyName: String?

        /**
         * Returns the hash of the currently visited property.
         */
        val propertyHashBytes: ByteArray?

        /**
         * A description of how the current property was fingerprinted.
         *
         *
         * Returns one or more of the values of org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult.FilePropertyAttribute, sorted.
         *
         *
         * This interface does not constrain the compatibility of values.
         * In practice however, such constraints do exist but are managed informally.
         * For example, consumers can assume that both org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult.FilePropertyAttribute.FilePropertyAttribute#DIRECTORY_SENSITIVITY_DEFAULT
         * and org.gradle.api.internal.tasks.BaseSnapshotInputsBuildOperationResult.FilePropertyAttribute#DIRECTORY_SENSITIVITY_IGNORE_DIRECTORIES will not be present.
         * This loose approach is used to allow the various types of normalization supported by Gradle to evolve,
         * and their usage to be conveyed here without changing this interface.
         */
        val propertyAttributes: MutableSet<String>?

        /**
         * Returns the absolute path of the currently visited location.
         */
        val path: String?

        /**
         * Returns the name of the currently visited location, as in [File.getName]
         */
        val name: String?

        /**
         * Returns the normalized content hash of the last visited file.
         *
         *
         * Must not be called when the last visited location was a directory.
         */
        val hashBytes: ByteArray?

        /**
         * Returns the length in bytes of the last visited file.
         *
         *
         * Must not be called when the last visited location was a directory.
         *
         * @since 9.6.0
         */
        val length: Long
    }
}
