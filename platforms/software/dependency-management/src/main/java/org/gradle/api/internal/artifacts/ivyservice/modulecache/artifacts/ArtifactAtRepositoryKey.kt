/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier

class ArtifactAtRepositoryKey(val repositoryId: String, val artifactId: ComponentArtifactIdentifier) {
    override fun toString(): String {
        return repositoryId + ":" + artifactId
    }

    override fun equals(o: Any?): Boolean {
        if (o !is ArtifactAtRepositoryKey) {
            return false
        }
        val other = o
        return repositoryId == other.repositoryId && artifactId == other.artifactId
    }

    override fun hashCode(): Int {
        return repositoryId.hashCode() xor artifactId.hashCode()
    }
}
