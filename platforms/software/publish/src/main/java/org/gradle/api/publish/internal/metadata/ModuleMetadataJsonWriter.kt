/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.publish.internal.metadata

import com.google.gson.stream.JsonWriter
import org.gradle.api.artifacts.ExcludeRule
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser
import org.gradle.internal.hash.ChecksumService
import org.gradle.util.GradleVersion
import org.gradle.util.internal.GUtil
import java.io.File
import java.io.IOException

internal class ModuleMetadataJsonWriter(
    jsonWriter: JsonWriter?,
    private val metadata: ModuleMetadataSpec,
    private val buildId: String?,
    private val checksumService: ChecksumService
) : JsonWriterScope(jsonWriter) {
    @Throws(IOException::class)
    fun write() {
        writeObject(Contents {
            writeFormat()
            writeIdentity()
            writeCreator()
            writeVariants()
        })
    }

    @Throws(IOException::class)
    private fun writeFormat() {
        write("formatVersion", GradleModuleMetadataParser.FORMAT_VERSION)
    }

    @Throws(IOException::class)
    private fun writeIdentity() {
        writeObject("component", Contents {
            val identity = metadata.identity
            if (identity.relativeUrl != null) {
                write("url", identity.relativeUrl)
            }
            writeCoordinates(identity.coordinates)
            writeAttributes(identity.attributes)
        })
    }

    @Throws(IOException::class)
    private fun writeCreator() {
        writeObject("createdBy", Contents {
            writeObject("gradle", Contents {
                write("version", GradleVersion.current().getVersion())
                if (buildId != null) {
                    write("buildId", buildId)
                }
            })
        }
        )
    }

    @Throws(IOException::class)
    private fun writeVariants() {
        val variants = metadata.variants
        if (variants.isEmpty()) {
            return
        }
        writeArray("variants", Contents {
            for (variant in variants) {
                if (variant is ModuleMetadataSpec.LocalVariant) {
                    val local = variant
                    writeObject(Contents {
                        write("name", local.name)
                        writeAttributes(local.attributes)
                        writeDependencies(local.dependencies)
                        writeDependencyConstraints(local.dependencyConstraints)
                        writeArtifacts(local.artifacts)
                        writeCapabilities("capabilities", local.capabilities)
                    })
                    continue
                }
                if (variant is ModuleMetadataSpec.RemoteVariant) {
                    val remote = variant
                    writeObject(Contents {
                        write("name", remote.name)
                        writeAttributes(remote.attributes)
                        writeAvailableAt(remote.availableAt)
                        writeCapabilities("capabilities", remote.capabilities)
                    })
                    continue
                }
                throw IllegalStateException("Unknown variant type: " + variant)
            }
        })
    }

    @Throws(IOException::class)
    private fun writeNonEmptyAttributes(attributes: MutableList<ModuleMetadataSpec.Attribute>) {
        if (!attributes.isEmpty()) {
            writeAttributes(attributes)
        }
    }

    @Throws(IOException::class)
    private fun writeAttributes(attributes: MutableList<ModuleMetadataSpec.Attribute>) {
        writeObject("attributes", Contents {
            for (attribute in attributes) {
                writeAttribute(attribute.name, attribute.value)
            }
        })
    }

    @Throws(IOException::class)
    private fun writeAttribute(name: String?, value: Any?) {
        if (value is Boolean) {
            write(name, value)
        } else if (value is Int) {
            write(name, value)
        } else if (value is String) {
            write(name, value)
        } else {
            throw IllegalArgumentException("value")
        }
    }

    @Throws(IOException::class)
    private fun writeCapabilities(key: String?, capabilities: MutableList<ModuleMetadataSpec.Capability>) {
        if (capabilities.isEmpty()) {
            return
        }
        writeArray(key, Contents {
            for (capability in capabilities) {
                writeObject(Contents {
                    write("group", capability.group)
                    write("name", capability.name)
                    if (capability.version != null) {
                        write("version", capability.version)
                    }
                })
            }
        })
    }

    @Throws(IOException::class)
    private fun writeAvailableAt(availableAt: ModuleMetadataSpec.AvailableAt) {
        writeObject("available-at", Contents {
            write("url", availableAt.url)
            writeCoordinates(availableAt.coordinates)
        })
    }

    @Throws(IOException::class)
    private fun writeCoordinates(coordinates: ModuleVersionIdentifier) {
        write("group", coordinates.getGroup())
        write("module", coordinates.getName())
        write("version", coordinates.getVersion())
    }

    @Throws(IOException::class)
    private fun writeArtifacts(artifacts: MutableList<ModuleMetadataSpec.Artifact>) {
        if (artifacts.isEmpty()) {
            return
        }
        writeArray("files", Contents {
            for (artifact in artifacts) {
                writeObject(Contents {
                    write("name", artifact.name)
                    write("url", artifact.uri)
                    val file = artifact.file
                    write("size", file.length())
                    write("sha512", sha512(file))
                    write("sha256", sha256(file))
                    write("sha1", sha1(file))
                    write("md5", md5(file))
                })
            }
        })
    }

    @Throws(IOException::class)
    private fun writeDependencies(dependencies: MutableList<ModuleMetadataSpec.Dependency>) {
        if (dependencies.isEmpty()) {
            return
        }
        writeArray("dependencies", Contents {
            for (moduleDependency in dependencies) {
                writeObject(Contents {
                    val identifier = moduleDependency.coordinates
                    write("group", identifier.group)
                    write("module", identifier.name)
                    writeVersionConstraint(identifier.version)
                    writeExcludes(moduleDependency.excludeRules)
                    writeNonEmptyAttributes(moduleDependency.attributes)
                    writeCapabilities("requestedCapabilities", moduleDependency.requestedCapabilities)
                    if (moduleDependency.endorseStrictVersions) {
                        write("endorseStrictVersions", true)
                    }
                    if (moduleDependency.reason != null) {
                        write("reason", moduleDependency.reason)
                    }
                    if (moduleDependency.artifactSelector != null) {
                        writeDependencyArtifact(moduleDependency.artifactSelector)
                    }
                })
            }
        })
    }

    @Throws(IOException::class)
    private fun writeVersionConstraint(version: ModuleMetadataSpec.Version?) {
        if (version == null) {
            return
        }
        writeObject("version", Contents {
            if (version.strictly != null) {
                write("strictly", version.strictly)
            }
            if (version.requires != null) {
                write("requires", version.requires)
            }
            if (version.preferred != null) {
                write("prefers", version.preferred)
            }
            if (!version.rejectedVersions.isEmpty()) {
                writeArray("rejects", version.rejectedVersions)
            }
        })
    }

    @Throws(IOException::class)
    private fun writeDependencyArtifact(artifactSelector: ModuleMetadataSpec.ArtifactSelector) {
        writeObject("thirdPartyCompatibility", Contents {
            writeObject("artifactSelector", Contents {
                write("name", artifactSelector.name)
                write("type", artifactSelector.type)
                if (artifactSelector.extension != null) {
                    write("extension", artifactSelector.extension)
                }
                if (artifactSelector.classifier != null) {
                    write("classifier", artifactSelector.classifier)
                }
            })
        }
        )
    }

    @Throws(IOException::class)
    private fun writeDependencyConstraints(constraints: MutableList<ModuleMetadataSpec.DependencyConstraint>) {
        if (constraints.isEmpty()) {
            return
        }
        writeArray("dependencyConstraints", Contents {
            for (constraint in constraints) {
                writeObject(Contents {
                    write("group", constraint.coordinates.group)
                    write("module", constraint.coordinates.name)
                    writeVersionConstraint(constraint.coordinates.version)
                    writeNonEmptyAttributes(constraint.attributes)
                    if (constraint.reason != null) {
                        write("reason", constraint.reason)
                    }
                })
            }
        })
    }

    @Throws(IOException::class)
    private fun writeExcludes(excludeRules: MutableSet<ExcludeRule>) {
        if (excludeRules.isEmpty()) {
            return
        }
        writeArray("excludes", Contents {
            for (excludeRule in excludeRules) {
                writeObject(Contents {
                    write("group", GUtil.elvis<String?>(excludeRule.getGroup(), "*"))
                    write("module", GUtil.elvis<String?>(excludeRule.getModule(), "*"))
                })
            }
        })
    }

    private fun md5(file: File): String {
        return checksumService.md5(file).toString()
    }

    private fun sha1(file: File): String {
        return checksumService.sha1(file).toString()
    }

    private fun sha256(file: File): String {
        return checksumService.sha256(file).toString()
    }

    private fun sha512(file: File): String {
        return checksumService.sha512(file).toString()
    }
}
