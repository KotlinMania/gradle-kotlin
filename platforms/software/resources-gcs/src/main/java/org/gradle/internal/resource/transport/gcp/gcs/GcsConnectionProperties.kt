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
package org.gradle.internal.resource.transport.gcp.gcs

import com.google.common.base.Optional
import com.google.common.collect.Sets
import org.apache.commons.lang3.StringUtils
import java.net.URI
import java.net.URISyntaxException

// re-use not possible across modules currently
class GcsConnectionProperties private constructor(private val endpoint: URI?, private val servicePath: String?, private val disableAuthentication: Boolean) {
    @JvmOverloads
    internal constructor(
        endpoint: String? = System.getProperty(GCS_ENDPOINT_PROPERTY), servicePath: String? = System.getProperty(GCS_SERVICE_PATH_PROPERTY), disableAuthentication: String? = System.getProperty(
            GCS_DISABLE_AUTH_PROPERTY
        )
    ) : this(
        configureEndpoint(endpoint),
        configureServicePath(servicePath),
        configureDisableAuthentication(disableAuthentication)
    )

    fun getEndpoint(): Optional<URI> {
        return Optional.fromNullable(endpoint)
    }

    fun getServicePath(): Optional<String> {
        return Optional.fromNullable(servicePath)
    }

    fun requiresAuthentication(): Boolean {
        return !disableAuthentication
    }

    companion object {
        const val GCS_ENDPOINT_PROPERTY: String = "org.gradle.gcs.endpoint"
        const val GCS_SERVICE_PATH_PROPERTY: String = "org.gradle.gcs.servicePath"

        // Controls when to disable reading default authentication credentials, should be used in tests only
        const val GCS_DISABLE_AUTH_PROPERTY: String = "org.gradle.gcs.disableAuthentication"

        private val SUPPORTED_SCHEMES: MutableSet<String> = Sets.newHashSet("HTTP", "HTTPS")

        private fun configureEndpoint(property: String?): URI? {
            var uri: URI? = null
            if (StringUtils.isNotBlank(property)) {
                try {
                    uri = URI(property)
                    require(
                        !(StringUtils.isBlank(uri.getScheme()) || !SUPPORTED_SCHEMES.contains(
                            uri.getScheme().uppercase()
                        ))
                    ) { "System property [" + GCS_ENDPOINT_PROPERTY + "=" + property + "] must have a scheme of 'http' or 'https'" }
                } catch (e: URISyntaxException) {
                    throw IllegalArgumentException("System property [" + GCS_ENDPOINT_PROPERTY + "=" + property + "]  must be a valid URI")
                }
            }
            return uri
        }

        private fun configureServicePath(property: String?): String? {
            if (StringUtils.isNotBlank(property)) {
                return property
            } else {
                return null
            }
        }

        private fun configureDisableAuthentication(property: String?): Boolean {
            return StringUtils.isNotBlank(property) && property.toBoolean()
        }
    }
}
