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
package org.gradle.api.internal.artifacts.verification.model

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet

class ImmutableArtifactVerificationMetadata(private val artifactName: String, checksums: MutableList<Checksum?>, trustedPgpKeys: MutableSet<String?>, ignoredPgpKeys: MutableSet<IgnoredKey?>) :
    ArtifactVerificationMetadata {
    private val checksums: MutableList<Checksum?>
    private val trustedPgpKeys: MutableSet<String?>
    private val ignoredPgpKeys: MutableSet<IgnoredKey?>
    private val hashCode: Int

    init {
        this.checksums = ImmutableList.copyOf<Checksum?>(checksums)
        this.trustedPgpKeys = ImmutableSet.copyOf<String?>(trustedPgpKeys)
        this.ignoredPgpKeys = ImmutableSet.copyOf<IgnoredKey?>(ignoredPgpKeys)
        this.hashCode = computeHashCode()
    }

    override fun getArtifactName(): String {
        return artifactName
    }

    override fun getChecksums(): MutableList<Checksum?> {
        return checksums
    }

    override fun getTrustedPgpKeys(): MutableSet<String?> {
        return trustedPgpKeys
    }

    override fun getIgnoredPgpKeys(): MutableSet<IgnoredKey?> {
        return ignoredPgpKeys
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as ImmutableArtifactVerificationMetadata

        if (artifactName != that.artifactName) {
            return false
        }
        if (checksums != that.checksums) {
            return false
        }
        if (ignoredPgpKeys != that.ignoredPgpKeys) {
            return false
        }
        return trustedPgpKeys == that.trustedPgpKeys
    }

    override fun hashCode(): Int {
        return hashCode
    }

    private fun computeHashCode(): Int {
        var result = artifactName.hashCode()
        result = 31 * result + checksums.hashCode()
        result = 31 * result + trustedPgpKeys.hashCode()
        result = 31 * result + ignoredPgpKeys.hashCode()
        return result
    }
}
