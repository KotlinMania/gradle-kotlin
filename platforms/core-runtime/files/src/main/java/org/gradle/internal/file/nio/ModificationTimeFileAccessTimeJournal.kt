/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.internal.file.FileAccessTimeJournal
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.FileTime

class ModificationTimeFileAccessTimeJournal : FileAccessTimeJournal {
    override fun setLastAccessTime(file: File, millis: Long) {
        try {
            Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(millis))
        } catch (e: IOException) {
            LOGGER.debug("Ignoring failure to set last access time of " + file, e)
        }
    }

    override fun getLastAccessTime(file: File): Long {
        return file.lastModified()
    }

    override fun deleteLastAccessTime(file: File) {
        // nothing to do
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(ModificationTimeFileAccessTimeJournal::class.java)
    }
}
