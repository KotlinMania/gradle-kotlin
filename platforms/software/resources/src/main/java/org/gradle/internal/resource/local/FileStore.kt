/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.resource.local

import org.gradle.api.Action
import java.io.File

/**
 * An indexed store that maps a key to a file or directory.
 *
 * Most implementations do not provide locking, which must be coordinated by the caller.
 */
interface FileStore<K> {
    /**
     * Moves the given file into the store.
     */
    @Throws(FileStoreException::class)
    fun move(key: K?, source: File?): LocallyAvailableResource?

    /**
     * Adds an entry to the store, using the given action to produce the file.
     *
     * @throws FileStoreAddActionException When the action fails
     * @throws FileStoreException On other failures
     */
    @Throws(FileStoreException::class)
    fun add(key: K?, addAction: Action<File?>?): LocallyAvailableResource?
}
