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
package org.gradle.internal.resource.transport.http

import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class JavaSystemPropertiesProxySettings internal constructor(
    val propertyPrefix: String?,
    val defaultPort: Int,
    proxyHost: String?,
    proxyPortString: String?,
    proxyUser: String?,
    proxyPassword: String?
) : HttpProxySettings {
    private val proxy: HttpProxySettings.HttpProxy?

    constructor(propertyPrefix: String?, defaultPort: Int) : this(
        propertyPrefix, defaultPort,
        getAndTrimSystemProperty(propertyPrefix + ".proxyHost"),
        getAndTrimSystemProperty(propertyPrefix + ".proxyPort"),
        getAndTrimSystemProperty(propertyPrefix + ".proxyUser"),
        getAndTrimSystemProperty(propertyPrefix + ".proxyPassword")
    )

    init {
        if (StringUtils.isBlank(proxyHost)) {
            this.proxy = null
        } else {
            this.proxy = HttpProxySettings.HttpProxy(proxyHost, initProxyPort(proxyPortString), proxyUser, proxyPassword)
        }
    }

    private fun initProxyPort(proxyPortString: String?): Int {
        if (StringUtils.isBlank(proxyPortString)) {
            return defaultPort
        }
        try {
            return proxyPortString!!.toInt()
        } catch (e: NumberFormatException) {
            val key = propertyPrefix + ".proxyPort"
            LOGGER.warn(
                "Invalid value for java system property '{}': '{}'. Value is not a valid number. Default port '{}' will be used.",
                key, proxyPortString, defaultPort
            )
            return defaultPort
        }
    }

    override fun getProxy(): HttpProxySettings.HttpProxy? {
        return proxy
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(JavaSystemPropertiesProxySettings::class.java)

        private fun getAndTrimSystemProperty(key: String): String? {
            val value = System.getProperty(key)
            return if (value != null) value.trim { it <= ' ' } else null
        }
    }
}
