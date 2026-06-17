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
package org.gradle.internal.resource.transport.sftp

import org.gradle.api.credentials.PasswordCredentials
import java.net.URI

class SftpHost(uri: URI, credentials: PasswordCredentials) {
    val hostname: String
    val port: Int
    val username: String?
    val password: String?

    init {
        hostname = uri.getHost()
        port = uri.getPort()
        username = credentials.username
        password = credentials.password
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val sftpHost = o as SftpHost

        if (port != sftpHost.port) {
            return false
        }
        if (hostname != sftpHost.hostname) {
            return false
        }
        if (if (password != null) (password != sftpHost.password) else sftpHost.password != null) {
            return false
        }
        if (if (username != null) (username != sftpHost.username) else sftpHost.username != null) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = hostname.hashCode()
        result = 31 * result + port
        result = 31 * result + (if (username != null) username.hashCode() else 0)
        result = 31 * result + (if (password != null) password.hashCode() else 0)
        return result
    }

    override fun toString(): String {
        return String.format("%s:%d (Username: %s)", hostname, port, username)
    }
}
