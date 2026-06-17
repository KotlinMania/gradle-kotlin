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
package org.gradle.internal.file.impl

import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.FileType

class DefaultFileMetadata private constructor(private val type: FileType, private val lastModified: Long, private val length: Long, private val accessType: FileMetadata.AccessType) : FileMetadata {
    override fun getType(): FileType {
        return type
    }

    override fun getLastModified(): Long {
        return lastModified
    }

    override fun getLength(): Long {
        return length
    }

    override fun getAccessType(): FileMetadata.AccessType {
        return accessType
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }
        val that = o as DefaultFileMetadata
        return type == that.type && length == that.length && lastModified == that.lastModified && accessType == that.accessType
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + (lastModified xor (lastModified ushr 32)).toInt()
        result = 31 * result + (length xor (length ushr 32)).toInt()
        result = 31 * result + accessType.hashCode()
        return result
    }

    companion object {
        private val DIR: FileMetadata = DefaultFileMetadata(FileType.Directory, 0, 0, FileMetadata.AccessType.DIRECT)
        private val DIR_ACCESSED_VIA_SYMLINK: FileMetadata = DefaultFileMetadata(FileType.Directory, 0, 0, FileMetadata.AccessType.VIA_SYMLINK)
        private val MISSING: FileMetadata = DefaultFileMetadata(FileType.Missing, 0, 0, FileMetadata.AccessType.DIRECT)
        private val BROKEN_SYMLINK: FileMetadata = DefaultFileMetadata(FileType.Missing, 0, 0, FileMetadata.AccessType.VIA_SYMLINK)
        @JvmStatic
        fun file(lastModified: Long, length: Long, accessType: FileMetadata.AccessType): FileMetadata {
            return DefaultFileMetadata(FileType.RegularFile, lastModified, length, accessType)
        }

        @JvmStatic
        fun directory(accessType: FileMetadata.AccessType): FileMetadata {
            when (accessType) {
                FileMetadata.AccessType.DIRECT -> return DIR
                FileMetadata.AccessType.VIA_SYMLINK -> return DIR_ACCESSED_VIA_SYMLINK
                else -> throw AssertionError()
            }
        }

        @JvmStatic
        fun missing(accessType: FileMetadata.AccessType): FileMetadata {
            when (accessType) {
                FileMetadata.AccessType.DIRECT -> return MISSING
                FileMetadata.AccessType.VIA_SYMLINK -> return BROKEN_SYMLINK
                else -> throw AssertionError()
            }
        }
    }
}
