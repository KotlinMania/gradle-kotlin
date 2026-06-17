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
package org.gradle.internal.resolve.resolver

import com.google.common.collect.ImmutableList
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactBackedResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry
import org.gradle.internal.Describables
import org.gradle.internal.component.external.model.DefaultImmutableCapability
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata
import org.gradle.internal.component.model.DefaultVariantMetadata
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.component.model.VariantResolveMetadata
import java.util.function.Function

class DefaultVariantArtifactResolver(
    private val artifactResolver: ArtifactResolver,
    private val artifactTypeRegistry: ImmutableArtifactTypeRegistry,
    private val resolvedVariantCache: ResolvedVariantCache
) : VariantArtifactResolver {
    override fun resolveAdhocVariant(component: ComponentArtifactResolveMetadata, sourceVariantId: VariantIdentifier, artifacts: ImmutableList<out ComponentArtifactMetadata>): ResolvedVariant {
        val identifier: VariantResolveMetadata.Identifier? = if (artifacts.size == 1)
            SingleArtifactVariantIdentifier(artifacts.iterator().next().getId())
        else
            null

        val adhoc: VariantResolveMetadata = DefaultVariantMetadata(
            "adhoc",
            identifier,
            Describables.of("adhoc variant for", component.getId()),
            component.getAttributes(),
            artifacts,
            ImmutableCapabilities.EMPTY
        )

        return resolveVariantArtifactSet(component, sourceVariantId, adhoc)
    }

    override fun resolveVariantArtifactSet(component: ComponentArtifactResolveMetadata, sourceVariantId: VariantIdentifier, variantArtifacts: VariantResolveMetadata): ResolvedVariant {
        // TODO #31538: In order to apply the artifact type registry, we need to realize the artifacts now, earlier than we should.
        // Since the artifact type registry must be applied before artifact selection, which occurs before task dependencies
        // execute, and since the artifact type registry is a function of the artifacts themselves, which are only known after task
        // dependencies execute, the artifact type registry is inherently flawed. It must be deprecated and removed.

        val artifacts = variantArtifacts.artifacts

        val artifactSetId = variantArtifacts.identifier
        if (artifactSetId == null || !variantArtifacts.isEligibleForCaching()) {
            return createResolvedVariant(artifactSetId, sourceVariantId, component, variantArtifacts, artifactTypeRegistry, artifacts)
        }

        // We use the artifact type registry as a key here, since for each consumer the registry may be different.
        // The registry is interned and is safe to be used as a cache key. Ideally, we would do away with the concept of the
        // artifact type registry entirely, as by design it means we need to look at the artifacts of a variant in order to perform
        // artifact selection -- a process that occurs before artifact files are even created.
        val key = ResolvedVariantCache.CacheKey(artifactSetId, artifactTypeRegistry)

        // Try first without locking
        val value = resolvedVariantCache.get(key)
        if (value != null) {
            return value
        }

        // Calculate the value with locking
        return resolvedVariantCache.computeIfAbsent(
            key,
            Function { k: ResolvedVariantCache.CacheKey? -> createResolvedVariant(k!!.variantIdentifier, sourceVariantId, component, variantArtifacts, k.artifactTypeRegistry, artifacts) }
        )
    }

    private fun createResolvedVariant(
        identifier: VariantResolveMetadata.Identifier?,
        sourceVariantId: VariantIdentifier,
        component: ComponentArtifactResolveMetadata,
        artifactVariant: VariantResolveMetadata,
        artifactTypeRegistry: ImmutableArtifactTypeRegistry,
        artifacts: ImmutableList<out ComponentArtifactMetadata>
    ): ResolvedVariant {
        val attributes = artifactTypeRegistry.mapAttributesFor(artifactVariant.attributes, artifacts)
        val capabilities: ImmutableCapabilities = withImplicitCapability(artifactVariant.getCapabilities(), component)

        // TODO: This value gets cached in a build-tree-scoped cache. It captures a project-scoped `artifactResolver`, which
        // is bound to the repositories of the consumer. That means subsequent resolutions of this artifact from a different
        // project will use the same resolver as the first resolution -- leading us to use repositories from another project
        // when resolving artifacts in this project. We disallow caching of external artifacts above with `isEligibleForCaching`,
        // but if we enable caching for external artifacts, we need to make sure that the `artifactResolver` is included as a
        // cache key or is provided later on during artifact resolution.

        // Better yet, the state required to resolve a variant (the `artifactResolver`) should be captured in the original
        // component artifact metadata, meaning the variant identity is tied to where it is resolved from. This way, we do
        // not need to track the resolver separately, and we can ensure the resolver that resolved a component's metadata is
        // the same one that resolves its artifacts. This would benefit greatly from "repository deduplication", where we could
        // consider repositories from multiple projects as equivalent as long as they are configured the same (same url, cache policy,
        // component metadata rules, metadata sources, etc.). We should probably leverage ComponentArtifactResolveMetadata#getSources() for this.
        return ArtifactBackedResolvedVariant(
            identifier,
            sourceVariantId,
            artifactVariant.asDescribable(),
            attributes,
            capabilities,
            artifacts,
            DefaultComponentArtifactResolver(component, artifactResolver)
        )
    }

    /**
     * Identifier for adhoc variants with a single artifact
     */
    private class SingleArtifactVariantIdentifier(private val artifactIdentifier: ComponentArtifactIdentifier) : VariantResolveMetadata.Identifier {
        override fun hashCode(): Int {
            return artifactIdentifier.hashCode()
        }

        override fun equals(obj: Any): Boolean {
            if (obj === this) {
                return true
            }
            if (obj == null || obj.javaClass != javaClass) {
                return false
            }
            val other = obj as SingleArtifactVariantIdentifier
            return artifactIdentifier == other.artifactIdentifier
        }
    }

    companion object {
        private fun withImplicitCapability(capabilities: ImmutableCapabilities, component: ComponentArtifactResolveMetadata): ImmutableCapabilities {
            // TODO: This doesn't seem right. We should know the capability of the variant before we get here instead of assuming that it's the same as the owner
            if (capabilities.asSet().isEmpty()) {
                return ImmutableCapabilities.of(DefaultImmutableCapability.defaultCapabilityForComponent(component.getModuleVersionId()))
            } else {
                return capabilities
            }
        }
    }
}
