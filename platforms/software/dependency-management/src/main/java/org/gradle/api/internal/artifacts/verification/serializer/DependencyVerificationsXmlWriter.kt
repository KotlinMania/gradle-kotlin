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
package org.gradle.api.internal.artifacts.verification.serializer

import org.gradle.api.internal.artifacts.verification.model.ArtifactVerificationMetadata
import org.gradle.api.internal.artifacts.verification.model.Checksum
import org.gradle.api.internal.artifacts.verification.model.ComponentVerificationMetadata
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerificationConfiguration
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier
import org.gradle.internal.UncheckedException.Companion.throwAsUncheckedException
import org.gradle.internal.xml.SimpleMarkupWriter
import org.gradle.internal.xml.SimpleXmlWriter
import java.io.IOException
import java.io.OutputStream
import java.util.function.Consumer
import java.util.function.Supplier
import java.util.stream.Collectors

class DependencyVerificationsXmlWriter private constructor(out: OutputStream?) {
    private val writer: SimpleXmlWriter

    init {
        this.writer = SimpleXmlWriter(out, SPACES)
    }

    @Throws(IOException::class)
    private fun write(verifier: DependencyVerifier) {
        verifier.topLevelComments!!.forEach(Consumer { comment: String? ->
            try {
                writer.comment(comment)
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
        })
        writer.startElement(DependencyVerificationXmlTags.VERIFICATION_METADATA)
        writeAttribute("xmlns", "https://schema.gradle.org/dependency-verification")
        writeAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
        writeAttribute("xsi:schemaLocation", "https://schema.gradle.org/dependency-verification https://schema.gradle.org/dependency-verification/dependency-verification-1.3.xsd")
        writeConfiguration(verifier.configuration)
        writeVerifications(verifier.getVerificationMetadata())
        writer.endElement()
        writer.close()
    }

    @Throws(IOException::class)
    private fun writeConfiguration(configuration: DependencyVerificationConfiguration) {
        writer.startElement(DependencyVerificationXmlTags.CONFIG)
        writeVerifyMetadata(configuration)
        writeSignatureCheck(configuration)
        writeKeyRingFormat(configuration)
        writeKeyServers(configuration)
        writeTrustedArtifacts(configuration)
        writIgnoredKeys(configuration)
        writeGloballyTrustedKeys(configuration)
        writer.endElement()
    }

    @Throws(IOException::class)
    private fun writeKeyRingFormat(configuration: DependencyVerificationConfiguration) {
        val keyRingFormat = configuration.keyringFormat
        if (keyRingFormat == null) {
            return
        }
        writer.startElement(DependencyVerificationXmlTags.KEYRING_FORMAT)
        writer.write(keyRingFormat.toString().lowercase())
        writer.endElement()
    }

    @Throws(IOException::class)
    private fun writeGloballyTrustedKeys(configuration: DependencyVerificationConfiguration) {
        val keys: MutableList<DependencyVerificationConfiguration.TrustedKey?> = configuration.trustedKeys
        if (keys.isEmpty()) {
            return
        }
        writer.startElement(DependencyVerificationXmlTags.TRUSTED_KEYS)
        val groupedByKeyId = keys
            .stream()
            .collect(Collectors.groupingBy(DependencyVerificationConfiguration.TrustedKey::keyId, Supplier { TreeMap() }, Collectors.toList()))
        for (e in groupedByKeyId.entries) {
            val key = e.key
            val trustedKeys: MutableList<DependencyVerificationConfiguration.TrustedKey?> = e.value
            if (trustedKeys.size == 1) {
                writeTrustedKey(trustedKeys.get(0)!!)
            } else {
                writeGroupedTrustedKey(key, trustedKeys)
            }
        }
        writer.endElement()
    }

    @Throws(IOException::class)
    private fun writeGroupedTrustedKey(keyId: String?, trustedKeys: MutableList<DependencyVerificationConfiguration.TrustedKey?>) {
        writer.startElement(DependencyVerificationXmlTags.TRUSTED_KEY)
        writeAttribute(DependencyVerificationXmlTags.ID, keyId)
        trustedKeys.stream().sorted().forEach { trustedKey: DependencyVerificationConfiguration.TrustedKey? ->
            try {
                writer.startElement(DependencyVerificationXmlTags.TRUSTING)
                writeTrustCoordinates(trustedKey!!)
                writer.endElement()
            } catch (e: IOException) {
                throw throwAsUncheckedException(e)
            }
        }
        writer.endElement()
    }

    @Throws(IOException::class)
    private fun writeTrustedKey(key: DependencyVerificationConfiguration.TrustedKey) {
        writer.startElement(DependencyVerificationXmlTags.TRUSTED_KEY)
        writeAttribute(DependencyVerificationXmlTags.ID, key.keyId)
        writeTrustCoordinates(key)
        writer.endElement()
    }

    @Throws(IOException::class)
    private fun writIgnoredKeys(configuration: DependencyVerificationConfiguration) {
        val ignoredKeys: MutableSet<IgnoredKey?> = configuration.ignoredKeys
        if (!ignoredKeys.isEmpty()) {
            writer.startElement(DependencyVerificationXmlTags.IGNORED_KEYS)
            ignoredKeys.stream().sorted().forEach { ignoredKey: IgnoredKey? ->
                try {
                    writeIgnoredKey(ignoredKey!!)
                } catch (ex: IOException) {
                    throw throwAsUncheckedException(ex)
                }
            }
            writer.endElement()
        }
    }

    @Throws(IOException::class)
    private fun writeIgnoredKey(ignoredKey: IgnoredKey) {
        writer.startElement(DependencyVerificationXmlTags.IGNORED_KEY)
        writeAttribute(DependencyVerificationXmlTags.ID, ignoredKey.getKeyId())
        writeNullableAttribute(DependencyVerificationXmlTags.REASON, ignoredKey.getReason())
        writer.endElement()
    }

    @Throws(IOException::class)
    private fun writeTrustedArtifacts(configuration: DependencyVerificationConfiguration) {
        val trustedArtifacts: MutableList<DependencyVerificationConfiguration.TrustedArtifact?> = configuration.trustedArtifacts
        if (trustedArtifacts.isEmpty()) {
            return
        }
        writer.startElement(DependencyVerificationXmlTags.TRUSTED_ARTIFACTS)
        trustedArtifacts.stream().sorted().forEach { trustedArtifact: DependencyVerificationConfiguration.TrustedArtifact? -> this.writeTrustedArtifact(trustedArtifact!!) }
        writer.endElement()
    }

    private fun writeTrustedArtifact(trustedArtifact: DependencyVerificationConfiguration.TrustedArtifact) {
        try {
            writer.startElement(DependencyVerificationXmlTags.TRUST)
            writeTrustCoordinates(trustedArtifact)
            writer.endElement()
        } catch (e: IOException) {
            throw throwAsUncheckedException(e)
        }
    }

    @Throws(IOException::class)
    private fun writeTrustCoordinates(trustedArtifact: DependencyVerificationConfiguration.TrustCoordinates) {
        writeNullableAttribute(DependencyVerificationXmlTags.GROUP, trustedArtifact.group)
        writeNullableAttribute(DependencyVerificationXmlTags.NAME, trustedArtifact.name)
        writeNullableAttribute(DependencyVerificationXmlTags.VERSION, trustedArtifact.version)
        writeNullableAttribute(DependencyVerificationXmlTags.FILE, trustedArtifact.fileName)
        if (trustedArtifact.isRegex) {
            writeAttribute(DependencyVerificationXmlTags.REGEX, "true")
        }
        writeNullableAttribute(DependencyVerificationXmlTags.REASON, trustedArtifact.reason)
    }

    @Throws(IOException::class)
    private fun writeSignatureCheck(configuration: DependencyVerificationConfiguration) {
        writer.startElement(DependencyVerificationXmlTags.VERIFY_SIGNATURES)
        writer.write(configuration.isVerifySignatures.toString())
        writer.endElement()
    }

    @Throws(IOException::class)
    private fun writeVerifyMetadata(configuration: DependencyVerificationConfiguration) {
        writer.startElement(DependencyVerificationXmlTags.VERIFY_METADATA)
        writer.write(configuration.isVerifyMetadata.toString())
        writer.endElement()
    }

    @Throws(IOException::class)
    private fun writeKeyServers(configuration: DependencyVerificationConfiguration) {
        val keyServers = configuration.keyServers
        if (!keyServers.isEmpty() || !configuration.isUseKeyServers) {
            writer.startElement(DependencyVerificationXmlTags.KEY_SERVERS)
            if (!configuration.isUseKeyServers) {
                writer.attribute(DependencyVerificationXmlTags.ENABLED, "false")
            }
            for (keyServer in keyServers) {
                writer.startElement(DependencyVerificationXmlTags.KEY_SERVER)
                writeAttribute(DependencyVerificationXmlTags.URI, keyServer.toASCIIString())
                writer.endElement()
            }
            writer.endElement()
        }
    }

    @Throws(IOException::class)
    private fun writeAttribute(name: String?, value: String?): SimpleMarkupWriter? {
        return writer.attribute(name, value)
    }

    @Throws(IOException::class)
    private fun writeNullableAttribute(name: String?, value: String?): SimpleMarkupWriter? {
        if (value == null) {
            return writer
        }
        return writeAttribute(name, value)
    }

    @Throws(IOException::class)
    private fun writeVerifications(verifications: MutableCollection<ComponentVerificationMetadata>) {
        writer.startElement(DependencyVerificationXmlTags.COMPONENTS)
        for (verification in verifications) {
            writeVerification(verification)
        }
        writer.endElement()
    }

    @Throws(IOException::class)
    private fun writeVerification(verification: ComponentVerificationMetadata) {
        val mci = verification.getComponentId()
        writer.startElement(DependencyVerificationXmlTags.COMPONENT)
        writeAttribute(DependencyVerificationXmlTags.GROUP, mci.getGroup())
        writeAttribute(DependencyVerificationXmlTags.NAME, mci.getModule())
        writeAttribute(DependencyVerificationXmlTags.VERSION, mci.getVersion())
        writeArtifactVerifications(verification.getArtifactVerifications())
        writer.endElement()
    }

    @Throws(IOException::class)
    private fun writeArtifactVerifications(verifications: MutableList<ArtifactVerificationMetadata>) {
        for (verification in verifications) {
            writeArtifactVerification(verification)
        }
    }

    @Throws(IOException::class)
    private fun writeArtifactVerification(verification: ArtifactVerificationMetadata) {
        val artifact = verification.getArtifactName()
        writer.startElement(DependencyVerificationXmlTags.ARTIFACT)
        writeAttribute(DependencyVerificationXmlTags.NAME, artifact)
        writeTrustedKeys(verification.getTrustedPgpKeys())
        writeIgnoredKeys(verification.getIgnoredPgpKeys())
        writeChecksums(verification.getChecksums())
        writer.endElement()
    }

    @Throws(IOException::class)
    private fun writeIgnoredKeys(ignoredPgpKeys: MutableSet<IgnoredKey>) {
        if (ignoredPgpKeys.isEmpty()) {
            return
        }
        writer.startElement(DependencyVerificationXmlTags.IGNORED_KEYS)
        for (ignoredPgpKey in ignoredPgpKeys) {
            writeIgnoredKey(ignoredPgpKey)
        }
        writer.endElement()
    }

    @Throws(IOException::class)
    private fun writeTrustedKeys(trustedPgpKeys: MutableSet<String?>) {
        for (key in trustedPgpKeys) {
            writer.startElement(DependencyVerificationXmlTags.PGP)
            writeAttribute(DependencyVerificationXmlTags.VALUE, key)
            writer.endElement()
        }
    }

    @Throws(IOException::class)
    private fun writeChecksums(checksums: MutableList<Checksum>) {
        for (checksum in checksums) {
            val kind = checksum.getKind().name
            val value = checksum.getValue()
            writer.startElement(kind)
            writeAttribute(DependencyVerificationXmlTags.VALUE, value)
            val origin = checksum.getOrigin()
            if (origin != null) {
                writeAttribute(DependencyVerificationXmlTags.ORIGIN, origin)
            }
            val reason = checksum.getReason()
            if (reason != null) {
                writeAttribute(DependencyVerificationXmlTags.REASON, reason)
            }
            val alternatives = checksum.getAlternatives()
            if (alternatives != null) {
                for (alternative in alternatives) {
                    writer.startElement(DependencyVerificationXmlTags.ALSO_TRUST)
                    writeAttribute(DependencyVerificationXmlTags.VALUE, alternative)
                    writer.endElement()
                }
            }
            writer.endElement()
        }
    }

    companion object {
        private const val SPACES = "   "

        @JvmStatic
        @Throws(IOException::class)
        fun serialize(verifier: DependencyVerifier, out: OutputStream) {
            try {
                val writer = DependencyVerificationsXmlWriter(out)
                writer.write(verifier)
            } finally {
                out.close()
            }
        }
    }
}
