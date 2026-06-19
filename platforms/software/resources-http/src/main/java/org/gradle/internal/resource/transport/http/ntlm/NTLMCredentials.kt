/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.internal.resource.transport.http.ntlm

import com.google.common.base.Preconditions
import java.net.InetAddress
import java.net.UnknownHostException


open class NTLMCredentials(username: String, password: String?) {
    val domain: String?
    val username: String
    val password: String?
    val workstation: String

    init {
        var username = username
        val domain: String?
        Preconditions.checkNotNull(username, "Username must not be null!")
        var slashPos = username.indexOf('\\')
        slashPos = if (slashPos >= 0) slashPos else username.indexOf('/')
        if (slashPos >= 0) {
            domain = username.substring(0, slashPos)
            username = username.substring(slashPos + 1)
        } else {
            domain = System.getProperty("http.auth.ntlm.domain", DEFAULT_DOMAIN)
        }
        this.domain = if (domain == null) null else domain.uppercase()
        this.username = username
        this.password = password
        this.workstation = determineWorkstationName()
    }

    private fun determineWorkstationName(): String {
        // This is a hidden property that may be useful to track down issues. Remove when NTLM Auth is solid.
        val sysPropWorkstation = System.getProperty("http.auth.ntlm.workstation")
        if (sysPropWorkstation != null) {
            return sysPropWorkstation
        }

        try {
            return removeDotSuffix(this.hostName!!).uppercase()
        } catch (e: UnknownHostException) {
            return DEFAULT_WORKSTATION
        }
    }

    @get:Throws(UnknownHostException::class)
    protected open val hostName: String?
        get() = InetAddress.getLocalHost().getHostName()

    private fun removeDotSuffix(`val`: String): String {
        val dotPos = `val`.indexOf('.')
        return if (dotPos == -1) `val` else `val`.substring(0, dotPos)
    }


    override fun toString(): String {
        return String.format("NTLM Credentials [user: %s, domain: %s, workstation: %s]", username, domain, workstation)
    }

    companion object {
        private const val DEFAULT_DOMAIN = ""
        private const val DEFAULT_WORKSTATION = ""
    }
}
