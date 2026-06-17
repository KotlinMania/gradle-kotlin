/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.layout

import org.gradle.api.artifacts.repositories.IvyPatternRepositoryLayout
import org.gradle.api.internal.artifacts.repositories.descriptor.IvyRepositoryDescriptor
import java.net.URI

/**
 * A repository layout that uses user-supplied patterns. Each pattern will be appended to the base URI for the repository.
 * At least one artifact pattern must be specified. If no Ivy patterns are specified, then the artifact patterns will be used.
 * Optionally supports a Maven style layout for the 'organisation' part, replacing any dots with forward slashes.
 */
class DefaultIvyPatternRepositoryLayout : AbstractRepositoryLayout(), IvyPatternRepositoryLayout {
    private val artifactPatterns: MutableSet<String?> = LinkedHashSet<String?>()
    private val ivyPatterns: MutableSet<String?> = LinkedHashSet<String?>()
    private var m2compatible = false

    /**
     * {@inheritDoc}
     */
    override fun artifact(pattern: String) {
        artifactPatterns.add(pattern)
    }

    /**
     * {@inheritDoc}
     */
    override fun ivy(pattern: String) {
        ivyPatterns.add(pattern)
    }

    /**
     * {@inheritDoc}
     */
    override fun getM2Compatible(): Boolean {
        return m2compatible
    }

    /**
     * {@inheritDoc}
     */
    override fun setM2compatible(m2compatible: Boolean) {
        this.m2compatible = m2compatible
    }

    override fun apply(baseUri: URI?, builder: IvyRepositoryDescriptor.Builder) {
        builder.setLayoutType("Pattern")
        builder.setM2Compatible(m2compatible)

        for (pattern in artifactPatterns) {
            builder.addArtifactPattern(pattern)
            builder.addArtifactResource(baseUri, pattern)
        }

        for (pattern in ivyPatterns) {
            builder.addIvyPattern(pattern)
        }

        val effectivePatterns = if (ivyPatterns.isEmpty()) artifactPatterns else ivyPatterns
        for (pattern in effectivePatterns) {
            builder.addIvyResource(baseUri, pattern)
        }
    }
}
