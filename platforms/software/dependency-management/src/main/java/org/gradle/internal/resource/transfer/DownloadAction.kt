/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.internal.resource.transfer

import org.apache.commons.io.IOUtils
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.util.internal.GFileUtils
import org.slf4j.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class DownloadAction(private val source: ExternalResourceName, private val temporaryFileProvider: TemporaryFileProvider, private val logger: Logger?) :
    ExternalResource.ContentAndMetadataAction<Any?> {
    private var destination: File? = null
    var metaData: ExternalResourceMetaData? = null
        private set

    @Throws(IOException::class)
    override fun execute(inputStream: InputStream, metaData: ExternalResourceMetaData): Any {
        destination = temporaryFileProvider.createTemporaryFile("gradle_download", "bin")
        this.metaData = metaData
        if (logger != null) {
            logger.info("Downloading {} to {}", source, destination)
        }
        if (destination!!.getParentFile() != null) {
            GFileUtils.mkdirs(destination!!.getParentFile())
        }
        FileOutputStream(destination).use { outputStream ->
            IOUtils.copyLarge(inputStream, outputStream)
        }
        return null
    }

    fun getDestination(): File {
        return destination!!
    }
}
