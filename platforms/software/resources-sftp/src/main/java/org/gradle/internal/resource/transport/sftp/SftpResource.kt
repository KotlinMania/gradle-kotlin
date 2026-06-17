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

import com.jcraft.jsch.SftpException
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.internal.resource.ResourceExceptions
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse
import java.io.InputStream
import java.net.URI

class SftpResource(private val clientFactory: SftpClientFactory, private val metaData: ExternalResourceMetaData?, val uRI: URI, private val credentials: PasswordCredentials?) :
    ExternalResourceReadResponse {
    private var client: LockableSftpClient? = null

    override fun openStream(): InputStream? {
        client = clientFactory.createSftpClient(this.uRI, credentials)
        try {
            return client!!.getSftpClient().get(uRI.getPath())
        } catch (e: SftpException) {
            throw ResourceExceptions.getFailed(this.uRI, e)
        }
    }

    val isLocal: Boolean
        get() = false

    override fun getMetaData(): ExternalResourceMetaData? {
        return metaData
    }

    override fun close() {
        clientFactory.releaseSftpClient(client)
    }
}
