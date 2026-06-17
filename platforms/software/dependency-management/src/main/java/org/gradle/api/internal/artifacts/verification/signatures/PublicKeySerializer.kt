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
package org.gradle.api.internal.artifacts.verification.signatures

import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import java.io.ByteArrayInputStream

internal class PublicKeySerializer : AbstractSerializer<PGPPublicKey?>() {
    @Throws(Exception::class)
    override fun read(decoder: Decoder): PGPPublicKey? {
        val encoded = decoder.readBinary()
        val objectFactory = PGPObjectFactory(
            PGPUtil.getDecoderStream(ByteArrayInputStream(encoded)), BcKeyFingerprintCalculator()
        )
        val `object` = objectFactory.nextObject()
        if (`object` is PGPPublicKey) {
            return `object`
        } else if (`object` is PGPPublicKeyRing) {
            return `object`.getPublicKey()
        }
        throw IllegalStateException("Unexpected key in cache: " + `object`.javaClass)
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, key: PGPPublicKey) {
        encoder.writeBinary(key.getEncoded())
    }
}
