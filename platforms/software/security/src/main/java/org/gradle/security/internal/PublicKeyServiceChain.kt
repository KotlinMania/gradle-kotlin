/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.security.internal

import com.google.common.collect.ImmutableList
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.gradle.api.logging.Logging.getLogger

class PublicKeyServiceChain private constructor(private val services: MutableList<PublicKeyService>) : PublicKeyService {
    override fun findByLongId(keyId: Long, builder: PublicKeyResultBuilder) {
        val fmb = FirstMatchBuilder(builder)
        for (service in services) {
            service.findByLongId(keyId, fmb)
            if (fmb.hasResult) {
                return
            }
        }
    }

    override fun findByFingerprint(fingerprint: ByteArray, builder: PublicKeyResultBuilder) {
        val fmb = FirstMatchBuilder(builder)
        for (service in services) {
            service.findByFingerprint(fingerprint, fmb)
            if (fmb.hasResult) {
                return
            }
        }
    }

    override fun close() {
        for (service in services) {
            try {
                service.close()
            } catch (e: Exception) {
                LOGGER!!.warn("Cannot close service", e)
            }
        }
    }

    private class FirstMatchBuilder(private val delegate: PublicKeyResultBuilder) : PublicKeyResultBuilder {
        var hasResult: Boolean = false

        override fun keyRing(keyring: PGPPublicKeyRing) {
            delegate.keyRing(keyring)
            hasResult = true
        }

        override fun publicKey(publicKey: PGPPublicKey) {
            delegate.publicKey(publicKey)
            hasResult = true
        }
    }

    companion object {
        private val LOGGER = getLogger(PublicKeyServiceChain::class.java)

        @JvmStatic
        fun of(vararg delegates: PublicKeyService): PublicKeyService {
            return PublicKeyServiceChain(ImmutableList.copyOf(delegates))
        }
    }
}
