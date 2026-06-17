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

import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import java.lang.reflect.Field
import java.nio.charset.StandardCharsets
import java.util.function.Consumer

object PGPUtils {
    private val KEYS_FIELD: Field = keysField

    /**
     * A custom method to get user ids since original method `PGPPublicKey.getUserIDs()` can fail fast in case user id is not correctly encoded in UTF-8.
     * The original method fails because it is very strict at conversion from bytes to UTF-8 string.
     *
     *
     * Example of dependencies with "broken" public key is `com.google.auto.value:auto-value-annotations`.
     */
    @JvmStatic
    fun getUserIDs(pk: PGPPublicKey): MutableList<String?> {
        val userIds: MutableList<String?> = ArrayList<String?>()
        pk.getRawUserIDs().forEachRemaining(Consumer { id: ByteArray? ->
            userIds.add(kotlin.text.String(id!!, StandardCharsets.UTF_8))
        })
        return userIds
    }

    /**
     * Returns the number of keys in the given keyring.
     * There is no public API to do this, so we use reflection to access the private field.
     * Public keys iterator is not used because it would require O(n) time to count the keys.
     */
    @JvmStatic
    fun getSize(keyring: PGPPublicKeyRing?): Int {
        try {
            return (KEYS_FIELD.get(keyring) as MutableList<PGPPublicKey?>).size
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }

    private val keysField: Field
        get() {
            try {
                val keysField = PGPPublicKeyRing::class.java.getDeclaredField("keys")
                keysField.setAccessible(true)
                return keysField
            } catch (e: NoSuchFieldException) {
                throw RuntimeException(e)
            }
        }
}
