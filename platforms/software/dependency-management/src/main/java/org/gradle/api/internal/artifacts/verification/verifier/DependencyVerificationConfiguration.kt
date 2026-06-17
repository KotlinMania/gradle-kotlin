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
import org.gradle.api.internal.artifacts.verification.exceptions.InvalidGpgKeyIdsException
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.jspecify.annotations.NullMarked
import java.lang.Boolean
import java.net.URI
import java.nio.charset.StandardCharsets
import kotlin.Any
import kotlin.Comparable
import kotlin.Int
import kotlin.String

@NullMarked
class DependencyVerificationConfiguration(
    val isVerifyMetadata: Boolean,
    val isVerifySignatures: Boolean,
    trustedArtifacts: MutableList<TrustedArtifact>,
    val isUseKeyServers: Boolean,
    val keyServers: MutableList<URI>,
    val ignoredKeys: MutableSet<IgnoredKey>,
    val trustedKeys: MutableList<TrustedKey>,
    val keyringFormat: KeyringFormat?
) {
    val trustedArtifacts: MutableList<TrustedArtifact>

    init {
        this.trustedArtifacts = ImmutableList.copyOf<TrustedArtifact>(trustedArtifacts)
    }

    abstract class TrustCoordinates internal constructor(val group: String?, val name: String?, val version: String?, val fileName: String?, val isRegex: Boolean, val reason: String?) {
        fun matches(id: ModuleComponentArtifactIdentifier): Boolean {
            val moduleComponentIdentifier = id.getComponentIdentifier()
            return matches(group, moduleComponentIdentifier.getGroup())
                    && matches(name, moduleComponentIdentifier.getModule())
                    && matches(version, moduleComponentIdentifier.getVersion())
                    && matches(fileName, id.fileName!!)
        }

        private fun matches(value: String?, expr: String): Boolean {
            if (value == null) {
                return true
            }
            if (!this.isRegex) {
                return expr == value
            }
            return expr.matches(value.toRegex())
        }

        override fun equals(o: Any): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as TrustCoordinates

            if (this.isRegex != that.isRegex) {
                return false
            }
            if (group != that.group) {
                return false
            }
            if (name != that.name) {
                return false
            }
            if (version != that.version) {
                return false
            }
            if (fileName != that.fileName) {
                return false
            }
            return reason == that.reason
        }

        override fun hashCode(): Int {
            var result = if (group != null) group.hashCode() else 0
            result = 31 * result + (if (name != null) name.hashCode() else 0)
            result = 31 * result + (if (version != null) version.hashCode() else 0)
            result = 31 * result + (if (fileName != null) fileName.hashCode() else 0)
            result = 31 * result + (if (this.isRegex) 1 else 0)
            result = 31 * result + (if (reason != null) reason.hashCode() else 0)
            return result
        }

        fun internalCompareTo(other: TrustCoordinates): Int {
            val regexComparison = Boolean.compare(this.isRegex, other.isRegex)
            if (regexComparison != 0) {
                return regexComparison
            }
            val groupComparison: Int = compareNullableStrings(this.group, other.group)
            if (groupComparison != 0) {
                return groupComparison
            }
            val nameComparison: Int = compareNullableStrings(this.name, other.name)
            if (nameComparison != 0) {
                return nameComparison
            }
            val versionComparison: Int = compareNullableStrings(
                this.version,
                other.version
            )
            if (versionComparison != 0) {
                return versionComparison
            }
            val fileNameComparison: Int = compareNullableStrings(
                this.fileName,
                other.fileName
            )
            if (fileNameComparison != 0) {
                return fileNameComparison
            }
            return compareNullableStrings(this.reason, other.reason)
        }
    }

    class TrustedArtifact internal constructor(group: String?, name: String?, version: String?, fileName: String?, regex: kotlin.Boolean, reason: String?) :
        TrustCoordinates(group, name, version, fileName, regex, reason), Comparable<TrustedArtifact> {
        override fun compareTo(other: TrustedArtifact): Int {
            return internalCompareTo(other)
        }
    }

    class TrustedKey internal constructor(keyId: String, group: String?, name: String?, version: String?, fileName: String?, regex: kotlin.Boolean) :
        TrustCoordinates(group, name, version, fileName, regex, null), Comparable<TrustedKey> {
        val keyId: String

        init {
            // The key is 160 bits long, encoded in base32 (case-insensitive characters).
            //
            // Base32 gives us 4 bits per character, so the whole fingerprint will be:
            // (160 bits) / (4 bits / character) = 40 characters
            //
            // By getting ASCII bytes (aka. strictly 1 byte per character, no variable-length magic)
            // we can safely check if the fingerprint is of the correct length.
            if (keyId.toByteArray(StandardCharsets.US_ASCII).size < 40) {
                throw InvalidGpgKeyIdsException(mutableListOf<String>(keyId))
            } else {
                this.keyId = keyId.uppercase()
            }
        }

        override fun equals(o: Any?): kotlin.Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            if (!super.equals(o)) {
                return false
            }

            val that = o as TrustedKey

            return keyId == that.keyId
        }

        override fun hashCode(): Int {
            var result = super.hashCode()
            result = 31 * result + keyId.hashCode()
            return result
        }

        override fun compareTo(other: TrustedKey): Int {
            val keyIdComparison = this.keyId.compareTo(other.keyId)
            if (keyIdComparison != 0) {
                return keyIdComparison
            }
            return internalCompareTo(other)
        }
    }

    enum class KeyringFormat {
        ARMORED,
        BINARY
    }

    companion object {
        private fun compareNullableStrings(first: String?, second: String?): Int {
            if (first == null) {
                if (second == null) {
                    return 0
                } else {
                    return -1
                }
            } else if (second == null) {
                return 1
            }
            return first.compareTo(second)
        }
    }
}
