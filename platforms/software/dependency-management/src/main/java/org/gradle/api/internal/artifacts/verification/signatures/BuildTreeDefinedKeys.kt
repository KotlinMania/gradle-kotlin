/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerificationConfiguration
import org.gradle.security.internal.KeyringFilePublicKeyService
import org.gradle.security.internal.PublicKeyService
import org.gradle.security.internal.PublicKeyServiceChain.Companion.of
import java.io.File

class BuildTreeDefinedKeys(
    private val keyringsRoot: File?,
    effectiveFormat: DependencyVerificationConfiguration.KeyringFormat?
) {
    private val keyService: KeyringFilePublicKeyService?

    val effectiveKeyringsFile: File?

    init {
        var effectiveFile: File
        if (effectiveFormat == DependencyVerificationConfiguration.KeyringFormat.ARMORED) {
            effectiveFile = this.asciiKeyringsFile
        } else if (effectiveFormat == DependencyVerificationConfiguration.KeyringFormat.BINARY) {
            effectiveFile = this.binaryKeyringsFile
        } else if (effectiveFormat == null) {
            effectiveFile = this.binaryKeyringsFile
            if (!effectiveFile.exists()) {
                effectiveFile = this.asciiKeyringsFile
            }
        } else {
            throw IllegalArgumentException("Unknown keyring format: " + effectiveFormat)
        }

        this.effectiveKeyringsFile = effectiveFile
        if (effectiveFile.exists()) {
            this.keyService = KeyringFilePublicKeyService(effectiveKeyringsFile)
        } else {
            this.keyService = null
        }
    }

    val binaryKeyringsFile: File
        get() = File(keyringsRoot, VERIFICATION_KEYRING_GPG)

    val asciiKeyringsFile: File
        get() = File(keyringsRoot, VERIFICATION_KEYRING_ASCII)

    fun applyTo(original: PublicKeyService?): PublicKeyService? {
        if (keyService != null) {
            return of(keyService, original)
        } else {
            return original
        }
    }

    companion object {
        private const val VERIFICATION_KEYRING_GPG = "verification-keyring.gpg"
        private const val VERIFICATION_KEYRING_ASCII = "verification-keyring.keys"
    }
}
