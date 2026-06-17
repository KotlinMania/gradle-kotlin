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
package org.gradle.internal.nativeintegration.filesystem.jdk7

import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.internal.nativeintegration.filesystem.Symlink
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

open class Jdk7Symlink protected constructor(private val symlinkCreationSupported: Boolean) : Symlink {
    constructor(temporaryFileProvider: TemporaryFileProvider) : this(doesSystemSupportSymlinks(temporaryFileProvider))

    override fun isSymlinkCreationSupported(): Boolean {
        return symlinkCreationSupported
    }

    @Throws(Exception::class)
    override fun symlink(link: File, target: File) {
        link.getParentFile().mkdirs()
        Files.createSymbolicLink(link.toPath(), target.toPath())
    }

    override fun isSymlink(suspect: File): Boolean {
        return Files.isSymbolicLink(suspect.toPath())
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(Jdk7Symlink::class.java)

        private fun doesSystemSupportSymlinks(temporaryFileProvider: TemporaryFileProvider): Boolean {
            var sourceFile: Path? = null
            var linkFile: Path? = null
            try {
                sourceFile = temporaryFileProvider.createTemporaryFile("symlink", "test").toPath()
                linkFile = temporaryFileProvider.createTemporaryFile("symlink", "test_link").toPath()

                Files.delete(linkFile)
                Files.createSymbolicLink(linkFile, sourceFile)
                return true
            } catch (e: InternalError) {
                if (e.message!!.contains("Should not get here")) {
                    // probably facing JDK-8046686
                    LOGGER.debug("Unable to create a symlink. Your system is hitting JDK bug id JDK-8046686. Symlink support disabled.", e)
                } else {
                    LOGGER.debug("Unexpected internal error", e)
                }
                return false
            } catch (e: IOException) {
                return false
            } catch (e: UnsupportedOperationException) {
                return false
            } finally {
                try {
                    if (sourceFile != null) {
                        Files.deleteIfExists(sourceFile)
                    }
                    if (linkFile != null) {
                        Files.deleteIfExists(linkFile)
                    }
                } catch (e: IOException) {
                    // We don't really need to handle this.
                }
            }
        }
    }
}
