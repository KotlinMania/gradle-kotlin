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
package org.gradle.internal.resource.transport.http

import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

class JavaSystemPropertiesHttpTimeoutSettings : HttpTimeoutSettings {
    override val connectionTimeoutMs: Int
    override val socketTimeoutMs: Int
    override val idleConnectionTimeoutMs: Int

    init {
        this.connectionTimeoutMs = initTimeout(CONNECTION_TIMEOUT_SYSTEM_PROPERTY, DEFAULT_CONNECTION_TIMEOUT)
        this.socketTimeoutMs = initTimeout(SOCKET_TIMEOUT_SYSTEM_PROPERTY, DEFAULT_SOCKET_TIMEOUT)
        this.idleConnectionTimeoutMs = initTimeout(IDLE_CONNECTION_TIMEOUT_SYSTEM_PROPERTY, DEFAULT_IDLE_CONNECTION_TIMEOUT)
    }

    private fun initTimeout(propertyName: String, defaultValue: Int): Int {
        val systemProperty = System.getProperty(propertyName)

        if (!StringUtils.isBlank(systemProperty)) {
            try {
                return systemProperty!!.toInt()
            } catch (e: NumberFormatException) {
                LOGGER.warn(
                    "Invalid value for java system property '{}': {}. Default timeout '{}' will be used.",
                    propertyName, systemProperty, defaultValue
                )
            }
        }

        return defaultValue
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(JavaSystemPropertiesHttpTimeoutSettings::class.java)
        const val CONNECTION_TIMEOUT_SYSTEM_PROPERTY: String = "org.gradle.internal.http.connectionTimeout"
        const val SOCKET_TIMEOUT_SYSTEM_PROPERTY: String = "org.gradle.internal.http.socketTimeout"
        const val IDLE_CONNECTION_TIMEOUT_SYSTEM_PROPERTY: String = "org.gradle.internal.http.idleConnectionTimeout"
        const val DEFAULT_CONNECTION_TIMEOUT: Int = 30000
        const val DEFAULT_SOCKET_TIMEOUT: Int = 30000

        /**
         * The default time in milliseconds for an idle connection to remain open.
         * [Microsoft Azure closes idle connections after 4 min](https://azure.microsoft.com/en-us/blog/new-configurable-idle-timeout-for-azure-load-balancer/),
         * so we set our default to be below that.
         */
        val DEFAULT_IDLE_CONNECTION_TIMEOUT: Int = Duration.ofMinutes(3).toMillis().toInt()
    }
}
