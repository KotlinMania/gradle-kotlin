/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.internal.artifacts.result.DefaultResolvedComponentResult
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedDependencyResult
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import javax.annotation.concurrent.NotThreadSafe

/**
 * A serializer for [ResolvedComponentResult] that is not thread-safe and not reusable.
 */
@NotThreadSafe
class ResolvedComponentResultSerializer(
    private val moduleVersionIdSerializer: Serializer<ModuleVersionIdentifier?>,
    private val componentIdSerializer: Serializer<ComponentIdentifier?>,
    private val componentSelectorSerializer: Serializer<ComponentSelector?>,
    private val resolvedVariantResultSerializer: Serializer<ResolvedVariantResult?>,
    private val componentSelectionReasonSerializer: Serializer<ComponentSelectionReasonInternal?>
) : Serializer<ResolvedComponentResult?> {
    @Throws(Exception::class)
    override fun read(decoder: Decoder): ResolvedComponentResult? {
        throw UnsupportedOperationException()
    }

    @Throws(Exception::class)
    override fun write(encoder: Encoder, value: ResolvedComponentResult) {
        val root = value as DefaultResolvedComponentResult
        val components: MutableMap<ResolvedComponentResult, Int> = HashMap<ResolvedComponentResult, Int>()
        writeComponent(encoder, root, components)
    }

    @Throws(Exception::class)
    private fun writeComponent(encoder: Encoder, component: ResolvedComponentResult, components: MutableMap<ResolvedComponentResult, Int>) {
        var id = components.get(component)
        if (id != null) {
            // Already seen
            encoder.writeSmallInt(id)
            return
        }
        id = components.size
        components.put(component, id)

        encoder.writeSmallInt(id)
        moduleVersionIdSerializer.write(encoder, component.getModuleVersion())
        componentIdSerializer.write(encoder, component.getId())
        componentSelectionReasonSerializer.write(encoder, (component as ResolvedComponentResultInternal).getSelectionReason())
        val allVariants: MutableList<ResolvedVariantResult> = component.availableVariants
        val resolvedVariants: MutableSet<ResolvedVariantResult> = HashSet<ResolvedVariantResult>(component.getVariants())
        encoder.writeSmallInt(allVariants.size)
        for (variant in allVariants) {
            resolvedVariantResultSerializer.write(encoder, variant)
            encoder.writeBoolean(resolvedVariants.contains(variant))
        }
        val dependencies = component.getDependencies()
        encoder.writeSmallInt(dependencies.size)
        for (dependency in dependencies) {
            val successful = dependency is ResolvedDependencyResult
            encoder.writeBoolean(successful)
            if (successful) {
                val dependencyResult = dependency as DefaultResolvedDependencyResult
                componentSelectorSerializer.write(encoder, dependencyResult.getRequested())
                writeComponent(encoder, dependencyResult.getSelected(), components)
            } else {
                val dependencyResult = dependency as DefaultUnresolvedDependencyResult
                componentSelectorSerializer.write(encoder, dependencyResult.getRequested())
            }
        }
    }
}
