/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.jvm.toolchain.internal.install

import org.apache.commons.io.IOUtils
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.resources.MissingResourceException
import org.gradle.authentication.Authentication
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResourceFactory
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ResourceExceptions
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@ServiceScope(Scope.Build::class)
class SecureFileDownloader(private val externalResourceFactory: ExternalResourceFactory) {
    fun getResourceFor(source: URI, authentications: MutableCollection<Authentication>): ExternalResource {
        return createExternalResource(source, authentications)
    }

    fun getResourceFor(source: URI): ExternalResource {
        return createExternalResource(source, mutableListOf<Authentication>())
    }

    fun download(source: URI, destination: File, resource: ExternalResource) {
        try {
            downloadResource(source, destination, resource)
        } catch (e: MissingResourceException) {
            throw MissingResourceException(source, String.format("Unable to download '%s' into file '%s'", source, destination), e)
        }
    }

    private fun createExternalResource(source: URI, authentications: MutableCollection<Authentication>): ExternalResource {
        val resourceName: ExternalResourceName = object : ExternalResourceName(source) {
            override val shortDisplayName: String
                get() = source.toString()
        }
        @Suppress("UNCHECKED_CAST")
        return externalResourceFactory.createExternalResource(source, authentications as MutableCollection<Authentication?>)!!.withProgressLogging().resource(resourceName)
    }

    private fun downloadResource(source: URI, targetFile: File, resource: ExternalResource) {
        val downloadFile = File(targetFile.getAbsoluteFile().toString() + ".part")
        try {
            resource.withContent(Action { inputStream: InputStream? ->
                LOGGER.info("Downloading {} to {}", resource.getDisplayName(), targetFile)
                copyIntoFile(source, inputStream!!, downloadFile)
            })
            try {
                moveFile(targetFile, downloadFile)
            } catch (e: IOException) {
                throw GradleException("Unable to move downloaded file to target destination", e)
            }
        } finally {
            downloadFile.delete()
        }
    }

    @Throws(IOException::class)
    private fun moveFile(targetFile: File, downloadFile: File) {
        try {
            Files.move(downloadFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(downloadFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun copyIntoFile(source: URI, inputStream: InputStream, destination: File) {
        try {
            FileOutputStream(destination).use { outputStream ->
                IOUtils.copyLarge(inputStream, outputStream)
            }
        } catch (e: IOException) {
            throw ResourceExceptions.getFailed(source, e)
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(SecureFileDownloader::class.java)
    }
}
