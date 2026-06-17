/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.file.nio

import org.gradle.internal.UncheckedException
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.FileMetadataAccessor
import org.gradle.internal.file.impl.DefaultFileMetadata.Companion.directory
import org.gradle.internal.file.impl.DefaultFileMetadata.Companion.file
import org.gradle.internal.file.impl.DefaultFileMetadata.Companion.missing
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

class NioFileMetadataAccessor : FileMetadataAccessor {
    override fun stat(file: File): FileMetadata {
        val path = file.toPath()
        var attributes: BasicFileAttributes?
        try {
            attributes = Files.readAttributes<BasicFileAttributes>(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (e: IOException) {
            return missing(FileMetadata.AccessType.DIRECT)
        }
        val accessType = FileMetadata.AccessType.viaSymlink(attributes.isSymbolicLink())
        if (accessType == FileMetadata.AccessType.VIA_SYMLINK) {
            try {
                attributes = Files.readAttributes<BasicFileAttributes>(path, BasicFileAttributes::class.java)
            } catch (e: IOException) {
                return missing(FileMetadata.AccessType.VIA_SYMLINK)
            }
        }
        if (attributes.isDirectory()) {
            return directory(accessType)
        }
        if (attributes.isOther()) {
            throw UncheckedException.throwAsUncheckedException(IOException("Unsupported file type for " + file.getAbsolutePath()), true)
        }
        return file(attributes.lastModifiedTime().toMillis(), attributes.size(), accessType)
    }
}
