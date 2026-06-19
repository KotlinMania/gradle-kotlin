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
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ResourceExceptions
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.internal.resource.transfer.AbstractExternalResourceAccessor
import org.gradle.internal.resource.transfer.ExternalResourceAccessor
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse
import java.net.URI

class SftpResourceAccessor(private val sftpClientFactory: SftpClientFactory, private val credentials: PasswordCredentials) : AbstractExternalResourceAccessor(), ExternalResourceAccessor {
    override fun getMetaData(location: ExternalResourceName, revalidate: Boolean): ExternalResourceMetaData? {
        val sftpClient = sftpClientFactory.createSftpClient(location.uri, credentials)
        try {
            val attributes = sftpClient.getSftpClient().lstat(location.path)
            return if (attributes != null) toMetaData(location.uri, attributes) else null
        } catch (e: SftpException) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return null
            }
            throw ResourceExceptions.getFailed(location.uri, e)
        } finally {
            sftpClientFactory.releaseSftpClient(sftpClient)
        }
    }

    private fun toMetaData(uri: URI, attributes: SftpATTRS): ExternalResourceMetaData {
        var lastModified: Long = -1
        var contentLength: Long = -1

        if ((attributes.getFlags() and SftpATTRS.SSH_FILEXFER_ATTR_ACMODTIME) != 0) {
            lastModified = attributes.getMTime().toLong() * 1000
        }
        if ((attributes.getFlags() and SftpATTRS.SSH_FILEXFER_ATTR_SIZE) != 0) {
            contentLength = attributes.getSize()
        }

        return DefaultExternalResourceMetaData(uri, lastModified, contentLength)
    }

    public override fun openResource(location: ExternalResourceName, revalidate: Boolean): ExternalResourceReadResponse? {
        val metaData = getMetaData(location, revalidate)
        return if (metaData != null) SftpResource(sftpClientFactory, metaData, location.uri, credentials) else null
    }
}
