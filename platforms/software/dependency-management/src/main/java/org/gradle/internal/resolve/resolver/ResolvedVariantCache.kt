/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.internal.resolve.resolver

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry
import org.gradle.internal.component.model.VariantResolveMetadata
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function

/**
 * Cache for [ResolvedVariant] instances.
 *
 * This cache contains ResolvedVariants for the entire build tree.
 */
@ServiceScope(Scope.BuildTree::class)
class ResolvedVariantCache {
    private val cache: MutableMap<CacheKey, ResolvedVariant> = ConcurrentHashMap<CacheKey, ResolvedVariant>()

    /**
     * Get the resolved variant associated with the key, if present.
     *
     * @return null if the corresponding value is not present in the cache
     */
    fun get(key: CacheKey): ResolvedVariant? {
        return cache.get(key)
    }

    /**
     * Caches resolved variants created by the given function.
     *
     * @return the resolved variant created by the function or a cached instance, if available
     */
    fun computeIfAbsent(key: CacheKey, mappingFunction: Function<CacheKey, ResolvedVariant>): ResolvedVariant {
        return cache.computeIfAbsent(key, mappingFunction)
    }

    /**
     * A cache key for the resolved variant cache that includes the artifact type registry that
     * transforms the attributes of the variant based on its artifacts. The registry is necessary here,
     * as the artifact type registry in each consuming project may be different, resulting in a
     * different computed attributes set for any given producer variant.
     *
     *
     * We key on the registry since the attribute transformation process is expensive. A registry
     * will always produce the same attributes for a given variant, so we are able to use the
     * variant and the registry as a composite key.
     */
    class CacheKey(val variantIdentifier: VariantResolveMetadata.Identifier, val artifactTypeRegistry: ImmutableArtifactTypeRegistry) {
        private val hashCode: Int

        init {
            this.hashCode = computeHashCode(variantIdentifier, artifactTypeRegistry)
        }

        override fun hashCode(): Int {
            return hashCode
        }

        //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
        override fun equals(obj: Any): Boolean {
            if (obj === this) {
                return true
            }
            if (obj == null || obj.javaClass != javaClass) {
                return false
            }
            val other = obj as CacheKey
            return variantIdentifier == other.variantIdentifier &&  // Artifact type registries are interned
                    artifactTypeRegistry === other.artifactTypeRegistry
        }

        companion object {
            private fun computeHashCode(
                variantIdentifier: VariantResolveMetadata.Identifier,
                artifactTypeRegistry: ImmutableArtifactTypeRegistry
            ): Int {
                return 31 * variantIdentifier.hashCode() + artifactTypeRegistry.hashCode()
            }
        }
    }
}
