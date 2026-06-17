/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.util.internal

import org.jspecify.annotations.NullMarked
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.AbstractMap
import java.util.Base64
import java.util.Objects
import java.util.function.Function

@NullMarked
class WrapperCredentials private constructor(private val token: String?, private val basicUserInfo: String?) {
    fun token(): String? {
        return token
    }

    fun usernameAndPassword(): MutableMap.MutableEntry<String, String>? {
        if (basicUserInfo == null) {
            return null
        }

        val usernameEnd = basicUserInfo.indexOf(':')
        return if (usernameEnd >= 0)
            mapEntry(basicUserInfo.substring(0, usernameEnd), basicUserInfo.substring(usernameEnd + 1))
        else
            null
    }

    fun username(): String? {
        val combined = usernameAndPassword()
        return if (combined != null) combined.key else null
    }

    fun authorizationTypeDisplayName(): String {
        return if (token != null)
            "Bearer Token"
        else
            "Basic"
    }

    fun authorizationHeader(): MutableMap.MutableEntry<String, String> {
        return mapEntry("Authorization", authorizationHeaderValue())
    }

    private fun authorizationHeaderValue(): String {
        if (token != null) {
            return "Bearer " + token
        } else if (basicUserInfo != null) {
            return "Basic " + Base64.getEncoder().encodeToString(basicUserInfo.toByteArray(StandardCharsets.UTF_8))
        } else {
            throw AssertionError("Internal error: Unexpected credentials state.")
        }
    }

    override fun equals(o: Any): Boolean {
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as WrapperCredentials
        return token == that.token
                && basicUserInfo == that.basicUserInfo
    }

    override fun hashCode(): Int {
        return Objects.hash(token, basicUserInfo)
    }

    override fun toString(): String {
        return ("WrapperCredentials{"
                + (if (token != null) "<TOKEN>" else "password for " + username())
                + '}')
    }

    companion object {
        fun fromToken(token: String): WrapperCredentials {
            return WrapperCredentials(Objects.requireNonNull<String>(token, "token"), null)
        }

        fun fromBasicUserInfo(basicUserInfo: String): WrapperCredentials {
            return WrapperCredentials(null, Objects.requireNonNull<String>(basicUserInfo, "basicUserInfo"))
        }

        fun fromUsernamePassword(username: String, password: String): WrapperCredentials {
            Objects.requireNonNull<String>(username, "username")
            Objects.requireNonNull<String>(password, "password")
            return fromBasicUserInfo(username + ':' + password)
        }

        fun findCredentials(
            distributionUrl: URI,
            propertyProvider: Function<in String, out String?>
        ): WrapperCredentials? {
            Objects.requireNonNull<URI>(distributionUrl, "distributionUrl")
            Objects.requireNonNull(propertyProvider, "propertyProvider")

            val token: String? = tryGetProperty(distributionUrl.getHost(), "wrapperToken", propertyProvider)
            if (token != null) {
                return fromToken(token)
            }
            return findBasicCredentials(distributionUrl, propertyProvider)
        }

        private fun findBasicCredentials(
            distributionUrl: URI,
            propertyProvider: Function<in String, out String?>
        ): WrapperCredentials? {
            val host = distributionUrl.getHost()
            val username: String? = tryGetProperty(host, "wrapperUser", propertyProvider)
            val password: String? = tryGetProperty(host, "wrapperPassword", propertyProvider)
            if (username != null && password != null) {
                return fromUsernamePassword(username, password)
            }

            val userInfo = distributionUrl.getUserInfo()
            return if (userInfo != null) fromBasicUserInfo(userInfo) else null
        }

        private fun tryGetProperty(
            host: String?,
            key: String,
            propertyProvider: Function<in String, out String?>
        ): String? {
            if (host != null) {
                val hostEscaped = host.replace('.', '_').lowercase()
                val hostProperty: String? = propertyProvider.apply("gradle." + hostEscaped + '.' + key)
                if (hostProperty != null) {
                    return hostProperty
                }
            }
            return propertyProvider.apply("gradle." + key)
        }

        private fun mapEntry(key: String, value: String): MutableMap.MutableEntry<String, String> {
            return AbstractMap.SimpleImmutableEntry<String, String>(key, value)
        }
    }
}
