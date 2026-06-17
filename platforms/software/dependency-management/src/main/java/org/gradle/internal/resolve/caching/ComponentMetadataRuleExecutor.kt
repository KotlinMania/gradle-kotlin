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
import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleDescriptorHashModuleSource
import org.gradle.cache.internal.InMemoryCacheController
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.serialize.Serializer
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.util.internal.BuildCommencedTimeProvider
import java.time.Duration
import java.util.Optional
import java.util.function.Function
import java.util.function.Supplier

@ServiceScope(Scope.Build::class)
class ComponentMetadataRuleExecutor(
    cacheBuilderFactory: GlobalScopedCacheBuilderFactory,
    cacheDecoratorFactory: InMemoryCacheDecoratorFactory?,
    snapshotter: ValueSnapshotter?,
    timeProvider: BuildCommencedTimeProvider,
    @JvmField val componentMetadataContextSerializer: Serializer<ModuleComponentResolveMetadata?>?
) : CrossBuildCachingRuleExecutor<ModuleComponentResolveMetadata?, ComponentMetadataContext?, ModuleComponentResolveMetadata?>(
    CACHE_ID, cacheBuilderFactory, cacheDecoratorFactory, snapshotter, timeProvider, createValidator(timeProvider),
    keyToSnapshotableTransformer,
    componentMetadataContextSerializer
) {
    private class SimpleResolvedModuleVersion(private val identifier: ModuleVersionIdentifier) : ResolvedModuleVersion {
        override fun getId(): ModuleVersionIdentifier {
            return identifier
        }
    }

    companion object {
        private const val CACHE_ID = "md-rule"

        @JvmStatic
        fun isMetadataRuleExecutorCache(controller: InMemoryCacheController): Boolean {
            return CACHE_ID == controller.getCacheId()
        }

        private val keyToSnapshotableTransformer: Transformer<Any?, ModuleComponentResolveMetadata?>
            get() = Transformer { moduleMetadata: ModuleComponentResolveMetadata? ->
                moduleMetadata!!.sources.withSource<ModuleDescriptorHashModuleSource?, String?>(
                    ModuleDescriptorHashModuleSource::class.java,
                    Function { source: Optional<ModuleDescriptorHashModuleSource?>? ->
                        source!!.map<String?>(Function { metadataFileSource: ModuleDescriptorHashModuleSource? ->
                            metadataFileSource!!.descriptorHash.toString() + moduleMetadata.getVariantDerivationStrategy().javaClass.getName()
                        })
                            .orElseThrow<RuntimeException?>(Supplier { RuntimeException("Cannot find original content hash") })
                    })
            }

        private fun createValidator(timeProvider: BuildCommencedTimeProvider): EntryValidator<ModuleComponentResolveMetadata> {
            return EntryValidator { policy: CacheExpirationControl?, entry: CachedEntry<ModuleComponentResolveMetadata>? ->
                val age = Duration.ofMillis(timeProvider.getCurrentTime() - entry!!.getTimestamp())
                val result = entry.getResult()
                !policy!!.moduleExpiry(SimpleResolvedModuleVersion(result.getModuleVersionId()), age, result.isChanging()).isMustCheck()
            }
        }
    }
}
