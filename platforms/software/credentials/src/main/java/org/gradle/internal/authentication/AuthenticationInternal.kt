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
package org.gradle.internal.authentication

import org.gradle.api.NonExtensible
import org.gradle.api.credentials.Credentials
import org.gradle.authentication.Authentication

@NonExtensible
interface AuthenticationInternal : Authentication {
    fun supports(credentials: Credentials?): Boolean

    @JvmField
    var credentials: Credentials?

    @JvmField
    val type: Class<out Authentication?>?

    fun requiresCredentials(): Boolean

    fun addHost(host: String?, port: Int)

    @JvmField
    val hostsForAuthentication: MutableCollection<HostAndPort?>?

    interface HostAndPort {
        /**
         * The hostname that the credentials are required for.
         *
         * null means "any host"
         */
        @JvmField
        val host: String?

        /**
         * The port that the credentials are required for
         *
         * -1 means "any port"
         */
        @JvmField
        val port: Int
    }
}
