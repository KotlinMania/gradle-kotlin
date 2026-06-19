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

import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.time.ExponentialBackoff.Companion.of
import java.io.File
import java.io.IOException

class KeyringFilePublicKeyService(private val keyRingFile: File) : PublicKeyService {
    private var keys: LoadedKeys? = null

    private fun load(): LoadedKeys {
        synchronized(this) {
            if (keys == null) {
                try {
                    val keyrings = SecuritySupport.loadKeyRingFile(keyRingFile)
                    val keyToKeyringBuilder: MutableMap<Fingerprint, PGPPublicKeyRing> = HashMap()
                    val longIdLongPGPPublicKeyBuilder: ImmutableMultimap.Builder<Long, PGPPublicKeyRing> = ImmutableListMultimap.builder()

                    for (keyring in keyrings) {
                        val it = keyring.getPublicKeys()
                        while (it.hasNext()) {
                            val key = it.next()
                            val fingerprint: Fingerprint = Fingerprint.Companion.of(key)
                            keyToKeyringBuilder.put(fingerprint, keyring)
                            longIdLongPGPPublicKeyBuilder.put(key.getKeyID(), keyring)
                        }
                    }
                    keys = LoadedKeys(ImmutableMap.copyOf(keyToKeyringBuilder), longIdLongPGPPublicKeyBuilder.build())
                    LOGGER!!.info("Loaded {} keys from {}", keys!!.keyToKeyring.size, keyRingFile)
                } catch (e: IOException) {
                    throw throwAsUncheckedException(e)
                }
            }
            return keys!!
        }
    }

    override fun close() {
    }

    override fun findByLongId(keyId: Long, builder: PublicKeyResultBuilder) {
        for (keyring in load().longIdToPublicKeys.get(keyId)) {
            builder.keyRing(keyring)
            val pkIt = keyring.getPublicKeys()
            while (pkIt.hasNext()) {
                val key = pkIt.next()
                if (key.getKeyID() == keyId) {
                    builder.publicKey(key)
                }
            }
        }
    }

    override fun findByFingerprint(bytes: ByteArray, builder: PublicKeyResultBuilder) {
        val fingerprint: Fingerprint = Fingerprint.wrap(bytes)
        val keyring = load().keyToKeyring.get(fingerprint)
        if (keyring != null) {
            builder.keyRing(keyring)
            val pkIt = keyring.getPublicKeys()
            while (pkIt.hasNext()) {
                val key = pkIt.next()
                if (key.getFingerprint().contentEquals(bytes)) {
                    builder.publicKey(key)
                }
            }
        }
    }

    private class LoadedKeys(val keyToKeyring: Map<Fingerprint, PGPPublicKeyRing>, val longIdToPublicKeys: Multimap<Long, PGPPublicKeyRing>)
    companion object {
        private val LOGGER = getLogger(KeyringFilePublicKeyService::class.java)
    }
}
