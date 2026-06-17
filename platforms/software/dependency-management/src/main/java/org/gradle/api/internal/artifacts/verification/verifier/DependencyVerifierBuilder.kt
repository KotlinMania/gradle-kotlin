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
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.verification.exceptions.ComponentVerificationException
import org.gradle.api.internal.artifacts.verification.exceptions.DependencyVerificationException
import org.gradle.api.internal.artifacts.verification.exceptions.InvalidGpgKeyIdsException
import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata
import org.gradle.api.internal.artifacts.verification.model.Checksum
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey
import org.gradle.api.internal.artifacts.verification.model.ImmutableArtifactVerificationMetadata
import org.gradle.api.internal.artifacts.verification.model.ImmutableComponentVerificationMetadata
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.logging.text.TreeFormatter
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Map
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors

class DependencyVerifierBuilder {
    private val byComponent: MutableMap<ModuleComponentIdentifier?, ComponentVerificationsBuilder?> = HashMap<ModuleComponentIdentifier?, ComponentVerificationsBuilder?>()
    val trustedArtifacts: MutableList<DependencyVerificationConfiguration.TrustedArtifact?> = ArrayList<DependencyVerificationConfiguration.TrustedArtifact?>()
    val trustedKeys: MutableSet<DependencyVerificationConfiguration.TrustedKey?> = LinkedHashSet<DependencyVerificationConfiguration.TrustedKey?>()
    val keyServers: MutableList<URI?> = ArrayList<URI?>()
    private val ignoredKeys: MutableSet<IgnoredKey?> = LinkedHashSet<IgnoredKey?>()
    var isVerifyMetadata: Boolean = true
    var isVerifySignatures: Boolean = false
    var isUseKeyServers: Boolean = true
    private val topLevelComments: MutableList<String?> = ArrayList<String?>()
    var keyringFormat: DependencyVerificationConfiguration.KeyringFormat? = null
        private set

    fun setKeyringFormat(newKeyringFormat: String?) {
        this.keyringFormat = parseKeyringFormat(newKeyringFormat)
    }

    private fun parseKeyringFormat(keyringFormat: String?): DependencyVerificationConfiguration.KeyringFormat? {
        if (keyringFormat == null) {
            return null
        }
        try {
            return DependencyVerificationConfiguration.KeyringFormat.valueOf(keyringFormat.uppercase())
        } catch (e: IllegalArgumentException) {
            throw DependencyVerificationException("Invalid keyring format: " + keyringFormat + ". The keyring format should be either 'armored' or 'binary', which determines how keys are stored. Please choose a valid format or leave it unset to generate both.")
        }
    }

    fun addTopLevelComment(comment: String?) {
        topLevelComments.add(comment)
    }

    fun addChecksum(artifact: ModuleComponentArtifactIdentifier, kind: ChecksumKind?, value: String?, origin: String?, reason: String?) {
        val componentIdentifier = artifact.getComponentIdentifier()
        byComponent.computeIfAbsent(componentIdentifier) { component: org.gradle.api.artifacts.component.ModuleComponentIdentifier? -> ComponentVerificationsBuilder(component) }!!
            .addChecksum(artifact, kind, value, origin, reason)
    }

    fun addTrustedKey(artifact: ModuleComponentArtifactIdentifier, key: String) {
        val componentIdentifier = artifact.getComponentIdentifier()
        byComponent.computeIfAbsent(componentIdentifier) { component: org.gradle.api.artifacts.component.ModuleComponentIdentifier? -> ComponentVerificationsBuilder(component) }!!
            .addTrustedKey(artifact, key)
    }

    fun addIgnoredKey(artifact: ModuleComponentArtifactIdentifier, key: IgnoredKey?) {
        val componentIdentifier = artifact.getComponentIdentifier()
        byComponent.computeIfAbsent(componentIdentifier) { component: org.gradle.api.artifacts.component.ModuleComponentIdentifier? -> ComponentVerificationsBuilder(component) }!!
            .addIgnoredKey(artifact, key)
    }

    @JvmOverloads
    fun addTrustedArtifact(group: String?, name: String?, version: String?, fileName: String?, regex: Boolean, reason: String? = null) {
        validateUserInput(group, name, version, fileName)
        trustedArtifacts.add(DependencyVerificationConfiguration.TrustedArtifact(group, name, version, fileName, regex, reason))
    }

    fun addIgnoredKey(keyId: IgnoredKey?) {
        ignoredKeys.add(keyId)
    }

    fun addTrustedKey(keyId: String, group: String?, name: String?, version: String?, fileName: String?, regex: Boolean) {
        validateUserInput(group, name, version, fileName)
        trustedKeys.add(DependencyVerificationConfiguration.TrustedKey(keyId, group, name, version, fileName, regex))
    }

    private fun validateUserInput(group: String?, name: String?, version: String?, fileName: String?) {
        // because this can be called from parsing XML, we need to perform additional verification
        if (group == null && name == null && version == null && fileName == null) {
            throw DependencyVerificationException("A trusted artifact must have at least one of group, name, version or file name not null")
        }
    }

    fun build(): DependencyVerifier {
        val builder = ImmutableMap.builderWithExpectedSize<ModuleComponentIdentifier?, ComponentVerificationMetadata?>(byComponent.size)
        byComponent.entries.stream()
            .sorted(Map.Entry.comparingByKey<ModuleComponentIdentifier?, ComponentVerificationsBuilder?>(MODULE_COMPONENT_IDENTIFIER_COMPARATOR))
            .forEachOrdered { entry: MutableMap.MutableEntry<ModuleComponentIdentifier?, ComponentVerificationsBuilder?>? -> builder.put(entry!!.key, entry.value!!.build()) }
        return DependencyVerifier(
            builder.build(), DependencyVerificationConfiguration(
                isVerifyMetadata,
                isVerifySignatures,
                trustedArtifacts,
                this.isUseKeyServers,
                ImmutableList.copyOf<URI?>(keyServers),
                ImmutableSet.copyOf<IgnoredKey?>(ignoredKeys),
                ImmutableList.copyOf<DependencyVerificationConfiguration.TrustedKey?>(trustedKeys),
                keyringFormat
            ), topLevelComments
        )
    }

    fun addKeyServer(uri: URI?) {
        keyServers.add(uri)
    }

    protected class ComponentVerificationsBuilder(private val component: ModuleComponentIdentifier?) {
        private val byArtifact: MutableMap<String?, ArtifactVerificationBuilder?> = HashMap<String?, ArtifactVerificationBuilder?>()

        fun addChecksum(artifact: ModuleComponentArtifactIdentifier, kind: ChecksumKind?, value: String?, origin: String?, reason: String?) {
            byArtifact.computeIfAbsent(artifact.fileName) { id: kotlin.String? -> org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder.ArtifactVerificationBuilder() }!!
                .addChecksum(kind, value, origin, reason)
        }

        fun addTrustedKey(artifact: ModuleComponentArtifactIdentifier, key: String) {
            byArtifact.computeIfAbsent(artifact.fileName) { id: kotlin.String? -> org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder.ArtifactVerificationBuilder() }!!
                .addTrustedKey(key)
        }

        fun addIgnoredKey(artifact: ModuleComponentArtifactIdentifier, key: IgnoredKey?) {
            byArtifact.computeIfAbsent(artifact.fileName) { id: kotlin.String? -> org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder.ArtifactVerificationBuilder() }!!
                .addIgnoredKey(key)
        }

        fun build(): ComponentVerificationMetadata {
            try {
                return ImmutableComponentVerificationMetadata(
                    component,
                    byArtifact.entries
                        .stream()
                        .map<ArtifactVerificationMetadata?> { entry: MutableMap.MutableEntry<String?, ArtifactVerificationBuilder?>? -> Companion.toArtifactVerification(entry) }
                        .sorted(Comparator.comparing<ArtifactVerificationMetadata?, String?>(Function { obj: ArtifactVerificationMetadata? -> obj!!.artifactName }))
                        .collect(Collectors.toList())
                )
            } catch (ex: InvalidGpgKeyIdsException) {
                throw ComponentVerificationException(component, Consumer { formatter: TreeFormatter? -> ex.formatMessage(formatter) })
            }
        }

        companion object {
            @Throws(InvalidGpgKeyIdsException::class)
            private fun toArtifactVerification(entry: MutableMap.MutableEntry<String?, ArtifactVerificationBuilder>): ArtifactVerificationMetadata {
                val key = entry.key
                val value = entry.value
                return ImmutableArtifactVerificationMetadata(
                    key,
                    value.buildChecksums(),
                    value.buildTrustedPgpKeys(),
                    value.buildIgnoredPgpKeys()
                )
            }
        }
    }

    protected class ArtifactVerificationBuilder {
        private val builder: MutableMap<ChecksumKind?, ChecksumBuilder> = Maps.newEnumMap<ChecksumKind?, ChecksumBuilder?>(ChecksumKind::class.java)
        private val pgpKeys: MutableSet<String?> = LinkedHashSet<String?>()
        private val ignoredPgpKeys: MutableSet<IgnoredKey?> = LinkedHashSet<IgnoredKey?>()

        fun addChecksum(kind: ChecksumKind?, value: String?, origin: String?, reason: String?) {
            val builder = this.builder.computeIfAbsent(kind) { kind: ChecksumKind? -> ChecksumBuilder(kind) }
            builder.addChecksum(value)
            if (origin != null) {
                builder.withOrigin(origin)
            }
            if (reason != null) {
                builder.withReason(reason)
            }
        }

        fun buildChecksums(): MutableList<Checksum?> {
            return builder.values
                .stream()
                .map<Checksum?> { obj: ChecksumBuilder? -> obj!!.build() }
                .sorted(Comparator.comparing<Checksum?, ChecksumKind?>(Function { obj: Checksum? -> obj!!.kind }))
                .collect(Collectors.toList())
        }

        fun addTrustedKey(key: String) {
            pgpKeys.add(key.uppercase())
        }

        fun addIgnoredKey(key: IgnoredKey?) {
            ignoredPgpKeys.add(key)
        }

        /**
         * Builds the list of trusted GPG keys.
         *
         *
         * This method will verify if all the trusted keys are in 160-bit fingerprint format.
         * We do not accept either short or long formats, as they can be vulnerable to collision attacks.
         *
         *
         *
         * Note: the fingerprints' formatting is not verified (i.e. if it's true base32 or not) at this stage.
         * It will happen when these fingerprints will be converted to [org.gradle.security.internal.Fingerprint].
         *
         * @return a set of trusted GPG keys
         * @throws InvalidGpgKeyIdsException if keys not fitting the requirements were found
         */
        @Throws(InvalidGpgKeyIdsException::class)
        fun buildTrustedPgpKeys(): MutableSet<String?> {
            val wrongPgpKeys = pgpKeys
                .stream() // The key is 160 bits long, encoded in base32 (case-insensitive characters).
                //
                // Base32 gives us 4 bits per character, so the whole fingerprint will be:
                // (160 bits) / (4 bits / character) = 40 characters
                //
                // By getting ASCII bytes (aka. strictly 1 byte per character, no variable-length magic)
                // we can safely check if the fingerprint is of the correct length.
                .filter { key: String? -> key!!.toByteArray(StandardCharsets.US_ASCII).size < 40 }
                .collect(Collectors.toList())

            if (wrongPgpKeys.isEmpty()) {
                return pgpKeys
            } else {
                throw InvalidGpgKeyIdsException(wrongPgpKeys)
            }
        }

        fun buildIgnoredPgpKeys(): MutableSet<IgnoredKey?> {
            return ignoredPgpKeys
        }
    }

    private class ChecksumBuilder(private val kind: ChecksumKind?) {
        private var value: String? = null
        private var origin: String? = null
        private var reason: String? = null
        private var alternatives: MutableSet<String?>? = null

        /**
         * Sets the origin, if not set already. This is
         * mostly used for automatic generation of checksums
         */
        fun withOrigin(origin: String?) {
            if (this.origin == null) {
                this.origin = origin
            }
        }

        /**
         * Sets the reason, if not set already.
         */
        fun withReason(reason: String?) {
            if (this.reason == null) {
                this.reason = reason
            }
        }

        fun addChecksum(checksum: String?) {
            if (value == null) {
                value = checksum
            } else if (value != checksum) {
                if (alternatives == null) {
                    alternatives = LinkedHashSet<String?>()
                }
                alternatives!!.add(checksum)
            }
        }

        fun build(): Checksum {
            return Checksum(
                kind,
                value,
                alternatives,
                origin,
                reason
            )
        }
    }

    companion object {
        private val MODULE_COMPONENT_IDENTIFIER_COMPARATOR: Comparator<ModuleComponentIdentifier?> =
            Comparator.comparing<ModuleComponentIdentifier?, String?>(Function { obj: ModuleComponentIdentifier? -> obj!!.getGroup() })
                .thenComparing<String?>(Function { obj: ModuleComponentIdentifier? -> obj!!.getModule() })
                .thenComparing<String?>(Function { obj: ModuleComponentIdentifier? -> obj!!.getVersion() })
    }
}
