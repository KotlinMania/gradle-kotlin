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

import org.gradle.internal.hash.HashCode
import java.io.File
import java.io.Serializable

class DefaultCachedArtifact : CachedArtifact, Serializable {
    val cachedFile: File?
    val cachedAt: Long
    private val descriptorHash: HashCode?
    private val attemptedLocations: MutableList<String?>?

    constructor(cachedFile: File?, cachedAt: Long, descriptorHash: HashCode?) {
        this.cachedFile = cachedFile
        this.cachedAt = cachedAt
        this.descriptorHash = descriptorHash
        this.attemptedLocations = mutableListOf<String?>()
    }

    constructor(attemptedLocations: MutableList<String?>?, cachedAt: Long, descriptorHash: HashCode?) {
        this.attemptedLocations = attemptedLocations
        this.cachedAt = cachedAt
        this.cachedFile = null
        this.descriptorHash = descriptorHash
    }

    val isMissing: Boolean
        get() = cachedFile == null

    override fun getDescriptorHash(): HashCode? {
        return descriptorHash
    }

    override fun attemptedLocations(): MutableList<String?>? {
        return attemptedLocations
    }
}
