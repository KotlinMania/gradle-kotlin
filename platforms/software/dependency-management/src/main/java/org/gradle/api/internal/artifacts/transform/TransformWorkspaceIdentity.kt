/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.internal.artifacts.transform

import org.gradle.internal.execution.Identity
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.internal.snapshot.ValueSnapshot

internal class TransformWorkspaceIdentity private constructor(val secondaryInputsSnapshot: ValueSnapshot, uniqueId: HashCode) : Identity {
    private val uniqueId: String

    init {
        this.uniqueId = uniqueId.toString()
    }

    override fun getUniqueId(): String {
        return uniqueId
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as TransformWorkspaceIdentity

        return uniqueId == that.uniqueId
    }

    override fun hashCode(): Int {
        return uniqueId.hashCode()
    }

    companion object {
        fun createMutable(
            normalizedInputArtifactPath: String,
            producerBuildTreePath: String,
            secondaryInputsSnapshot: ValueSnapshot,
            dependenciesHash: HashCode
        ): TransformWorkspaceIdentity {
            val hasher = Hashing.newHasher()
            hasher.putString(normalizedInputArtifactPath)
            hasher.putString(producerBuildTreePath)
            hasher.put(secondaryInputsSnapshot)
            hasher.putHash(dependenciesHash)
            return TransformWorkspaceIdentity(secondaryInputsSnapshot, hasher.hash())
        }

        fun createNonNormalizedImmutable(
            inputArtifactPath: ValueSnapshot,
            inputArtifactSnapshot: HashCode,
            secondaryInputsSnapshot: ValueSnapshot,
            dependenciesHash: HashCode
        ): TransformWorkspaceIdentity {
            val hasher = Hashing.newHasher()
            hasher.put(inputArtifactPath)
            hasher.putHash(inputArtifactSnapshot)
            hasher.put(secondaryInputsSnapshot)
            hasher.putHash(dependenciesHash)
            return TransformWorkspaceIdentity(secondaryInputsSnapshot, hasher.hash())
        }

        fun createNormalizedImmutable(
            inputArtifactPath: ValueSnapshot,
            inputArtifactFingerprint: CurrentFileCollectionFingerprint,
            secondaryInputsSnapshot: ValueSnapshot,
            dependenciesHash: HashCode
        ): TransformWorkspaceIdentity {
            val hasher = Hashing.newHasher()
            hasher.put(inputArtifactPath)
            hasher.putHash(inputArtifactFingerprint.getHash())
            hasher.put(secondaryInputsSnapshot)
            hasher.putHash(dependenciesHash)
            return TransformWorkspaceIdentity(secondaryInputsSnapshot, hasher.hash())
        }
    }
}
