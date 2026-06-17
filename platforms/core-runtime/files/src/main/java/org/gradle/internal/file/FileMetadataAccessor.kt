/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.file

import java.io.File

interface FileMetadataAccessor {
    /**
     * Gets the file metadata of a [File].
     *
     *
     * If the type of the file cannot be determined, or is
     * neither [FileType.RegularFile]
     * nor [FileType.Directory],
     * then the file type of the file metadata is of type
     * [FileType.Missing].
     *
     *
     * Such cases include:
     *
     *  * actual missing files
     *  * broken symlinks
     *  * circular symlinks
     *  * named pipes
     *
     */
    fun stat(f: File): FileMetadata?
}
