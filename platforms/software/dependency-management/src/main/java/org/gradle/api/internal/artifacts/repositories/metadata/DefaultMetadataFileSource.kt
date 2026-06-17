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
package org.gradle.api.internal.artifacts.repositories.metadata

import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import org.gradle.internal.hash.HashCode
import java.io.File

/**
 * This module source stores information about the original
 * descriptor.
 */
class DefaultMetadataFileSource(private val artifactId: ModuleComponentArtifactIdentifier, private val artifactFile: File, private val sha1: HashCode) : MetadataFileSource {
    override fun getArtifactFile(): File {
        return artifactFile
    }

    override fun getArtifactId(): ModuleComponentArtifactIdentifier {
        return artifactId
    }

    override fun getSha1(): HashCode {
        return sha1
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as DefaultMetadataFileSource

        if (artifactId != that.artifactId) {
            return false
        }
        return sha1 == that.sha1
    }

    override fun hashCode(): Int {
        var result = artifactId.hashCode()
        result = 31 * result + sha1.hashCode()
        return result
    }

    override fun toString(): String {
        return "MetadataFileSource{" +
                "artifactId=" + artifactId +
                ", artifactFile=" + artifactFile +
                ", sha1=" + sha1 +
                '}'
    }
}
