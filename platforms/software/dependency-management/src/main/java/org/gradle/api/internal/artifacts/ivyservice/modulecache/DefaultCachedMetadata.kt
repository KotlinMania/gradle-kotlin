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
package org.gradle.api.internal.artifacts.ivyservice.modulecache

import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.DefaultResolvedModuleVersion
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.model.ModuleSources
import org.gradle.util.internal.BuildCommencedTimeProvider
import java.time.Duration
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.Volatile

internal class DefaultCachedMetadata private constructor(private val ageMillis: Long, private val metadata: ModuleComponentResolveMetadata?) : ModuleMetadataCache.CachedMetadata {
    @Volatile
    private var processedMetadataByRules: MutableMap<Int?, ExternalModuleComponentGraphResolveState?>? = null

    constructor(entry: ModuleMetadataCacheEntry, metadata: ModuleComponentResolveMetadata?, timeProvider: BuildCommencedTimeProvider) : this(
        timeProvider.getCurrentTime() - entry.createTimestamp,
        metadata
    )

    override fun isMissing(): Boolean {
        return metadata == null
    }

    override fun getModuleSources(): ModuleSources {
        return metadata!!.sources
    }

    override fun getModuleVersion(): ResolvedModuleVersion? {
        return if (isMissing()) null else DefaultResolvedModuleVersion(getMetadata()!!.moduleVersionId)
    }

    override fun getMetadata(): ModuleComponentResolveMetadata? {
        return metadata
    }

    override fun getAge(): Duration? {
        return Duration.ofMillis(ageMillis)
    }

    override fun getProcessedMetadata(key: Int): ExternalModuleComponentGraphResolveState? {
        if (processedMetadataByRules != null) {
            return processedMetadataByRules!!.get(key)
        }
        return null
    }

    @Synchronized
    override fun putProcessedMetadata(hash: Int, processed: ExternalModuleComponentGraphResolveState?) {
        if (processedMetadataByRules == null) {
            processedMetadataByRules = Collections.singletonMap<Int?, ExternalModuleComponentGraphResolveState?>(hash, processed)
            return
        } else if (processedMetadataByRules!!.size == 1) {
            processedMetadataByRules = ConcurrentHashMap<Int?, ExternalModuleComponentGraphResolveState?>(processedMetadataByRules)
        }
        processedMetadataByRules!!.put(hash, processed)
    }

    override fun dehydrate(): ModuleMetadataCache.CachedMetadata {
        if (metadata == null) {
            return this
        }
        val copy = this.metadata.asMutable()

        val asImmutable = copy!!.asImmutable()
        return DefaultCachedMetadata(ageMillis, asImmutable)
    }
}
