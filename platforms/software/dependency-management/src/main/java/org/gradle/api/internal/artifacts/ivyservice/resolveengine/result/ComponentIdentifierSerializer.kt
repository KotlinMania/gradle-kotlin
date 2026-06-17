/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultRootComponentIdentifier
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier
import org.gradle.internal.component.local.model.OpaqueComponentArtifactIdentifier
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import java.io.File
import java.io.IOException

/**
 * A thread-safe and reusable serializer for [ComponentIdentifier].
 */
class ComponentIdentifierSerializer : AbstractSerializer<ComponentIdentifier?>() {
    private val projectIdentitySerializer = ProjectIdentitySerializer(PathSerializer())

    @Throws(IOException::class)
    override fun read(decoder: Decoder): ComponentIdentifier {
        val id = decoder.readByte()
        val implementation: Implementation? = Implementation.Companion.valueOf(id.toInt())
        requireNotNull(implementation) { "Unable to find component identifier type with id: " + id }
        when (implementation) {
            Implementation.PROJECT -> return DefaultProjectComponentIdentifier(projectIdentitySerializer.read(decoder))
            Implementation.MODULE -> return DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(decoder.readString(), decoder.readString()!!), decoder.readString()!!)
            Implementation.SNAPSHOT -> return MavenUniqueSnapshotComponentIdentifier(
                DefaultModuleIdentifier.newId(decoder.readString(), decoder.readString()!!),
                decoder.readString()!!,
                decoder.readString()!!
            )

            Implementation.ROOT_COMPONENT -> {
                val instanceId = decoder.readLong()
                return DefaultRootComponentIdentifier(instanceId)
            }

            Implementation.LIBRARY -> return DefaultLibraryBinaryIdentifier(decoder.readString()!!, decoder.readString()!!, decoder.readString()!!)
            Implementation.OPAQUE -> return OpaqueComponentArtifactIdentifier(File(decoder.readString()))
            Implementation.OPAQUE_NOTATION -> return OpaqueComponentIdentifier(readClassPathNotation(decoder))
            else -> throw IllegalArgumentException("Unsupported component identifier implementation: " + implementation)
        }
    }

    @Throws(IOException::class)
    override fun write(encoder: Encoder, value: ComponentIdentifier) {
        requireNotNull(value) { "Provided component identifier may not be null" }

        val implementation: Implementation = resolveImplementation(value)

        encoder.writeByte(implementation.id)

        when (implementation) {
            Implementation.MODULE -> {
                val moduleComponentIdentifier = value as ModuleComponentIdentifier
                encoder.writeString(moduleComponentIdentifier.getGroup())
                encoder.writeString(moduleComponentIdentifier.getModule())
                encoder.writeString(moduleComponentIdentifier.getVersion())
            }

            Implementation.SNAPSHOT -> {
                val snapshotIdentifier = value as MavenUniqueSnapshotComponentIdentifier
                encoder.writeString(snapshotIdentifier.getGroup())
                encoder.writeString(snapshotIdentifier.getModule())
                encoder.writeString(snapshotIdentifier.getVersion())
                encoder.writeString(snapshotIdentifier.timestamp)
            }

            Implementation.PROJECT -> {
                val projectComponentIdentifier = value as ProjectComponentIdentifierInternal
                projectIdentitySerializer.write(encoder, projectComponentIdentifier.getProjectIdentity())
            }

            Implementation.ROOT_COMPONENT -> {
                val rootComponentIdentifier = value as DefaultRootComponentIdentifier
                encoder.writeLong(rootComponentIdentifier.getInstanceId())
            }

            Implementation.LIBRARY -> {
                val libraryIdentifier = value as LibraryBinaryIdentifier
                encoder.writeString(libraryIdentifier.getProjectPath())
                encoder.writeString(libraryIdentifier.getLibraryName())
                encoder.writeString(libraryIdentifier.getVariant())
            }

            Implementation.OPAQUE -> {
                val opaqueIdentifier = value as OpaqueComponentArtifactIdentifier
                encoder.writeString(opaqueIdentifier.file.getPath())
            }

            Implementation.OPAQUE_NOTATION -> {
                val opaqueIdentifier = value as OpaqueComponentIdentifier
                writeClassPathNotation(encoder, opaqueIdentifier.getClassPathNotation())
            }

            else -> throw IllegalStateException("Unsupported implementation type: " + implementation)
        }
    }

    private enum class Implementation(id: Int) {
        MODULE(1),
        PROJECT(2),
        LIBRARY(6),
        SNAPSHOT(7),
        OPAQUE(8),
        OPAQUE_NOTATION(9),
        ROOT_COMPONENT(10);

        private val id: Byte

        init {
            this.id = id.toByte()
        }

        companion object {
            fun valueOf(id: Int): Implementation? {
                when (id) {
                    1 -> return Implementation.MODULE
                    2 -> return Implementation.PROJECT
                    6 -> return Implementation.LIBRARY
                    7 -> return Implementation.SNAPSHOT
                    8 -> return Implementation.OPAQUE
                    9 -> return Implementation.OPAQUE_NOTATION
                    10 -> return Implementation.ROOT_COMPONENT
                }
                return null
            }
        }
    }

    companion object {
        @Throws(IOException::class)
        private fun writeClassPathNotation(encoder: Encoder, classPathNotation: DependencyFactoryInternal.ClassPathNotation) {
            encoder.writeSmallInt(classPathNotation.ordinal)
        }

        @Throws(IOException::class)
        private fun readClassPathNotation(decoder: Decoder): DependencyFactoryInternal.ClassPathNotation {
            val ordinal = decoder.readSmallInt()
            return DependencyFactoryInternal.ClassPathNotation.org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal.ClassPathNotation.entries.toTypedArray()[ordinal]
        }

        private fun resolveImplementation(value: ComponentIdentifier): Implementation {
            if (value is MavenUniqueSnapshotComponentIdentifier) {
                return Implementation.SNAPSHOT
            } else if (value is ModuleComponentIdentifier) {
                return Implementation.MODULE
            } else if (value is ProjectComponentIdentifierInternal) {
                return Implementation.PROJECT
            } else if (value is DefaultRootComponentIdentifier) {
                return Implementation.ROOT_COMPONENT
            } else if (value is LibraryBinaryIdentifier) {
                return Implementation.LIBRARY
            } else if (value is OpaqueComponentArtifactIdentifier) {
                return Implementation.OPAQUE
            } else if (value is OpaqueComponentIdentifier) {
                return Implementation.OPAQUE_NOTATION
            } else {
                throw IllegalArgumentException("Unsupported component identifier class: " + value.javaClass)
            }
        }
    }
}
