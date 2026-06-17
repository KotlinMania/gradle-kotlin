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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser

import com.google.common.base.Objects
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.apache.commons.lang3.StringUtils
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.capability.CapabilitySelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.capabilities.Capability
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import org.gradle.api.internal.artifacts.capability.DefaultSpecificCapabilitySelector
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultExcludeRuleConverter
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ExcludeRuleConverter
import org.gradle.api.internal.artifacts.repositories.metadata.MavenVariantAttributesFactory
import org.gradle.api.internal.attributes.AttributesFactory
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.ImmutableAttributesEntry
import org.gradle.api.internal.attributes.UsageCompatibilityHandler
import org.gradle.api.internal.capabilities.ImmutableCapability
import org.gradle.api.internal.model.NamedObjectInstantiator
import org.gradle.api.logging.Logging.getLogger
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.component.external.model.MutableComponentVariant
import org.gradle.internal.component.external.model.MutableComponentVariant.attributes
import org.gradle.internal.component.external.model.MutableComponentVariant.capabilities
import org.gradle.internal.component.external.model.MutableComponentVariant.name
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata.attributes
import org.gradle.internal.component.external.model.ShadowedImmutableCapability
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.local.LocallyAvailableExternalResource
import org.gradle.internal.snapshot.impl.CoercingStringValueSnapshot
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashSet
import kotlin.collections.MutableList
import kotlin.collections.MutableSet
import kotlin.collections.copy
import kotlin.collections.isEmpty
import kotlin.collections.mutableListOf
import kotlin.text.format
import kotlin.text.isEmpty
import kotlin.text.lastIndexOf
import kotlin.text.replace
import kotlin.text.substring

class GradleModuleMetadataParser(val attributesFactory: AttributesFactory, moduleIdentifierFactory: ImmutableModuleIdentifierFactory, val instantiator: NamedObjectInstantiator?) {
    private val excludeRuleConverter: ExcludeRuleConverter

    init {
        this.excludeRuleConverter = DefaultExcludeRuleConverter(moduleIdentifierFactory)
    }

    fun parse(resource: LocallyAvailableExternalResource, metadata: MutableModuleComponentResolveMetadata) {
        resource.withContent<Any?>(ExternalResource.ContentAction { inputStream: InputStream? ->
            var version: String? = null
            try {
                val reader = JsonReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
                reader.beginObject()
                if (reader.peek() != JsonToken.NAME) {
                    throw RuntimeException("Module metadata should contain a 'formatVersion' value.")
                }
                val name = reader.nextName()
                if (name != "formatVersion") {
                    throw RuntimeException(String.format("The 'formatVersion' value should be the first value in a module metadata. Found '%s' instead.", name))
                }
                if (reader.peek() != JsonToken.STRING) {
                    throw RuntimeException("The 'formatVersion' value should have a string value.")
                }
                version = reader.nextString()
                consumeTopLevelElements(reader, metadata)
                val file = resource.getFile()
                if (FORMAT_VERSION != version) {
                    LOGGER!!.debug(
                        "Unrecognized metadata format version '{}' found in '{}'. Parsing succeeded but it may lead to unexpected resolution results. Try upgrading to a newer version of Gradle",
                        version,
                        file
                    )
                }
                return@withContent null
            } catch (e: Exception) {
                if (version != null && FORMAT_VERSION != version) {
                    throw MetaDataParseException(
                        "module metadata",
                        resource,
                        String.format("unsupported format version '%s' specified in module metadata. This version of Gradle supports format version %s.", version, FORMAT_VERSION),
                        e
                    )
                }
                throw MetaDataParseException("module metadata", resource, e)
            }
        })
        maybeAddEnforcedPlatformVariant(metadata)
    }

    private fun maybeAddEnforcedPlatformVariant(metadata: MutableModuleComponentResolveMetadata) {
        val variants: MutableList<out MutableComponentVariant?>? = metadata.mutableVariants
        if (variants == null || variants.isEmpty()) {
            return
        }
        for (variant in ImmutableList.copyOf(variants)) {
            val entry: ImmutableAttributesEntry<String?>? = variant.attributes.findEntry<String>(MavenVariantAttributesFactory.CATEGORY_ATTRIBUTE)
            if (entry != null && Category.REGULAR_PLATFORM == entry.getIsolatedValue() && variant.capabilities.isEmpty()) {
                // This generates a synthetic enforced platform variant with the same dependencies, similar to what the Maven variant derivation strategy does
                val enforcedAttributes =
                    attributesFactory.concat<String>(variant.attributes, MavenVariantAttributesFactory.CATEGORY_ATTRIBUTE, CoercingStringValueSnapshot(Category.ENFORCED_PLATFORM, instantiator!!))
                val enforcedCapability = buildShadowPlatformCapability(metadata.id)
                metadata.addVariant(variant.copy("enforced" + StringUtils.capitalize(variant.name), enforcedAttributes, enforcedCapability))
            }
        }
    }

    private fun buildShadowPlatformCapability(componentId: ModuleComponentIdentifier): Capability {
        return ShadowedImmutableCapability(
            DefaultImmutableCapability(
                componentId.getGroup(),
                componentId.getModule(),
                componentId.getVersion()
            ), "-derived-enforced-platform"
        )
    }

    @Throws(IOException::class)
    private fun consumeTopLevelElements(reader: JsonReader, metadata: MutableModuleComponentResolveMetadata) {
        while (reader.peek() != JsonToken.END_OBJECT) {
            val name = reader.nextName()
            when (name) {
                "variants" -> consumeVariants(reader, metadata)
                "component" -> consumeComponent(reader, metadata)
                else -> consumeAny(reader)
            }
        }
    }

    @Throws(IOException::class)
    private fun consumeComponent(reader: JsonReader, metadata: MutableModuleComponentResolveMetadata) {
        reader.beginObject()
        while (reader.peek() != JsonToken.END_OBJECT) {
            val name = reader.nextName()
            when (name) {
                "attributes" -> metadata.attributes = consumeAttributes(reader)
                else -> consumeAny(reader)
            }
        }
        reader.endObject()
    }

    @Throws(IOException::class)
    private fun consumeVariants(reader: JsonReader, metadata: MutableModuleComponentResolveMetadata) {
        reader.beginArray()
        while (reader.peek() != JsonToken.END_ARRAY) {
            consumeVariant(reader, metadata)
        }
        reader.endArray()
    }

    @Throws(IOException::class)
    private fun consumeVariant(reader: JsonReader, metadata: MutableModuleComponentResolveMetadata) {
        var variantName: String? = null
        var attributes = ImmutableAttributes.EMPTY
        var files: MutableList<ModuleFile> = mutableListOf<ModuleFile?>()
        var dependencies: MutableList<ModuleDependency> = mutableListOf<ModuleDependency?>()
        var dependencyConstraints: MutableList<ModuleDependencyConstraint> = mutableListOf<ModuleDependencyConstraint?>()
        var capabilities: MutableList<ImmutableCapability> = mutableListOf<ImmutableCapability?>()
        var availableExternally = false

        reader.beginObject()
        while (reader.peek() != JsonToken.END_OBJECT) {
            val name = reader.nextName()
            when (name) {
                "name" -> variantName = reader.nextString()
                "attributes" -> attributes = consumeAttributes(reader)
                "files" -> files = consumeFiles(reader)
                "dependencies" -> dependencies = consumeDependencies(reader)
                "dependencyConstraints" -> dependencyConstraints = consumeDependencyConstraints(reader)
                "capabilities" -> capabilities = consumeCapabilities(reader, true)
                "available-at" -> {
                    availableExternally = true
                    dependencies = consumeVariantLocation(reader)
                }

                else -> consumeAny(reader)
            }
        }
        assertDefined(reader, "name", variantName)
        reader.endObject()

        val variant = metadata.addVariant(variantName!!, attributes)
        variant!!.isAvailableExternally = availableExternally
        if (availableExternally) {
            if (!dependencyConstraints.isEmpty()) {
                throw RuntimeException("A variant declared with available-at cannot declare dependency constraints")
            }
            if (!files.isEmpty()) {
                throw RuntimeException("A variant declared with available-at cannot declare files")
            }
        }
        populateVariant(files, dependencies, dependencyConstraints, capabilities, variant)
    }

    private fun populateVariant(
        files: MutableList<ModuleFile>,
        dependencies: MutableList<ModuleDependency>,
        dependencyConstraints: MutableList<ModuleDependencyConstraint>,
        capabilities: MutableList<ImmutableCapability>,
        variant: MutableComponentVariant
    ) {
        for (file in files) {
            variant.addFile(file.name, file.uri)
        }
        for (dependency in dependencies) {
            val capabilitySelectors = ImmutableSet.builderWithExpectedSize<CapabilitySelector?>(dependency.requestedCapabilities.size)
            for (requestedCapability in dependency.requestedCapabilities) {
                capabilitySelectors.add(DefaultSpecificCapabilitySelector(requestedCapability))
            }
            variant.addDependency(
                dependency.group,
                dependency.module,
                dependency.versionConstraint,
                dependency.excludes,
                dependency.reason,
                dependency.attributes,
                capabilitySelectors.build(),
                dependency.endorsing,
                dependency.artifact
            )
        }
        for (dependencyConstraint in dependencyConstraints) {
            variant.addDependencyConstraint(
                dependencyConstraint.group,
                dependencyConstraint.module,
                dependencyConstraint.versionConstraint,
                dependencyConstraint.reason,
                dependencyConstraint.attributes
            )
        }
        for (capability in capabilities) {
            variant.addCapability(capability.getGroup(), capability.getName(), capability.getVersion()!!)
        }
    }

    @Throws(IOException::class)
    private fun consumeVariantLocation(reader: JsonReader): MutableList<ModuleDependency> {
        var url: String? = null
        var group: String? = null
        var module: String? = null
        var version: String? = null

        reader.beginObject()
        while (reader.peek() != JsonToken.END_OBJECT) {
            val name = reader.nextName()
            when (name) {
                "url" -> url = reader.nextString()
                "group" -> group = reader.nextString()
                "module" -> module = reader.nextString()
                "version" -> version = reader.nextString()
                else -> consumeAny(reader)
            }
        }
        assertDefined(reader, "url", url)
        assertDefined(reader, "group", group)
        assertDefined(reader, "module", module)
        assertDefined(reader, "version", version)
        reader.endObject()

        return ImmutableList.of<ModuleDependency?>(
            GradleModuleMetadataParser.ModuleDependency(
                group!!,
                module!!,
                DefaultImmutableVersionConstraint(version!!),
                ImmutableList.of<ExcludeMetadata?>(),
                null,
                ImmutableAttributes.EMPTY,
                mutableListOf<ImmutableCapability?>(),
                false,
                null
            )
        )
    }

    /**
     * Consume the dependencies of a given variant.
     *
     *
     * This method needs to remove any duplicates from said dependencies.
     *
     * @param reader The Json to read from
     * @return a list of dependencies
     * @throws IOException when the reader fails
     */
    @Throws(IOException::class)
    private fun consumeDependencies(reader: JsonReader): MutableList<ModuleDependency> {
        val dependencies: MutableSet<ModuleDependency?> = LinkedHashSet<ModuleDependency?>()
        reader.beginArray()
        while (reader.peek() != JsonToken.END_ARRAY) {
            var group: String? = null
            var module: String? = null
            var reason: String? = null
            var attributes = ImmutableAttributes.EMPTY
            var version: VersionConstraint = DefaultImmutableVersionConstraint.of()
            var excludes = ImmutableList.of<ExcludeMetadata?>()
            var requestedCapabilities: MutableList<ImmutableCapability> = ImmutableList.of<ImmutableCapability?>()
            var artifactSelector: IvyArtifactName? = null
            var endorseStrictVersions = false

            reader.beginObject()
            while (reader.peek() != JsonToken.END_OBJECT) {
                val name = reader.nextName()
                when (name) {
                    "group" -> group = reader.nextString()
                    "module" -> module = reader.nextString()
                    "version" -> version = consumeVersion(reader)
                    "excludes" -> excludes = consumeExcludes(reader)
                    "reason" -> reason = reader.nextString()
                    "attributes" -> attributes = consumeAttributes(reader)
                    "requestedCapabilities" -> requestedCapabilities = consumeCapabilities(reader, false)
                    "endorseStrictVersions" -> endorseStrictVersions = reader.nextBoolean()
                    "thirdPartyCompatibility" -> {
                        reader.beginObject()
                        while (reader.peek() != JsonToken.END_OBJECT) {
                            val compatibilityFeatureName = reader.nextName()
                            if (compatibilityFeatureName == "artifactSelector") {
                                artifactSelector = consumeArtifactSelector(reader)
                            } else {
                                consumeAny(reader)
                            }
                        }
                        reader.endObject()
                    }

                    else -> consumeAny(reader)
                }
            }
            assertDefined(reader, "group", group)
            assertDefined(reader, "module", module)
            reader.endObject()

            dependencies.add(GradleModuleMetadataParser.ModuleDependency(group!!, module!!, version, excludes, reason!!, attributes, requestedCapabilities, endorseStrictVersions, artifactSelector))
        }
        reader.endArray()
        return ArrayList<ModuleDependency>(dependencies)
    }

    @Throws(IOException::class)
    private fun consumeArtifactSelector(reader: JsonReader): IvyArtifactName {
        reader.beginObject()
        var artifactName: String? = null
        var type: String? = null
        var extension: String? = null
        var classifier: String? = null
        while (reader.peek() != JsonToken.END_OBJECT) {
            val name = reader.nextName()
            when (name) {
                "name" -> artifactName = reader.nextString()
                "type" -> type = reader.nextString()
                "extension" -> extension = reader.nextString()
                "classifier" -> classifier = reader.nextString()
                else -> consumeAny(reader)
            }
        }
        assertDefined(reader, "name", artifactName)
        assertDefined(reader, "type", type)
        if (extension == null) {
            extension = type
        }
        reader.endObject()
        return DefaultIvyArtifactName(artifactName!!, type!!, extension, classifier)
    }

    @Throws(IOException::class)
    private fun consumeCapabilities(reader: JsonReader, versionRequired: Boolean): MutableList<ImmutableCapability> {
        val capabilities = ImmutableList.builder<ImmutableCapability?>()
        reader.beginArray()
        while (reader.peek() != JsonToken.END_ARRAY) {
            var group: String? = null
            var name: String? = null
            var version: String? = null

            reader.beginObject()
            while (reader.peek() != JsonToken.END_OBJECT) {
                val `val` = reader.nextName()
                when (`val`) {
                    "group" -> group = reader.nextString()
                    "name" -> name = reader.nextString()
                    "version" -> if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        version = reader.nextString()
                    }
                }
            }
            assertDefined(reader, "group", group)
            assertDefined(reader, "name", name)
            if (versionRequired) {
                assertDefined(reader, "version", version)
            }
            reader.endObject()

            capabilities.add(DefaultImmutableCapability(group!!, name!!, version))
        }
        reader.endArray()
        return capabilities.build()
    }

    /**
     * Consume the dependency constraints of a given variant.
     *
     *
     * This method needs to remove any duplicates from said constraints.
     *
     * @param reader The Json to read from
     * @return a list of constraints
     * @throws IOException when the reader fails
     */
    @Throws(IOException::class)
    private fun consumeDependencyConstraints(reader: JsonReader): MutableList<ModuleDependencyConstraint> {
        val dependencies: MutableSet<ModuleDependencyConstraint?> = LinkedHashSet<ModuleDependencyConstraint?>()
        reader.beginArray()
        while (reader.peek() != JsonToken.END_ARRAY) {
            var group: String? = null
            var module: String? = null
            var reason: String? = null
            var version: VersionConstraint = DefaultImmutableVersionConstraint.of()
            var attributes = ImmutableAttributes.EMPTY

            reader.beginObject()
            while (reader.peek() != JsonToken.END_OBJECT) {
                val name = reader.nextName()
                when (name) {
                    "group" -> group = reader.nextString()
                    "module" -> module = reader.nextString()
                    "version" -> version = consumeVersion(reader)
                    "reason" -> reason = reader.nextString()
                    "attributes" -> attributes = consumeAttributes(reader)
                    else -> consumeAny(reader)
                }
            }
            assertDefined(reader, "group", group)
            assertDefined(reader, "module", module)
            reader.endObject()

            dependencies.add(GradleModuleMetadataParser.ModuleDependencyConstraint(group!!, module!!, version, reason!!, attributes))
        }
        reader.endArray()
        return ArrayList<ModuleDependencyConstraint>(dependencies)
    }

    @Throws(IOException::class)
    private fun consumeVersion(reader: JsonReader): ImmutableVersionConstraint {
        var requiredVersion = ""
        var preferredVersion = ""
        var strictVersion = ""
        val rejects: MutableList<String?> = ArrayList<String?>()

        reader.beginObject()
        while (reader.peek() != JsonToken.END_OBJECT) {
            // At this stage, 'strictly' implies 'requires'.
            val cst = reader.nextName()
            when (cst) {
                "prefers" -> preferredVersion = reader.nextString()
                "requires" -> requiredVersion = reader.nextString()
                "strictly" -> strictVersion = reader.nextString()
                "rejects" -> {
                    reader.beginArray()
                    while (reader.peek() != JsonToken.END_ARRAY) {
                        rejects.add(reader.nextString())
                    }
                    reader.endArray()
                }

                else -> consumeAny(reader)
            }
        }
        reader.endObject()
        return DefaultImmutableVersionConstraint.of(preferredVersion, requiredVersion, strictVersion, rejects)
    }

    @Throws(IOException::class)
    private fun consumeExcludes(reader: JsonReader): ImmutableList<ExcludeMetadata?> {
        val builder = ImmutableList.Builder<ExcludeMetadata?>()
        reader.beginArray()
        while (reader.peek() != JsonToken.END_ARRAY) {
            var group: String? = null
            var module: String? = null

            reader.beginObject()
            while (reader.peek() != JsonToken.END_OBJECT) {
                val name = reader.nextName()
                when (name) {
                    "group" -> group = reader.nextString()
                    "module" -> module = reader.nextString()
                    else -> consumeAny(reader)
                }
            }
            reader.endObject()

            val exclude = excludeRuleConverter.createExcludeRule(group!!, module!!)
            builder.add(exclude)
        }
        reader.endArray()
        return builder.build()
    }

    @Throws(IOException::class)
    private fun consumeFiles(reader: JsonReader): MutableList<ModuleFile> {
        val files: MutableList<ModuleFile> = ArrayList<ModuleFile>()
        reader.beginArray()
        while (reader.peek() != JsonToken.END_ARRAY) {
            var fileName: String? = null
            var fileUrl: String? = null

            reader.beginObject()
            while (reader.peek() != JsonToken.END_OBJECT) {
                val name = reader.nextName()
                when (name) {
                    "name" -> fileName = reader.nextString()
                    "url" -> fileUrl = reader.nextString()
                    else -> consumeAny(reader)
                }
            }
            assertDefined(reader, "name", fileName)
            assertDefined(reader, "url", fileUrl)
            reader.endObject()

            files.add(GradleModuleMetadataParser.ModuleFile(fileName!!, fileUrl!!))
        }
        reader.endArray()
        return files
    }

    @Throws(IOException::class)
    private fun consumeAttributes(reader: JsonReader): ImmutableAttributes {
        var libraryElementsValue: String? = null
        var attributes = ImmutableAttributes.EMPTY

        reader.beginObject()
        while (reader.peek() != JsonToken.END_OBJECT) {
            val attrName = reader.nextName()
            if (reader.peek() == JsonToken.BOOLEAN) {
                val attrValue = reader.nextBoolean()
                attributes = attributesFactory.concat<Boolean?>(attributes, Attribute.of<Boolean?>(attrName, Boolean::class.java), attrValue)
            } else if (reader.peek() == JsonToken.NUMBER) {
                val attrValue = reader.nextInt()
                attributes = attributesFactory.concat<Int?>(attributes, Attribute.of<Int?>(attrName, Int::class.java), attrValue)
            } else {
                var attrValue = reader.nextString()
                if (Usage.USAGE_ATTRIBUTE.getName() == attrName) {
                    // Handle potentially legacy Usage values. Unfortunately, "published metadata is forever",
                    // so we need to handle this legacy value for the rest of time.
                    // In the future, it might make sense to handle this in another place, like in a JVM-specific
                    // ecosystem plugin.
                    val legacyUsage = UsageCompatibilityHandler.getReplacementUsage(attrValue)
                    if (legacyUsage != null) {
                        libraryElementsValue = UsageCompatibilityHandler.getLibraryElements(attrValue)
                        attrValue = legacyUsage
                    }
                }
                attributes = attributesFactory.concat<String?>(attributes, Attribute.of<String?>(attrName, String::class.java), CoercingStringValueSnapshot(attrValue!!, instantiator!!))
            }
        }
        reader.endObject()

        // If a legacy Usage value was encountered, only add the corresponding LibraryElements value if there is not already one provided.
        if (libraryElementsValue != null && attributes.findEntry(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.getName()) == null) {
            attributes = attributesFactory.concat<String?>(
                attributes,
                Attribute.of<String?>(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE.getName(), String::class.java),
                CoercingStringValueSnapshot(libraryElementsValue, instantiator!!)
            )
        }

        return attributes
    }

    @Throws(IOException::class)
    private fun consumeAny(reader: JsonReader) {
        reader.skipValue()
    }

    private fun assertDefined(reader: JsonReader, attribute: String?, value: String?) {
        if (StringUtils.isEmpty(value)) {
            val path = reader.getPath()
            // remove leading '$', remove last child segment, use '/' as separator
            throw RuntimeException("missing '" + attribute + "' at " + path.substring(1, path.lastIndexOf('.')).replace('.', '/'))
        }
    }

    private class ModuleFile(val name: String, val uri: String)

    private class ModuleDependency(
        val group: String,
        val module: String,
        val versionConstraint: VersionConstraint,
        val excludes: ImmutableList<ExcludeMetadata?>,
        val reason: String,
        val attributes: ImmutableAttributes,
        val requestedCapabilities: MutableList<ImmutableCapability>,
        val endorsing: Boolean,
        val artifact: IvyArtifactName?
    ) {
        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }

            val that = o as ModuleDependency
            return Objects.equal(group, that.group)
                    && Objects.equal(module, that.module)
                    && Objects.equal(versionConstraint, that.versionConstraint)
                    && Objects.equal(excludes, that.excludes)
                    && Objects.equal(reason, that.reason)
                    && Objects.equal(attributes, that.attributes)
                    && Objects.equal(requestedCapabilities, that.requestedCapabilities)
                    && endorsing == that.endorsing && Objects.equal(artifact, that.artifact)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(group, module, versionConstraint, excludes, reason, attributes, endorsing, artifact)
        }
    }

    private class ModuleDependencyConstraint(val group: String, val module: String, val versionConstraint: VersionConstraint, val reason: String, val attributes: ImmutableAttributes) {
        override fun equals(o: Any?): Boolean {
            if (this === o) {
                return true
            }
            if (o == null || javaClass != o.javaClass) {
                return false
            }
            val that = o as ModuleDependencyConstraint
            return Objects.equal(group, that.group)
                    && Objects.equal(module, that.module)
                    && Objects.equal(versionConstraint, that.versionConstraint)
                    && Objects.equal(reason, that.reason)
                    && Objects.equal(attributes, that.attributes)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(group, module, versionConstraint, reason, attributes)
        }
    }

    companion object {
        private val LOGGER = getLogger(GradleModuleMetadataParser::class.java)

        const val FORMAT_VERSION: String = "1.1"
    }
}
