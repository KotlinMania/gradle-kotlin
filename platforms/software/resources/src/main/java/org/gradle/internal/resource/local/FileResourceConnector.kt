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
package org.gradle.internal.resource.local

import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.LocalBinaryResource
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import java.io.File
import java.net.URI

class FileResourceConnector(private val fileSystem: FileSystem, private val listener: FileResourceListener) : FileResourceRepository {
    override fun withProgressLogging(): ExternalResourceRepository {
        return this
    }

    override fun localResource(file: File): LocalBinaryResource {
        return LocalFileStandInExternalResource(file, fileSystem, listener)
    }

    override fun resource(resource: ExternalResourceName, revalidate: Boolean): LocallyAvailableExternalResource {
        return resource(resource)
    }

    override fun resource(location: ExternalResourceName): LocallyAvailableExternalResource {
        val localFile: File = getFile(location)
        return LocalFileStandInExternalResource(localFile, fileSystem, listener)
    }

    override fun resource(file: File): LocallyAvailableExternalResource {
        return LocalFileStandInExternalResource(file, fileSystem, listener)
    }

    override fun resource(file: File, originUri: URI, originMetadata: ExternalResourceMetaData?): LocallyAvailableExternalResource {
        return DefaultLocallyAvailableExternalResource(originUri, file, originMetadata, fileSystem)
    }

    companion object {
        private fun getFile(location: ExternalResourceName): File {
            return File(location.uri)
        }
    }
}
