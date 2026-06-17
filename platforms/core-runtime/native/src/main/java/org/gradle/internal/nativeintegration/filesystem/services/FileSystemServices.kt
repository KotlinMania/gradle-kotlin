/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.internal.file.FileCanonicalizer
import org.gradle.internal.file.FileMetadataAccessor
import org.gradle.internal.file.FilePermissionHandler
import org.gradle.internal.file.StatStatistics
import org.gradle.internal.file.nio.Jdk7FileCanonicalizer
import org.gradle.internal.file.nio.PosixJdk7FilePermissionHandler
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.nativeintegration.filesystem.Symlink
import org.gradle.internal.nativeintegration.filesystem.jdk7.Jdk7Symlink
import org.gradle.internal.nativeintegration.filesystem.jdk7.WindowsJdk7Symlink
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider

class FileSystemServices : ServiceRegistrationProvider {
    fun configure(registration: ServiceRegistration) {
        registration.add(GenericFileSystem.Factory::class.java)
        registration.add(StatStatistics.Collector::class.java)
    }

    @Provides
    fun createFileCanonicalizer(): FileCanonicalizer {
        return Jdk7FileCanonicalizer()
    }

    @Provides
    fun createWindowsJdkSymlink(): Symlink {
        return WindowsJdk7Symlink()
    }

    @Provides
    fun createJdkSymlink(temporaryFileProvider: TemporaryFileProvider?): Symlink {
        return Jdk7Symlink(temporaryFileProvider)
    }

    @Provides
    fun createFileSystem(
        genericFileSystemFactory: GenericFileSystem.Factory,
        operatingSystem: OperatingSystem,
        metadataAccessor: FileMetadataAccessor?,
        temporaryFileProvider: TemporaryFileProvider?
    ): FileSystem {
        if (operatingSystem.isWindows()) {
            val symlink = createWindowsJdkSymlink()
            return genericFileSystemFactory.create(EmptyChmod(), FallbackStat(), symlink)
        }

        val symlink = createJdkSymlink(temporaryFileProvider)
        val handler: FilePermissionHandler = PosixJdk7FilePermissionHandler()
        return genericFileSystemFactory.create(handler, handler, symlink)
    }
}
