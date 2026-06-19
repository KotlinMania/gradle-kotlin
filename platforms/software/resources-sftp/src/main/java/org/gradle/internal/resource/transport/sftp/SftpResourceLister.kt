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
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.transfer.ExternalResourceLister
import java.util.Vector

class SftpResourceLister(private val sftpClientFactory: SftpClientFactory, private val credentials: PasswordCredentials) : ExternalResourceLister {
    override fun list(directory: ExternalResourceName): MutableList<String?>? {
        val client = sftpClientFactory.createSftpClient(directory.uri, credentials)

        try {
            @Suppress("UNCHECKED_CAST")
            val entries = client.getSftpClient().ls(directory.path) as Vector<ChannelSftp.LsEntry>
            val list: MutableList<String?> = ArrayList<String?>()
            for (entry in entries) {
                list.add(entry.filename)
            }
            return list
        } catch (e: SftpException) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return null
            }
            throw ResourceException(directory.uri, String.format("Could not list children for resource '%s'.", directory.uri), e)
        } finally {
            sftpClientFactory.releaseSftpClient(client)
        }
    }
}
