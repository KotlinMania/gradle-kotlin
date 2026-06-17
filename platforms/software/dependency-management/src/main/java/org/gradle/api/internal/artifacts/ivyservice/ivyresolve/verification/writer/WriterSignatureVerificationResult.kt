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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer

import org.bouncycastle.openpgp.PGPPublicKey
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationResultBuilder
import org.gradle.security.internal.Fingerprint.Companion.of

internal class WriterSignatureVerificationResult(private val ignoredKeys: MutableSet<String>, private val entry: PgpEntry) : SignatureVerificationResultBuilder {
    override fun missingKey(keyId: String) {
        ignoredKeys.add(keyId)
        entry.missing()
    }

    override fun verified(key: PGPPublicKey, trusted: Boolean) {
        val keyId = of(key).toString()
        entry.addVerifiedKey(keyId)
    }

    override fun failed(key: PGPPublicKey) {
        val keyId = of(key).toString()
        entry.fail(keyId)
    }

    override fun ignored(keyId: String) {
        ignoredKeys.add(keyId)
    }

    override fun noSignatures() {
        entry.noSignatures()
    }

    override fun failedToReadSignatureFile(causeDescription: String) {
        entry.noSignatures()
    }
}
