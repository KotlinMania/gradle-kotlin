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
package org.gradle.internal.resolve.caching

import org.gradle.api.Transformer
import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.util.internal.BuildCommencedTimeProvider
import java.time.Duration

@ServiceScope(Scope.Build::class)
class ComponentMetadataSupplierRuleExecutor(
    cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
    cacheDecoratorFactory: InMemoryCacheDecoratorFactory?,
    snapshotter: ValueSnapshotter?,
    timeProvider: BuildCommencedTimeProvider,
    componentMetadataSerializer: Serializer<ComponentMetadata?>?
) : CrossBuildCachingRuleExecutor<ModuleVersionIdentifier?, ComponentMetadataSupplierDetails?, ComponentMetadata?>(
    "md-supplier",
    cacheBuilderFactory,
    cacheDecoratorFactory,
    snapshotter,
    timeProvider,
    createValidator(timeProvider),
    KEY_TO_SNAPSHOTTABLE,
    componentMetadataSerializer
) {
    private class SimpleResolvedModuleVersion(private val result: ComponentMetadata) : ResolvedModuleVersion {
        override fun getId(): ModuleVersionIdentifier {
            return result.getId()
        }
    }

    companion object {
        private val KEY_TO_SNAPSHOTTABLE = Transformer { obj: ModuleVersionIdentifier? -> obj.toString() }

        fun createValidator(timeProvider: BuildCommencedTimeProvider): EntryValidator<ComponentMetadata> {
            return EntryValidator { policy: CacheExpirationControl?, entry: CachedEntry<ComponentMetadata>? ->
                val age = Duration.ofMillis(timeProvider.getCurrentTime() - entry!!.getTimestamp())
                val result = entry.getResult()
                !policy!!.moduleExpiry(SimpleResolvedModuleVersion(result), age, result.isChanging()).isMustCheck()
            }
        }
    }
}
