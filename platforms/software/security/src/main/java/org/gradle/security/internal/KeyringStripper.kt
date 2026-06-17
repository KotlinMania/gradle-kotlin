/*
 * Copyright 2023 the original author or authors.
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
import org.bouncycastle.bcpg.PublicKeyPacket
import org.bouncycastle.bcpg.TrustPacket
import org.bouncycastle.bcpg.UserIDPacket
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.operator.KeyFingerPrintCalculator
import java.lang.reflect.Constructor
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.StreamSupport

/**
 * A utility class to strip unnecessary information from a keyring
 */
object KeyringStripper {
    private val KEY_CONSTRUCTOR: Constructor<PGPPublicKey>

    private val SUBKEY_CONSTRUCTOR: Constructor<PGPPublicKey>

    init {
        try {
            KEY_CONSTRUCTOR = keyConstructor
            SUBKEY_CONSTRUCTOR = subkeyConstructor
        } catch (e: NoSuchMethodException) {
            throw RuntimeException(e)
        }
    }

    fun strip(keyring: PGPPublicKeyRing, fingerprintCalculator: KeyFingerPrintCalculator?): PGPPublicKeyRing {
        val strippedKeys = StreamSupport
            .stream<PGPPublicKey?>(keyring.spliterator(), false)
            .map<PGPPublicKey?> { key: PGPPublicKey? -> KeyringStripper.stripKey(key!!, fingerprintCalculator) }
            .collect(Collectors.toList())

        return PGPPublicKeyRing(strippedKeys)
    }

    private fun stripKey(key: PGPPublicKey, fingerprintCalculator: KeyFingerPrintCalculator?): PGPPublicKey {
        val stripped: PGPPublicKey
        try {
            if (key.isMasterKey()) {
                val id = PGPUtils.getUserIDs(key)
                    .stream()
                    .filter { obj: String? -> KeyringStripper.looksLikeEmail() }
                    .min(Comparator.comparing<String?, Int?>(Function { obj: String? -> obj!!.length }))

                val ids: MutableList<UserIDPacket?>?
                val idSignatures: MutableList<MutableList<PGPSignature?>?>?
                if (id.isPresent()) {
                    ids = mutableListOf<UserIDPacket?>(UserIDPacket(id.get()))
                    idSignatures = mutableListOf<MutableList<PGPSignature?>?>(mutableListOf<PGPSignature?>())
                } else {
                    ids = mutableListOf<UserIDPacket?>()
                    idSignatures = mutableListOf<MutableList<PGPSignature?>?>()
                }

                // unfortunately, the PGPPublicKey constructor is package private, so we need to use reflection
                stripped = KEY_CONSTRUCTOR.newInstance(
                    key.getPublicKeyPacket(),
                    null,
                    mutableListOf<Any?>(),
                    ids,
                    mutableListOf<Any?>(null),
                    idSignatures,
                    fingerprintCalculator
                )
            } else {
                // unfortunately, the PGPPublicKey subKey constructor is package private, so we need to use reflection
                stripped = SUBKEY_CONSTRUCTOR.newInstance(
                    key.getPublicKeyPacket(),
                    null,
                    ImmutableList.copyOf<PGPSignature?>(key.getKeySignatures()),
                    fingerprintCalculator
                )
            }

            return stripped
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun looksLikeEmail(id: String): Boolean {
        return id.length >= 5 && id.contains("@")
    }

    @get:Throws(NoSuchMethodException::class)
    private val keyConstructor: Constructor<PGPPublicKey>
        get() {
            val constructor = PGPPublicKey::class.java.getDeclaredConstructor(
                PublicKeyPacket::class.java,
                TrustPacket::class.java,
                MutableList::class.java,
                MutableList::class.java,
                MutableList::class.java,
                MutableList::class.java,
                KeyFingerPrintCalculator::class.java
            )
            constructor.setAccessible(true)
            return constructor
        }

    @get:Throws(NoSuchMethodException::class)
    private val subkeyConstructor: Constructor<PGPPublicKey>
        get() {
            val constructor = PGPPublicKey::class.java.getDeclaredConstructor(
                PublicKeyPacket::class.java,
                TrustPacket::class.java,
                MutableList::class.java,
                KeyFingerPrintCalculator::class.java
            )
            constructor.setAccessible(true)
            return constructor
        }
}
