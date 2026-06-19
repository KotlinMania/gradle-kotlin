/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.base.Optional
import com.google.common.collect.Sets
import com.google.common.primitives.Ints
import org.apache.commons.lang3.StringUtils
import org.gradle.internal.resource.transport.http.HttpProxySettings
import org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpProxySettings
import org.gradle.internal.resource.transport.http.JavaSystemPropertiesSecureHttpProxySettings
import java.net.URI
import java.net.URISyntaxException

class S3ConnectionProperties {
    val endpoint: Optional<URI>
    private val proxySettings: HttpProxySettings
    private val secureProxySettings: HttpProxySettings
    val maxErrorRetryCount: Optional<Int>
    val partSize: Long

    constructor() {
        endpoint = configureEndpoint(System.getProperty(S3_ENDPOINT_PROPERTY))
        proxySettings = JavaSystemPropertiesHttpProxySettings()
        secureProxySettings = JavaSystemPropertiesSecureHttpProxySettings()
        maxErrorRetryCount = configureErrorRetryCount(System.getProperty(S3_MAX_ERROR_RETRY))
        partSize = DEFAULT_PART_SIZE
    }

    constructor(proxySettings: HttpProxySettings, secureProxySettings: HttpProxySettings, endpoint: URI?, maxErrorRetryCount: Int?) {
        this.endpoint = Optional.fromNullable<URI>(endpoint)
        this.proxySettings = proxySettings
        this.secureProxySettings = secureProxySettings
        this.maxErrorRetryCount = Optional.fromNullable<Int>(maxErrorRetryCount)
        this.partSize = DEFAULT_PART_SIZE
    }

    private fun configureEndpoint(property: String?): Optional<URI> {
        var uri: URI? = null
        if (StringUtils.isNotBlank(property)) {
            try {
                uri = URI(property)
                require(
                    !(StringUtils.isBlank(uri.getScheme()) || !SUPPORTED_SCHEMES.contains(
                        uri.getScheme().uppercase()
                    ))
                ) { "System property [" + S3_ENDPOINT_PROPERTY + "=" + property + "] must have a scheme of 'http' or 'https'" }
            } catch (e: URISyntaxException) {
                throw IllegalArgumentException("System property [" + S3_ENDPOINT_PROPERTY + "=" + property + "]  must be a valid URI")
            }
        }
        return Optional.fromNullable<URI>(uri)
    }

    val proxy: Optional<HttpProxySettings.HttpProxy>
        get() {
            if (endpoint.isPresent()) {
                if (endpoint.get().getScheme().equals("HTTP", ignoreCase = true)) {
                    return Optional.fromNullable<HttpProxySettings.HttpProxy>(proxySettings.proxy)
                } else {
                    return Optional.fromNullable<HttpProxySettings.HttpProxy>(secureProxySettings.proxy)
                }
            }
            return Optional.fromNullable<HttpProxySettings.HttpProxy>(secureProxySettings.proxy)
        }

    private fun configureErrorRetryCount(property: String?): Optional<Int> {
        var count: Int? = null
        if (null != property) {
            count = Ints.tryParse(property)
            require(!(null == count || count < 0)) { "System property [" + S3_MAX_ERROR_RETRY + "=" + property + "]  must be a valid positive Integer" }
        }
        return Optional.fromNullable<Int>(count)
    }

    val multipartThreshold: Long
        get() = partSize * 2

    companion object {
        const val S3_ENDPOINT_PROPERTY: String = "org.gradle.s3.endpoint"

        //The maximum number of times to retry a request when S3 responds with a http 5xx error
        const val S3_MAX_ERROR_RETRY: String = "org.gradle.s3.maxErrorRetry"
        private val SUPPORTED_SCHEMES: MutableSet<String> = Sets.newHashSet<String>("HTTP", "HTTPS")
        private val DEFAULT_PART_SIZE = (50 * 1024 * 1024).toLong()
    }
}
