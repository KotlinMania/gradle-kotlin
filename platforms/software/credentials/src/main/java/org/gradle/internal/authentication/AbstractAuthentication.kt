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

import org.gradle.api.credentials.Credentials
import org.gradle.authentication.Authentication
import java.util.Objects

abstract class AbstractAuthentication @JvmOverloads constructor(
    private val name: String?,
    private val type: Class<out Authentication?>?,
    private val supportedCredentialType: Class<out Credentials?> = null
) : AuthenticationInternal {
    private var credentials: Credentials? = null

    private val hosts: MutableSet<AuthenticationInternal.HostAndPort?>

    init {
        this.hosts = HashSet<AuthenticationInternal.HostAndPort?>()
    }

    override fun getCredentials(): Credentials? {
        return credentials
    }

    override fun setCredentials(credentials: Credentials?) {
        this.credentials = credentials
    }

    override fun getName(): String? {
        return name
    }

    override fun supports(credentials: Credentials): Boolean {
        return supportedCredentialType.isAssignableFrom(credentials.javaClass)
    }

    override fun getType(): Class<out Authentication?>? {
        return type
    }

    override fun toString(): String {
        return String.format("'%s'(%s)", getName(), getType()!!.getSimpleName())
    }


    override fun getHostsForAuthentication(): MutableCollection<AuthenticationInternal.HostAndPort?> {
        return hosts
    }


    override fun addHost(host: String?, port: Int) {
        hosts.add(DefaultHostAndPort(host, port))
    }

    private class DefaultHostAndPort(private val host: String?, private val port: Int) : AuthenticationInternal.HostAndPort {
        override fun getHost(): String? {
            return host
        }

        override fun getPort(): Int {
            return port
        }

        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as DefaultHostAndPort
            return getPort() == that.getPort() &&
                    getHost() == that.getHost()
        }

        override fun hashCode(): Int {
            return Objects.hash(getHost(), getPort())
        }
    }
}
