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
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import java.io.File
import java.util.function.Function

internal abstract class VerificationEntry protected constructor(val id: ModuleComponentArtifactIdentifier, val artifactKind: ArtifactVerificationOperation.ArtifactKind, val file: File) :
    Comparable<VerificationEntry> {
    val group: String
        get() = id.getComponentIdentifier().getGroup()

    val module: String
        get() = id.getComponentIdentifier().getModule()

    val version: String
        get() = id.getComponentIdentifier().getVersion()

    abstract val order: Int

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as VerificationEntry

        if (id != that.id) {
            return false
        }
        if (artifactKind != that.artifactKind) {
            return false
        }
        return file == that.file
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + artifactKind.hashCode()
        result = 31 * result + file.hashCode()
        return result
    }

    override fun compareTo(other: VerificationEntry): Int {
        return ENTRY_COMPARATOR.compare(this, other)
    }

    companion object {
        private val ENTRY_COMPARATOR: Comparator<VerificationEntry> = Comparator.comparing<VerificationEntry, String>(Function { obj: VerificationEntry -> obj.group })
            .thenComparing<String>(Function { obj: VerificationEntry -> obj.module })
            .thenComparing<String>(Function { obj: VerificationEntry -> obj.version })
            .thenComparing<File>(Function { obj: VerificationEntry -> obj.file })
            .thenComparing<ArtifactVerificationOperation.ArtifactKind>(Function { obj: VerificationEntry -> obj.artifactKind })
            .thenComparing<Int>(Function { obj: VerificationEntry -> obj.order })
    }
}
