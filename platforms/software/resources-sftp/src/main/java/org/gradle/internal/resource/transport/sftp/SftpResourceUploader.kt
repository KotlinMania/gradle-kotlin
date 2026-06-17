/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.resource.transport.sftp

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpException
import org.apache.commons.io.FilenameUtils
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ReadableContent
import org.gradle.internal.resource.ResourceExceptions
import org.gradle.internal.resource.transfer.ExternalResourceUploader
import java.io.IOException
import java.net.URI

class SftpResourceUploader(private val sftpClientFactory: SftpClientFactory, private val credentials: PasswordCredentials?) : ExternalResourceUploader {
    @Throws(IOException::class)
    override fun upload(resource: ReadableContent, destination: ExternalResourceName) {
        val client = sftpClientFactory.createSftpClient(destination.getUri(), credentials)

        try {
            val channel = client.getSftpClient()
            ensureParentDirectoryExists(channel, destination.getUri())
            val sourceStream = resource.open()
            try {
                channel.put(sourceStream, destination.getPath())
            } finally {
                sourceStream.close()
            }
        } catch (e: SftpException) {
            throw ResourceExceptions.putFailed(destination.getUri(), e)
        } finally {
            sftpClientFactory.releaseSftpClient(client)
        }
    }

    private fun ensureParentDirectoryExists(channel: ChannelSftp, uri: URI) {
        val parentPath = FilenameUtils.getFullPathNoEndSeparator(uri.getPath())
        if (parentPath == "/") {
            return
        }
        val parent = uri.resolve(parentPath)

        try {
            channel.lstat(parentPath)
            return
        } catch (e: SftpException) {
            if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                throw ResourceException(parent, String.format("Could not lstat resource '%s'.", parent), e)
            }
        }
        ensureParentDirectoryExists(channel, parent)
        try {
            channel.mkdir(parentPath)
        } catch (e: SftpException) {
            throw ResourceException(parent, String.format("Could not create resource '%s'.", parent), e)
        }
    }
}
