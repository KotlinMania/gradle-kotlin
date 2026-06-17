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

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.component.model.VariantIdentifier
import org.gradle.internal.component.model.VariantResolveMetadata
import java.util.Objects
import java.util.function.Predicate

/**
 * A [ResolvedVariant] that applies artifact exclusions to a delegate [ResolvedVariant].
 */
class ExcludingVariantArtifactSet(private val delegate: ResolvedVariant, private val moduleId: ModuleIdentifier, private val exclusions: ExcludeSpec) : ResolvedVariant,
    VariantResolveMetadata.Identifier {
    private val id: VariantResolveMetadata.Identifier

    init {
        this.id = ExcludingIdentifier(delegate.identifier, moduleId, exclusions)
    }

    override fun asDescribable(): DisplayName {
        return Describables.of(delegate.asDescribable(), exclusions)
    }

    override fun getIdentifier(): VariantResolveMetadata.Identifier? {
        return id
    }

    override fun getSourceVariantId(): VariantIdentifier {
        return delegate.sourceVariantId
    }

    override fun getAttributes(): ImmutableAttributes {
        return delegate.getAttributes()
    }

    override fun getCapabilities(): ImmutableCapabilities {
        return delegate.capabilities
    }

    override fun getArtifacts(): ResolvedArtifactSet {
        val artifacts = delegate.artifacts
        return FilteringResolvedArtifactSet(artifacts, Predicate { artifact: ResolvableArtifact? -> this.include(artifact!!) })
    }

    private fun include(artifact: ResolvableArtifact): Boolean {
        return !exclusions.excludesArtifact(moduleId, artifact.artifactName)
    }

    private class ExcludingIdentifier(
        private val identifier: VariantResolveMetadata.Identifier?,
        private val moduleId: ModuleIdentifier,
        private val exclusions: ExcludeSpec
    ) : VariantResolveMetadata.Identifier {
        override fun hashCode(): Int {
            var result = Objects.hashCode(identifier)
            result = 31 * result + moduleId.hashCode()
            result = 31 * result + exclusions.hashCode()
            return result
        }

        override fun equals(obj: Any): Boolean {
            if (obj === this) {
                return true
            }
            if (obj == null || obj.javaClass != javaClass) {
                return false
            }
            val other = obj as ExcludingIdentifier
            return areIdsEqual(identifier, other.identifier) &&
                    moduleId == other.moduleId &&
                    exclusions == other.exclusions
        }

        companion object {
            private fun areIdsEqual(
                id1: VariantResolveMetadata.Identifier?,
                id2: VariantResolveMetadata.Identifier?
            ): Boolean {
                // Artifact sets without ID are adhoc.
                // We cannot compare them by ID so assume they are not equal.
                if (id1 == null || id2 == null) {
                    return false
                }

                return id1 == id2
            }
        }
    }
}
