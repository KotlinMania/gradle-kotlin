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
package org.gradle.internal.nativeintegration.filesystem.services

import org.gradle.internal.UncheckedException
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.FileMetadataAccessor
import org.gradle.internal.file.impl.DefaultFileMetadata.Companion.directory
import org.gradle.internal.file.impl.DefaultFileMetadata.Companion.file
import org.gradle.internal.file.impl.DefaultFileMetadata.Companion.missing
import java.io.File
import java.io.IOException

class FallbackFileMetadataAccessor : FileMetadataAccessor {
    override fun stat(f: File): FileMetadata? {
        if (!f.exists()) {
            return missing(FileMetadata.AccessType.DIRECT)
        }
        if (f.isDirectory()) {
            return directory(FileMetadata.AccessType.DIRECT)
        }
        if (f.isFile()) {
            return file(f.lastModified(), f.length(), FileMetadata.AccessType.DIRECT)
        }
        throw UncheckedException.throwAsUncheckedException(IOException("Unsupported file type for " + f.getAbsolutePath()), true)
    }
}
