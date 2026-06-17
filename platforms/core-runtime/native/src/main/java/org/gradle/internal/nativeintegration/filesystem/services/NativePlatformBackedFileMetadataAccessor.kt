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

import net.rubygrapefruit.platform.NativeException
import net.rubygrapefruit.platform.file.FileInfo
import net.rubygrapefruit.platform.file.Files
import org.gradle.internal.UncheckedException
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.FileMetadata.AccessType.Companion.viaSymlink
import org.gradle.internal.file.FileMetadataAccessor
import org.gradle.internal.file.impl.DefaultFileMetadata.Companion.directory
import org.gradle.internal.file.impl.DefaultFileMetadata.Companion.file
import org.gradle.internal.file.impl.DefaultFileMetadata.Companion.missing
import java.io.File
import java.io.IOException

class NativePlatformBackedFileMetadataAccessor(private val files: Files) : FileMetadataAccessor {
    override fun stat(f: File): FileMetadata? {
        var stat: FileInfo
        try {
            stat = files.stat(f, false)
        } catch (e: NativeException) {
            throw UncheckedException.throwAsUncheckedException(IOException("Could not stat file " + f.getAbsolutePath(), e), true)
        }
        val accessType = viaSymlink(stat.getType() == FileInfo.Type.Symlink)
        if (accessType == FileMetadata.AccessType.VIA_SYMLINK) {
            try {
                stat = files.stat(f, true)
            } catch (e: NativeException) {
                // For a symlink cycle, file.exists() returns false when unable to stat the file.
                if (!f.exists()) {
                    return missing(accessType)
                }
                throw UncheckedException.throwAsUncheckedException(IOException("Could not stat file " + f.getAbsolutePath(), e), true)
            }
        }
        when (stat.getType()) {
            FileInfo.Type.File -> return file(stat.getLastModifiedTime(), stat.getSize(), accessType)
            FileInfo.Type.Directory -> return directory(accessType)
            FileInfo.Type.Missing -> return missing(accessType)
            FileInfo.Type.Other -> throw UncheckedException.throwAsUncheckedException(IOException("Unsupported file type for " + f.getAbsolutePath()), true)
            else -> throw IllegalArgumentException("Unrecognised file type: " + stat.getType())
        }
    }
}
