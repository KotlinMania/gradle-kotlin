/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.attributes.immutable.artifact

import com.google.common.collect.ImmutableMap
import com.google.common.io.Files
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.internal.artifacts.TransformRegistration
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.model.ComponentArtifactMetadata
import java.io.File
import java.util.function.Consumer

/**
 * Immutable counterpart to [ArtifactTypeRegistry]. Instances should
 * be only created with [ImmutableArtifactTypeRegistryFactory].
 *
 *
 * This class is deeply immutable and thread safe. Instances created with
 * [ImmutableArtifactTypeRegistryFactory] are interned and therefore
 * can be compared with reference equality.
 */
class ImmutableArtifactTypeRegistry(
    private val attributesFactory: AttributesFactory,
    val mappings: ImmutableMap<String, ImmutableAttributes>,
    val defaultArtifactAttributes: ImmutableAttributes
) {
    private val hashCode: Int

    init {
        this.hashCode = computeHashCode(mappings, defaultArtifactAttributes)
    }

    fun visitArtifactTypeAttributes(transformRegistrations: MutableCollection<TransformRegistration>, action: Consumer<in ImmutableAttributes>) {
        // Apply default attributes before visiting
        val visitor: Consumer<in ImmutableAttributes> = Consumer { attributes: ImmutableAttributes ->
            val attributesPlusDefaults = attributesFactory.concat(defaultArtifactAttributes.asImmutable(), attributes)
            action.accept(attributesPlusDefaults)
        }

        val seen: MutableSet<String> = HashSet<String>()
        for (artifactTypeDefinition in mappings.entries) {
            if (seen.add(artifactTypeDefinition.key)) {
                var attributes = artifactTypeDefinition.value
                attributes = attributesFactory.concat(attributesFactory.of<String>(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactTypeDefinition.key), attributes)
                visitor.accept(attributes)
            }
        }

        for (registration in transformRegistrations) {
            val sourceAttributes: AttributeContainerInternal = registration.from
            val format = sourceAttributes.getAttribute<String>(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE)
            if (format != null && seen.add(format)) {
                // Some artifact type that has not already been visited
                val attributes = attributesFactory.of<String>(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, format)
                visitor.accept(attributes)
            }
        }

        if (seen.add(ArtifactTypeDefinition.DIRECTORY_TYPE)) {
            val directory = attributesFactory.of<String>(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE)
            visitor.accept(directory)
        }
    }

    fun mapAttributesFor(file: File): ImmutableAttributes {
        val withoutDefaultAttributes = mapWithoutDefaultAttributesFor(file)
        return attributesFactory.concat(defaultArtifactAttributes.asImmutable(), withoutDefaultAttributes)
    }

    private fun mapWithoutDefaultAttributesFor(file: File): ImmutableAttributes {
        if (file.isDirectory()) {
            return attributesFactory.of<String>(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.DIRECTORY_TYPE)
        } else {
            var attributes = ImmutableAttributes.EMPTY
            val extension = Files.getFileExtension(file.getName())
            attributes = applyForExtension(attributes, extension)
            return attributesFactory.concat(attributesFactory.of<String>(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, extension), attributes)
        }
    }

    fun mapAttributesFor(attributes: ImmutableAttributes, artifacts: Iterable<out ComponentArtifactMetadata>): ImmutableAttributes {
        val withoutDefaultAttributes = mapWithoutDefaultAttributesFor(attributes, artifacts)
        return attributesFactory.concat(defaultArtifactAttributes.asImmutable(), withoutDefaultAttributes)
    }

    private fun mapWithoutDefaultAttributesFor(attributes: ImmutableAttributes, artifacts: Iterable<out ComponentArtifactMetadata>): ImmutableAttributes {
        // Add attributes to be applied given the extension
        var attributes = attributes
        if (!mappings.isEmpty()) {
            var extension: String? = null
            for (artifact in artifacts) {
                val candidateExtension = artifact.getName()!!.extension
                if (extension == null) {
                    extension = candidateExtension
                } else if (extension != candidateExtension) {
                    extension = null
                    break
                }
            }
            if (extension != null) {
                attributes = applyForExtension(attributes, extension)
            }
        }

        // Add artifact format as an implicit attribute when all artifacts have the same format
        if (!attributes.contains(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE)) {
            var format: String? = null
            for (artifact in artifacts) {
                val candidateFormat = artifact.getName()!!.type
                if (format == null) {
                    format = candidateFormat
                } else if (format != candidateFormat) {
                    format = null
                    break
                }
            }
            if (format != null) {
                attributes = attributesFactory.concat<String>(attributes.asImmutable(), ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, format)
            }
        }

        return attributes
    }

    private fun applyForExtension(attributes: ImmutableAttributes, extension: String): ImmutableAttributes {
        var attributes = attributes
        val definition = mappings.get(extension)
        if (definition != null) {
            attributes = attributesFactory.concat(definition, attributes)
        }
        return attributes
    }

    override fun equals(o: Any): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || javaClass != o.javaClass) {
            return false
        }

        val that = o as ImmutableArtifactTypeRegistry
        return mappings == that.mappings &&
                defaultArtifactAttributes == that.defaultArtifactAttributes
    }

    override fun hashCode(): Int {
        return hashCode
    }

    companion object {
        private fun computeHashCode(
            mappings: ImmutableMap<String, ImmutableAttributes>,
            defaultArtifactAttributes: ImmutableAttributes
        ): Int {
            var result = mappings.hashCode()
            result = 31 * result + defaultArtifactAttributes.hashCode()
            return result
        }
    }
}
