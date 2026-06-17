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

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ArtifactVerificationOperation
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import java.io.File

internal class ChecksumEntry(id: ModuleComponentArtifactIdentifier, artifactKind: ArtifactVerificationOperation.ArtifactKind, file: File, val checksumKind: ChecksumKind) :
    VerificationEntry(id, artifactKind, file) {
    private val hashCode: Int

    // This field is mutable and is just a performance optimization
    // to avoid creating an extra map in the end, so it does NOT
    // participate in equals/hashcode
    var checksum: String? = null

    init {
        this.hashCode = precomputeHashCode()
    }

    private fun precomputeHashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + getFile().getName().hashCode()
        result = 31 * result + getArtifactKind().hashCode()
        result = 31 * result + checksumKind.hashCode()
        return result
    }

    override fun getOrder(): Int {
        return checksumKind.ordinal
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as ChecksumEntry

        if (id != that.id) {
            return false
        }
        if (getArtifactKind() != that.getArtifactKind()) {
            return false
        }
        if (getFile() != that.getFile()) {
            return false
        }
        return checksumKind == that.checksumKind
    }

    override fun hashCode(): Int {
        return hashCode
    }
}
