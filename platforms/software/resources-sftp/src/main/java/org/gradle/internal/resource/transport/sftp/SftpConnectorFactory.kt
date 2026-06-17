/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.credentials.PasswordCredentials
import org.gradle.authentication.Authentication
import org.gradle.internal.authentication.AllSchemesAuthentication
import org.gradle.internal.resource.connector.ResourceConnectorFactory
import org.gradle.internal.resource.connector.ResourceConnectorSpecification
import org.gradle.internal.resource.transfer.DefaultExternalResourceConnector
import org.gradle.internal.resource.transfer.ExternalResourceConnector

class SftpConnectorFactory(private val sftpClientFactory: SftpClientFactory?) : ResourceConnectorFactory {
    override fun getSupportedProtocols(): MutableSet<String?> {
        return mutableSetOf<String?>("sftp")
    }

    override fun getSupportedAuthentication(): MutableSet<Class<out Authentication?>?> {
        val supported: MutableSet<Class<out Authentication?>?> = HashSet<Class<out Authentication?>?>()
        supported.add(AllSchemesAuthentication::class.java)
        return supported
    }

    override fun createResourceConnector(connectionDetails: ResourceConnectorSpecification): ExternalResourceConnector {
        val passwordCredentials = connectionDetails.getCredentials<PasswordCredentials?>(PasswordCredentials::class.java)
        val accessor = SftpResourceAccessor(sftpClientFactory, passwordCredentials)
        val lister = SftpResourceLister(sftpClientFactory, passwordCredentials)
        val uploader = SftpResourceUploader(sftpClientFactory, passwordCredentials)
        return DefaultExternalResourceConnector(accessor, lister, uploader)
    }
}
