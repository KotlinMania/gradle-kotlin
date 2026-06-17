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
package org.gradle.api.internal.artifacts.verification.verifier

import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.security.internal.PGPUtils.getUserIDs
import org.gradle.security.internal.PublicKeyResultBuilder
import org.gradle.security.internal.PublicKeyService
import java.io.File
import java.lang.String
import java.util.Map
import java.util.TreeSet
import java.util.function.Consumer

class SignatureVerificationFailure(affectedFile: File?, private val signatureFile: File?, val errors: MutableMap<String?, SignatureError?>, private val keyService: PublicKeyService) :
    AbstractVerificationFailure(affectedFile) {
    override fun getSignatureFile(): File? {
        return signatureFile
    }

    override fun explainTo(formatter: TreeFormatter) {
        if (errors.size == 1) {
            val entry = errors.entries.iterator().next()
            formatter.append(toMessage(entry.key, entry.value!!))
            return
        }
        formatter.append("Multiple signature verification errors found")
        formatter.startChildren()
        errors.entries
            .stream()
            .sorted(Map.Entry.comparingByKey<String?, SignatureError?>())
            .forEachOrdered { entry: MutableMap.MutableEntry<String?, SignatureError?>? -> formatter.node(toMessage(entry!!.key, entry.value!!)) }
        formatter.endChildren()
    }

    private fun toMessage(key: String?, value: SignatureError): String {
        val sb = StringBuilder()
        appendError(key, value, sb)
        return sb.toString()
    }

    private fun appendError(keyId: String?, error: SignatureError, sb: StringBuilder) {
        sb.append("Artifact was signed with key '").append(keyId).append("' ")
        val publicKey = error.publicKey
        when (error.kind) {
            FailureKind.PASSED_NOT_TRUSTED -> {
                appendKeyDetails(sb, publicKey!!)
                sb.append("and passed verification but the key isn't in your trusted keys list.")
            }

            FailureKind.FAILED -> {
                appendKeyDetails(sb, publicKey!!)
                sb.append("but signature didn't match")
            }

            FailureKind.MISSING_KEY -> sb.append("but it wasn't found in any key server so it couldn't be verified")
            else -> {}
        }
    }

    enum class FailureKind {
        PASSED_NOT_TRUSTED,
        FAILED,
        IGNORED_KEY,
        MISSING_KEY
    }

    fun appendKeyDetails(sb: StringBuilder, key: PGPPublicKey) {
        keyService.findByFingerprint(key.getFingerprint(), object : PublicKeyResultBuilder {
            override fun keyRing(keyring: PGPPublicKeyRing) {
                val userIds: MutableSet<String?> = TreeSet<String?>()
                collectUserIds(userIds, key)
                keyring.getPublicKeys().forEachRemaining(Consumer { userkey: PGPPublicKey? -> collectUserIds(userIds, userkey!!) })
                if (!userIds.isEmpty()) {
                    sb.append("(")
                }
                sb.append(String.join(", ", userIds))
                if (!userIds.isEmpty()) {
                    sb.append(") ")
                }
            }

            override fun publicKey(publicKey: PGPPublicKey?) {
            }
        })
    }

    private fun collectUserIds(userIds: MutableSet<kotlin.String?>, userkey: PGPPublicKey) {
        userIds.addAll(getUserIDs(userkey))
    }

    class SignatureError(val publicKey: PGPPublicKey?, val kind: FailureKind)
}
