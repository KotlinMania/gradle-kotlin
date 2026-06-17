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

import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.KeyFingerPrintCalculator
import org.bouncycastle.openpgp.operator.PGPContentVerifierBuilderProvider
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.Security

object SecuritySupport {
    private val LOGGER = getLogger(SecuritySupport::class.java)

    private const val BUFFER = 4096
    const val KEYS_FILE_EXT: String = ".keys"

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.setProperty("crypto.policy", "unlimited")
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @JvmStatic
    @Throws(PGPException::class)
    fun verify(file: File, signature: PGPSignature, publicKey: PGPPublicKey?): Boolean {
        signature.init(createContentVerifier(), publicKey)
        val buffer = ByteArray(BUFFER)
        var len: Int
        try {
            BufferedInputStream(FileInputStream(file)).use { `in` ->
                while ((`in`.read(buffer).also { len = it }) >= 0) {
                    signature.update(buffer, 0, len)
                }
            }
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
        return signature.verify()
    }

    private fun createContentVerifier(): PGPContentVerifierBuilderProvider {
        return BcPGPContentVerifierBuilderProvider()
    }

    @JvmStatic
    fun readSignatures(file: File): PGPSignatureList? {
        try {
            BufferedInputStream(Files.newInputStream(file.toPath())).use { stream ->
                PGPUtil.getDecoderStream(stream).use { decoderStream ->
                    return readSignatureList(decoderStream, file.toString())
                }
            }
        } catch (e: IOException) {
            throw InvalidSignatureFileException(file, e)
        } catch (e: PGPException) {
            throw InvalidSignatureFileException(file, e)
        }
    }

    @Throws(IOException::class, PGPException::class)
    private fun readSignatureList(decoderStream: InputStream?, locationHint: String?): PGPSignatureList? {
        val objectFactory = PGPObjectFactory(decoderStream, BcKeyFingerprintCalculator())
        val nextObject = objectFactory.nextObject()
        if (nextObject is PGPSignatureList) {
            return nextObject
        } else if (nextObject is PGPCompressedData) {
            return readSignatureList(nextObject.getDataStream(), locationHint)
        } else {
            LOGGER!!.warn("Expected a signature list in {}, but got {}. Skipping this signature.", locationHint, if (nextObject == null) "invalid file" else nextObject.javaClass)
            return null
        }
    }

    @JvmStatic
    fun toLongIdHexString(key: Long): String {
        return String.format("%016X", key).trim { it <= ' ' }
    }

    fun toHexString(fingerprint: ByteArray?): String? {
        return Fingerprint.Companion.wrap(fingerprint).toString()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun loadKeyRingFile(keyringFile: File): MutableList<PGPPublicKeyRing?> {
        val existingRings: MutableList<PGPPublicKeyRing?> = ArrayList<PGPPublicKeyRing?>()
        PGPUtil.getDecoderStream(createInputStreamFor(keyringFile)).use { ins ->
            val objectFactory: PGPObjectFactory = JcaPGPObjectFactory(ins)
            val fingerprintCalculator: KeyFingerPrintCalculator = JcaKeyFingerprintCalculator()
            try {
                for (o in objectFactory) {
                    if (o is PGPPublicKeyRing) {
                        // backward compatibility: old keyrings should be stripped too
                        val strippedKeyRing = KeyringStripper.strip(o, fingerprintCalculator)
                        existingRings.add(strippedKeyRing)
                    }
                }
            } catch (e: Exception) {
                LOGGER!!.warn("Error while reading the keyring file. {} keys read: {}", existingRings.size, e.message)
            }
        }
        return existingRings
    }

    @Throws(IOException::class)
    private fun createInputStreamFor(keyringFile: File): InputStream {
        val stream: InputStream = BufferedInputStream(FileInputStream(keyringFile))
        if (keyringFile.getName().endsWith(KEYS_FILE_EXT)) {
            return ArmoredInputStream(stream)
        }
        return stream
    }
}
