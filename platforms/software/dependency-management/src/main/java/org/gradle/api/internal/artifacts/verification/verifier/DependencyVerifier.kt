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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Sets
import org.bouncycastle.openpgp.PGPPublicKey
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ArtifactVerificationOperation
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata
import org.gradle.api.internal.artifacts.verification.model.Checksum
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationResultBuilder
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationService
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.hash.ChecksumService
import org.gradle.internal.hash.HashCode
import org.gradle.security.internal.Fingerprint.Companion.of
import org.gradle.security.internal.PublicKeyService
import java.io.File
import java.util.Map
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors

class DependencyVerifier internal constructor(
    verificationMetadata: MutableMap<ModuleComponentIdentifier?, ComponentVerificationMetadata?>,
    val configuration: DependencyVerificationConfiguration,
    val topLevelComments: MutableList<String?>?
) {
    private val verificationMetadata: MutableMap<String?, ComponentVerificationMetadata?>

    init {
        this.verificationMetadata = verificationMetadata.entries.stream()
            .collect(ImmutableMap.toImmutableMap<MutableMap.MutableEntry<ModuleComponentIdentifier?, ComponentVerificationMetadata?>?, String?, ComponentVerificationMetadata?>(Function { entry: MutableMap.MutableEntry<ModuleComponentIdentifier?, ComponentVerificationMetadata?>? ->
                toStringKey(
                    entry!!.key!!
                )
            }, Function { Map.Entry.value }))
    }

    fun verify(
        checksumService: ChecksumService,
        signatureVerificationService: SignatureVerificationService,
        kind: ArtifactVerificationOperation.ArtifactKind?,
        foundArtifact: ModuleComponentArtifactIdentifier,
        artifactFile: File,
        signatureFile: File?,
        builder: ArtifactVerificationResultBuilder
    ) {
        if (shouldSkipVerification(kind)) {
            return
        }
        performVerification(
            foundArtifact,
            checksumService,
            signatureVerificationService,
            artifactFile,
            signatureFile, ArtifactVerificationResultBuilder { failure: VerificationFailure? ->
                if (isTrustedArtifact(foundArtifact)) {
                    return@performVerification
                }
                builder.failWith(failure)
            })
    }

    private fun shouldSkipVerification(kind: ArtifactVerificationOperation.ArtifactKind?): Boolean {
        return kind == ArtifactVerificationOperation.ArtifactKind.METADATA && !configuration.isVerifyMetadata()
    }

    private fun isTrustedArtifact(id: ModuleComponentArtifactIdentifier): Boolean {
        return configuration.getTrustedArtifacts().stream().anyMatch { artifact: DependencyVerificationConfiguration.TrustedArtifact? -> artifact!!.matches(id) }
    }

    private fun performVerification(
        foundArtifact: ModuleComponentArtifactIdentifier,
        checksumService: ChecksumService,
        signatureVerificationService: SignatureVerificationService,
        file: File,
        signature: File?,
        builder: ArtifactVerificationResultBuilder
    ) {
        if (!file.exists()) {
            builder.failWith(DeletedArtifact(file))
            return
        }
        doVerifyArtifact(foundArtifact, checksumService, signatureVerificationService, file, signature, builder)
    }

    private fun doVerifyArtifact(
        foundArtifact: ModuleComponentArtifactIdentifier,
        checksumService: ChecksumService,
        signatureVerificationService: SignatureVerificationService,
        file: File,
        signature: File?,
        builder: ArtifactVerificationResultBuilder
    ) {
        val publicKeyService = signatureVerificationService.getPublicKeyService()
        val componentVerification: ComponentVerificationMetadata? = verificationMetadata.get(toStringKey(foundArtifact.getComponentIdentifier()))
        if (componentVerification != null) {
            val foundArtifactFileName = foundArtifact.fileName
            val verifications = componentVerification.artifactVerifications
            for (verification in verifications) {
                val verifiedArtifact = verification.artifactName
                if (verifiedArtifact == foundArtifactFileName) {
                    if (signature == null) {
                        // There is no signature file or verify-signature=false
                        if (configuration.isVerifySignatures()) {
                            builder.failWith(MissingSignature(file))
                        }
                    } else {
                        // There is a signature file and verify-signature=true
                        val result = DefaultSignatureVerificationResultBuilder(file, signature)
                        verifySignature(
                            signatureVerificationService,
                            file,
                            signature,
                            allTrustedKeys(foundArtifact, verification.trustedPgpKeys),
                            allIgnoredKeys(verification.ignoredPgpKeys),
                            result
                        )
                        if (result.hasError()) {
                            val error = result.asError(publicKeyService)
                            builder.failWith(error)
                            if (error.isFatal()) {
                                return
                            }
                        } else if (verification.checksums.isEmpty()) {
                            return
                        }
                    }
                    if (verification.checksums.isEmpty()) {
                        builder.failWith(MissingChecksums(file))
                    } else {
                        verifyChecksums(checksumService, file, verification, builder)
                    }
                    return
                }
            }
        }
        if (signature != null) {
            // it's possible that the artifact is not listed explicitly but we can still verify signatures
            val result = DefaultSignatureVerificationResultBuilder(file, signature)
            verifySignature(signatureVerificationService, file, signature, allTrustedKeys(foundArtifact, mutableSetOf<String?>()), allIgnoredKeys(mutableSetOf<IgnoredKey?>()), result)
            if (result.hasError()) {
                val error = result.asError(publicKeyService)
                builder.failWith(error)
                if (error.isFatal()) {
                    return
                }
            } else {
                return
            }
        }
        builder.failWith(MissingChecksums(file))
    }

    private fun toStringKey(moduleComponentIdentifier: ModuleComponentIdentifier): String {
        return moduleComponentIdentifier.getGroup() + ":" + moduleComponentIdentifier.getModule() + ":" + moduleComponentIdentifier.getVersion()
    }

    private fun allTrustedKeys(id: ModuleComponentArtifactIdentifier, artifactSpecificKeys: MutableSet<String?>): MutableSet<String?> {
        if (configuration.getTrustedKeys().isEmpty()) {
            return artifactSpecificKeys
        } else {
            val allKeys: MutableSet<String?> = Sets.newHashSet<String?>(artifactSpecificKeys)
            configuration.getTrustedKeys()
                .stream()
                .filter { trustedKey: DependencyVerificationConfiguration.TrustedKey? -> trustedKey!!.matches(id) }
                .forEach { trustedKey: DependencyVerificationConfiguration.TrustedKey? -> allKeys.add(trustedKey!!.getKeyId()) }
            return allKeys
        }
    }

    private fun allIgnoredKeys(artifactSpecificKeys: MutableSet<IgnoredKey?>): MutableSet<String?> {
        if (configuration.getIgnoredKeys().isEmpty()) {
            return artifactSpecificKeys.stream().map<String?> { obj: IgnoredKey? -> obj!!.keyId }.collect(Collectors.toSet())
        } else {
            if (artifactSpecificKeys.isEmpty()) {
                return configuration.getIgnoredKeys().stream().map<String?> { obj: IgnoredKey? -> obj!!.keyId }.collect(Collectors.toSet())
            }
            val allKeys: MutableSet<String?> = HashSet<String?>()
            artifactSpecificKeys.stream()
                .map<String?> { obj: IgnoredKey? -> obj!!.keyId }
                .forEach { e: String? -> allKeys.add(e) }
            configuration.getIgnoredKeys()
                .stream()
                .map<String?> { obj: IgnoredKey? -> obj!!.keyId }
                .forEach { e: String? -> allKeys.add(e) }
            return allKeys
        }
    }

    private fun verifySignature(
        signatureVerificationService: SignatureVerificationService,
        file: File?,
        signature: File?,
        trustedKeys: MutableSet<String?>?,
        ignoredKeys: MutableSet<String?>?,
        result: SignatureVerificationResultBuilder?
    ) {
        signatureVerificationService.verify(file, signature, trustedKeys, ignoredKeys, result)
    }

    private fun verifyChecksums(checksumService: ChecksumService, file: File, verification: ArtifactVerificationMetadata, builder: ArtifactVerificationResultBuilder) {
        val checksums = verification.checksums
        for (checksum in checksums) {
            verifyChecksum(checksum.kind, file, checksum.value, checksum.alternatives, checksumService, builder)
        }
    }

    fun getVerificationMetadata(): MutableCollection<ComponentVerificationMetadata?> {
        return verificationMetadata.values
    }

    val suggestedWriteFlags: MutableList<String?>
        get() {
            val writeFlags: MutableSet<String?> = LinkedHashSet<String?>()
            if (configuration.isVerifySignatures()) {
                writeFlags.add("pgp")
            }
            getVerificationMetadata().forEach(Consumer { md: ComponentVerificationMetadata? ->
                md!!.artifactVerifications.forEach(Consumer { av: ArtifactVerificationMetadata? ->
                    av!!.checksums.forEach(Consumer { checksum: Checksum? -> writeFlags.add(checksum!!.kind.name) })
                })
            })
            if (mutableSetOf<String?>("pgp") == writeFlags) {
                // need to suggest at least one checksum so we use the most secure
                writeFlags.add("sha512")
            }
            return ImmutableList.copyOf<String?>(writeFlags)
        }

    private class DefaultSignatureVerificationResultBuilder(private val file: File, private val signatureFile: File) : SignatureVerificationResultBuilder {
        private var missingKeys: MutableList<String?>? = null
        private var trustedKeys: MutableList<PGPPublicKey?>? = null
        private var validNotTrusted: MutableList<PGPPublicKey>? = null
        private var failedKeys: MutableList<PGPPublicKey>? = null
        private var ignoredKeys: MutableList<String?>? = null
        private var hasValidSignatures = true
        private var corruptionError: String? = null

        override fun missingKey(keyId: String?) {
            if (missingKeys == null) {
                missingKeys = ArrayList<String?>()
            }
            missingKeys!!.add(keyId)
        }

        override fun verified(key: PGPPublicKey?, trusted: Boolean) {
            if (trusted) {
                if (trustedKeys == null) {
                    trustedKeys = ArrayList<PGPPublicKey?>()
                }
                trustedKeys!!.add(key)
            } else {
                if (validNotTrusted == null) {
                    validNotTrusted = ArrayList<PGPPublicKey>()
                }
                validNotTrusted!!.add(key!!)
            }
        }

        override fun failed(pgpPublicKey: PGPPublicKey?) {
            if (failedKeys == null) {
                failedKeys = ArrayList<PGPPublicKey>()
            }
            failedKeys!!.add(pgpPublicKey!!)
        }

        override fun ignored(keyId: String?) {
            if (ignoredKeys == null) {
                ignoredKeys = ArrayList<String?>()
            }
            ignoredKeys!!.add(keyId)
        }

        override fun noSignatures() {
            hasValidSignatures = false
        }

        override fun failedToReadSignatureFile(causeDescription: String?) {
            corruptionError = causeDescription
        }

        fun hasOnlyIgnoredKeys(): Boolean {
            return ignoredKeys != null && trustedKeys == null && validNotTrusted == null && missingKeys == null && failedKeys == null
        }

        fun asError(publicKeyService: PublicKeyService?): VerificationFailure {
            if (corruptionError != null) {
                return InvalidSignatureFile(file, signatureFile, corruptionError!!)
            }
            if (!hasValidSignatures) {
                return InvalidSignature(file, signatureFile)
            }
            if (hasOnlyIgnoredKeys()) {
                return OnlyIgnoredKeys(file)
            }
            val errors: MutableMap<String?, SignatureVerificationFailure.SignatureError?> = HashMap<String?, SignatureVerificationFailure.SignatureError?>()
            if (missingKeys != null) {
                for (missingKey in missingKeys) {
                    errors.put(missingKey, error(null, SignatureVerificationFailure.FailureKind.MISSING_KEY))
                }
            }
            if (failedKeys != null) {
                for (failedKey in failedKeys) {
                    errors.put(of(failedKey).toString(), error(failedKey, SignatureVerificationFailure.FailureKind.FAILED))
                }
            }
            if (validNotTrusted != null) {
                for (trustedKey in validNotTrusted) {
                    errors.put(of(trustedKey).toString(), error(trustedKey, SignatureVerificationFailure.FailureKind.PASSED_NOT_TRUSTED))
                }
            }
            if (ignoredKeys != null) {
                for (ignoredKey in ignoredKeys) {
                    errors.put(ignoredKey, error(null, SignatureVerificationFailure.FailureKind.IGNORED_KEY))
                }
            }
            return SignatureVerificationFailure(file, signatureFile, ImmutableMap.copyOf<String?, SignatureVerificationFailure.SignatureError?>(errors), publicKeyService)
        }

        fun hasError(): Boolean {
            return failedKeys != null || validNotTrusted != null || missingKeys != null || !hasValidSignatures || corruptionError != null || hasOnlyIgnoredKeys()
        }
    }

    companion object {
        private fun verifyChecksum(algorithm: ChecksumKind, file: File, expected: String, alternatives: MutableSet<String?>?, cache: ChecksumService, builder: ArtifactVerificationResultBuilder) {
            val actualChecksum: String = checksumOf(algorithm, file, cache)
            if (expected == actualChecksum) {
                return
            }
            if (alternatives != null) {
                for (alternative in alternatives) {
                    if (actualChecksum == alternative) {
                        return
                    }
                }
            }
            builder.failWith(ChecksumVerificationFailure(file, algorithm, expected, actualChecksum))
        }

        private fun checksumOf(algorithm: ChecksumKind, file: File, cache: ChecksumService): String {
            var hashValue: HashCode? = null
            when (algorithm) {
                ChecksumKind.md5 -> hashValue = cache.md5(file)
                ChecksumKind.sha1 -> hashValue = cache.sha1(file)
                ChecksumKind.sha256 -> hashValue = cache.sha256(file)
                ChecksumKind.sha512 -> hashValue = cache.sha512(file)
            }
            return hashValue.toString()
        }

        private fun error(key: PGPPublicKey?, kind: SignatureVerificationFailure.FailureKind?): SignatureVerificationFailure.SignatureError {
            return SignatureVerificationFailure.SignatureError(key, kind)
        }
    }
}
