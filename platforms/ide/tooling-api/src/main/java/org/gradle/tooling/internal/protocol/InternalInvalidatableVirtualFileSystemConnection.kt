/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.tooling.internal.protocol

/**
 * Mixed into a provider connection, to allow notifying the daemon about changed paths.
 *
 *
 * DO NOT CHANGE THIS INTERFACE - it is part of the cross-version protocol.
 *
 *
 * Consumer compatibility: This method is used by all consumer versions from 6.1.
 *
 * Provider compatibility: This method is implemented by all provider versions from 6.1.
 *
 * @since 6.1
 * @see ConnectionVersion4
 */
interface InternalInvalidatableVirtualFileSystemConnection : InternalProtocolInterface {
    /**
     * Notifies all daemons about file changes made by an external process, like an IDE.
     *
     *
     * The daemons will use this information to update the retained file system state.
     *
     *
     * The paths which are passed in need to be absolute, canonicalized paths.
     * For a delete, the deleted path should be passed.
     * For a rename, the old and the new path should be passed.
     * When creating a new file, the path to the file should be passed.
     *
     *
     * The call is synchronous, i.e. the method ensures that the changed paths are taken into account
     * by the daemon after it returned. This ensures that for every build started
     * after this method has been called knows about the changed paths.
     *
     *
     * If the version of Gradle does not support virtual file system retention (i.e. &lt; 6.1),
     * then the operation is a no-op.
     *
     *
     * Consumer compatibility: This method is used by all consumer versions from 6.1.
     *
     * Provider compatibility: This method is implemented by all provider versions from 6.1.
     *
     * @param changedPaths Absolute paths which have been changed by the external process.
     * @throws UnsupportedVersionException When the target Gradle version is &lt;= 2.5.
     * @throws GradleConnectionException On some other failure using the connection.
     * @since 6.1
     */
    fun notifyDaemonsAboutChangedPaths(changedPaths: MutableList<String?>?, parameters: BuildParameters?)
}
