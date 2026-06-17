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

import com.google.common.collect.ImmutableSet

/**
 * Internal representation of a checksum, aimed at *verification*.
 * A checksum consists of a kind (md5, sha1, ...), a value, but also
 * provides *alternatives*. Alternatives are checksums which are
 * deemed trusted, because sometimes in a single build we can see different
 * checksums for the same module, because they are sourced from different
 * repositories.
 *
 * In theory, this shouldn't be allowed. However, it's often the case that
 * an artifact, in particular _metadata artifacts_ (POM files, ...) differ
 * from one repository to the other (either by end of lines, additional line
 * at the end of the file, ...). Because they are different doesn't mean that
 * they are compromised, so this is a facility for the user to declare "I know
 * I should use a single source of truth but the infrastructure is hard or
 * impossible to fix so let's trust this source".
 *
 * In addition to the list of alternatives, a checksum also provides a source,
 * which is documentation to explain where a checksum was found.
 */
class Checksum(val kind: ChecksumKind, val value: String, alternatives: MutableSet<String?>?, val origin: String?, val reason: String?) {
    val alternatives: MutableSet<String?>?
    private val hashCode: Int

    init {
        this.alternatives = if (alternatives == null) null else ImmutableSet.copyOf<String?>(alternatives)
        this.hashCode = computeHashcode()
    }

    private fun computeHashcode(): Int {
        var result = kind.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + (if (alternatives != null) alternatives.hashCode() else 0)
        result = 31 * result + (if (origin != null) origin.hashCode() else 0)
        result = 31 * result + (if (reason != null) reason.hashCode() else 0)
        return result
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val checksum = o as Checksum

        if (kind != checksum.kind) {
            return false
        }
        if (value != checksum.value) {
            return false
        }
        if (alternatives != checksum.alternatives) {
            return false
        }
        if (origin != checksum.origin) {
            return false
        }
        return reason == checksum.reason
    }

    override fun hashCode(): Int {
        return hashCode
    }
}
