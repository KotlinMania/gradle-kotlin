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
package org.gradle.internal.resource.transport.aws.s3

import org.gradle.api.credentials.AwsCredentials
import org.gradle.authentication.Authentication
import org.gradle.authentication.aws.AwsImAuthentication
import org.gradle.internal.authentication.AllSchemesAuthentication
import org.gradle.internal.resource.connector.ResourceConnectorFactory
import org.gradle.internal.resource.connector.ResourceConnectorSpecification
import org.gradle.internal.resource.transfer.ExternalResourceConnector

class S3ConnectorFactory : ResourceConnectorFactory {
    override fun getSupportedProtocols(): MutableSet<String?> {
        return mutableSetOf<String?>("s3")
    }

    override fun getSupportedAuthentication(): MutableSet<Class<out Authentication?>?> {
        val supported: MutableSet<Class<out Authentication?>?> = HashSet<Class<out Authentication?>?>()
        supported.add(AwsImAuthentication::class.java)
        supported.add(AllSchemesAuthentication::class.java)
        return supported
    }

    override fun createResourceConnector(connectionDetails: ResourceConnectorSpecification): ExternalResourceConnector {
        val authentications = connectionDetails.getAuthentications()
        // Since s3 transport supports only one type of credentials at a time, let's use the first one found.
        for (authentication in authentications) {
            // We get only the first element here, nothing else. But Collection
            // forces us to use an iterator.
            if (authentication is AllSchemesAuthentication) {
                // First things first, retro compatibility
                val awsCredentials = connectionDetails.getCredentials<AwsCredentials>(AwsCredentials::class.java)
                requireNotNull(awsCredentials) { "AwsCredentials must be set for S3 backed repository." }
                return S3ResourceConnector(S3Client(awsCredentials, S3ConnectionProperties()))
            }

            if (authentication is AwsImAuthentication) {
                return S3ResourceConnector(S3Client(S3ConnectionProperties()))
            }
        }

        throw IllegalArgumentException("S3 resource should either specify AwsImAuthentication or provide some AwsCredentials.")
    }
}
