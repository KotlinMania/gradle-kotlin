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
package org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions

import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator
import org.gradle.cache.IndexedCache
import org.gradle.internal.serialize.AbstractSerializer
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.util.internal.BuildCommencedTimeProvider

open class DefaultModuleVersionsCache(
    timeProvider: BuildCommencedTimeProvider?,
    private val artifactCacheLockingManager: ArtifactCacheLockingAccessCoordinator,
    private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory
) : AbstractModuleVersionsCache(timeProvider) {
    private var cache: IndexedCache<ModuleAtRepositoryKey?, ModuleVersionsCacheEntry?>? = null
        get() {
            if (field == null) {
                field = initCache()
            }
            return field
        }

    private fun initCache(): IndexedCache<ModuleAtRepositoryKey?, ModuleVersionsCacheEntry?>? {
        return artifactCacheLockingManager.createCache<ModuleAtRepositoryKey?, ModuleVersionsCacheEntry?>(
            "module-versions",
            ModuleKeySerializer(moduleIdentifierFactory),
            ModuleVersionsCacheEntrySerializer()
        )
    }

    protected override fun store(key: ModuleAtRepositoryKey, entry: ModuleVersionsCacheEntry) {
        this.cache!!.put(key, entry)
    }

    protected override fun get(key: ModuleAtRepositoryKey): ModuleVersionsCacheEntry? {
        return this.cache!!.getIfPresent(key)
    }

    private class ModuleKeySerializer(private val moduleIdentifierFactory: ImmutableModuleIdentifierFactory) : AbstractSerializer<ModuleAtRepositoryKey?>() {
        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: ModuleAtRepositoryKey) {
            encoder.writeString(value.repositoryId)
            encoder.writeString(value.moduleId.getGroup())
            encoder.writeString(value.moduleId.getName())
        }

        @Throws(Exception::class)
        override fun read(decoder: Decoder): ModuleAtRepositoryKey {
            val resolverId = decoder.readString()
            val group = decoder.readString()
            val module = decoder.readString()
            return ModuleAtRepositoryKey(resolverId, moduleIdentifierFactory.module(group!!, module!!))
        }
    }

    private class ModuleVersionsCacheEntrySerializer : AbstractSerializer<ModuleVersionsCacheEntry?>() {
        @Throws(Exception::class)
        override fun write(encoder: Encoder, value: ModuleVersionsCacheEntry) {
            val versions = value.moduleVersionListing
            encoder.writeInt(versions.size)
            for (version in versions) {
                encoder.writeString(version)
            }
            encoder.writeLong(value.createTimestamp)
        }

        @Throws(Exception::class)
        override fun read(decoder: Decoder): ModuleVersionsCacheEntry {
            val size = decoder.readInt()
            val versions: MutableSet<String?> = LinkedHashSet<String?>()
            for (i in 0..<size) {
                versions.add(decoder.readString())
            }
            val createTimestamp = decoder.readLong()
            return ModuleVersionsCacheEntry(versions, createTimestamp)
        }
    }
}
