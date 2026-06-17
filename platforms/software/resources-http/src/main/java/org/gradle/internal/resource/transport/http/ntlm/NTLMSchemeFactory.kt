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

import jcifs.ntlmssp.NtlmFlags
import jcifs.ntlmssp.Type1Message
import jcifs.ntlmssp.Type2Message
import jcifs.ntlmssp.Type3Message
import jcifs.util.Base64
import org.apache.http.auth.AuthScheme
import org.apache.http.auth.AuthSchemeProvider
import org.apache.http.impl.auth.NTLMEngine
import org.apache.http.impl.auth.NTLMEngineException
import org.apache.http.impl.auth.NTLMScheme
import org.apache.http.protocol.HttpContext
import java.io.IOException

// Copied from http://hc.apache.org/httpcomponents-client-ga/ntlm.html
class NTLMSchemeFactory : AuthSchemeProvider {
    override fun create(context: HttpContext?): AuthScheme {
        return NTLMScheme(JCIFSEngine())
    }

    private class JCIFSEngine : NTLMEngine {
        @Throws(NTLMEngineException::class)
        override fun generateType1Msg(domain: String?, workstation: String?): String {
            val type1Message = Type1Message(TYPE_1_FLAGS, domain, workstation)
            return Base64.encode(type1Message.toByteArray())
        }

        @Throws(NTLMEngineException::class)
        override fun generateType3Msg(username: String?, password: String?, domain: String?, workstation: String?, challenge: String): String {
            val type2Message: Type2Message?
            try {
                type2Message = Type2Message(Base64.decode(challenge))
            } catch (exception: IOException) {
                throw NTLMEngineException("Invalid NTLM type 2 message", exception)
            }
            val type2Flags = type2Message.getFlags()
            val type3Flags = type2Flags and (-0x1 xor (NtlmFlags.NTLMSSP_TARGET_TYPE_DOMAIN or NtlmFlags.NTLMSSP_TARGET_TYPE_SERVER))
            val type3Message = Type3Message(type2Message, password, domain, username, workstation, type3Flags)
            return Base64.encode(type3Message.toByteArray())
        }

        companion object {
            private val TYPE_1_FLAGS = NtlmFlags.NTLMSSP_NEGOTIATE_56 or
                    NtlmFlags.NTLMSSP_NEGOTIATE_128 or
                    NtlmFlags.NTLMSSP_NEGOTIATE_NTLM2 or
                    NtlmFlags.NTLMSSP_NEGOTIATE_ALWAYS_SIGN or
                    NtlmFlags.NTLMSSP_REQUEST_TARGET
        }
    }
}
