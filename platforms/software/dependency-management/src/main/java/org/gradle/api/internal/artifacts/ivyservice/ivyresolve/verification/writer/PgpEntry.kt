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

import com.google.common.collect.Sets
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ArtifactVerificationOperation
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerificationConfiguration
import org.gradle.internal.Factory
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import java.io.File
import java.util.TreeSet
import java.util.concurrent.atomic.AtomicBoolean

internal class PgpEntry(id: ModuleComponentArtifactIdentifier, artifactKind: ArtifactVerificationOperation.ArtifactKind, file: File, signatureFile: Factory<File?>) :
    VerificationEntry(id, artifactKind, file) {
    val signatureFile: Factory<File?>
    val trustedKeys: MutableSet<String> = TreeSet<String>()
    private val requiresChecksums = AtomicBoolean()
    private val failed: MutableSet<String> = Sets.newConcurrentHashSet<String>()
    private val noSignature = AtomicBoolean()
    private val hasSignatureFile = AtomicBoolean()

    // this field is used during "grouping" of entries to tell if we should ignore writing this entry
    private val keysDeclaredGlobally: MutableSet<String> = HashSet<String>()

    init {
        this.signatureFile = org.gradle.internal.Factory {
            val f = signatureFile.create()
            val hasSig = f != null && f.exists()
            hasSignatureFile.set(hasSig)
            if (!hasSig) {
                requiresChecksums.set(true)
            }
            f
        }
    }

    override fun getOrder(): Int {
        return -1
    }

    fun addVerifiedKey(key: String): PgpEntry {
        trustedKeys.add(key)
        return this
    }

    fun fail(keyId: String) {
        requiresChecksums.set(true)
        failed.add(keyId)
    }

    fun missing() {
        requiresChecksums.set(true)
    }

    fun noSignatures() {
        requiresChecksums.set(true)
        noSignature.set(true)
    }

    val isRequiringChecksums: Boolean
        get() = requiresChecksums.get()

    fun isFailed(): Boolean {
        return !failed.isEmpty() || noSignature.get()
    }

    fun getFailed(): MutableSet<String> {
        return failed
    }

    fun keyDeclaredGlobally(keyId: String) {
        keysDeclaredGlobally.add(keyId)
    }

    fun doesNotDeclareKeyGlobally(keyId: String): Boolean {
        return !keysDeclaredGlobally.contains(keyId)
    }

    fun hasArtifactLevelKeys(): Boolean {
        return trustedKeys != keysDeclaredGlobally
    }

    val artifactLevelKeys: MutableSet<String>
        get() {
            val keys: MutableSet<String> = Sets.newHashSet<String>(trustedKeys)
            keys.removeAll(keysDeclaredGlobally)
            return keys
        }

    fun hasSignatureFile(): Boolean {
        return hasSignatureFile.get()
    }

    fun checkAndMarkSatisfiedBy(trustedKey: DependencyVerificationConfiguration.TrustedKey): Boolean {
        if (!trustedKeys.contains(trustedKey.keyId)) {
            return false
        }
        val matches: Boolean = trustedKey.matches(id)
        if (matches) {
            keyDeclaredGlobally(trustedKey.keyId)
        }
        return matches
    }
}
