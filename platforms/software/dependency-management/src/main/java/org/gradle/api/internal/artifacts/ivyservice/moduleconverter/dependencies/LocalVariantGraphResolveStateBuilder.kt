/*
 * Copyright 2007 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState
import org.gradle.internal.component.model.ExcludeMetadata
import org.gradle.internal.component.model.LocalOriginDependencyMetadata
import org.gradle.internal.model.CalculatedValueContainerFactory
import org.gradle.internal.model.ModelContainer
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.function.Function

/**
 * Builds [LocalVariantGraphResolveMetadata] instances from [ConfigurationInternal]s, while
 * caching intermediary dependency and exclude state.
 */
@ServiceScope(Scope.BuildTree::class)
interface LocalVariantGraphResolveStateBuilder {
    /**
     * Create variant state to be used as a root variant of a dependency graph.
     */
    fun createRootVariantState(
        configuration: ConfigurationInternal,
        componentId: ComponentIdentifier,
        dependencyCache: DependencyCache,
        model: ModelContainer<*>,
        calculatedValueContainerFactory: CalculatedValueContainerFactory
    ): LocalVariantGraphResolveState?

    /**
     * Create variant state to be used as a consumable variant of a component within a dependency graph.
     */
    fun createConsumableVariantState(
        configuration: ConfigurationInternal,
        componentId: ComponentIdentifier,
        dependencyCache: DependencyCache,
        model: ModelContainer<*>,
        calculatedValueContainerFactory: CalculatedValueContainerFactory
    ): LocalVariantGraphResolveState?

    /**
     * A cache of the defined dependencies for dependency configurations. This tracks the cached internal
     * dependency representations for these types so when constructing leaf configuration metadata
     * (resolvable and consumable), these conversions do not need to be executed multiple times.
     */
    class DependencyCache {
        private val cache: MutableMap<String, DependencyState> = HashMap<String, DependencyState>()

        fun computeIfAbsent(
            configuration: ConfigurationInternal,
            factory: Function<ConfigurationInternal, DependencyState>
        ): DependencyState {
            var state = cache.get(configuration.getName())
            if (state == null) {
                state = factory.apply(configuration)
                cache.put(configuration.getName(), state)
            }
            return state
        }

        fun invalidate() {
            cache.clear()
        }
    }

    /**
     * The immutable state of a variant's dependencies and excludes. This type tracks
     * the internal representations, after they have been converted from their DSL representations.
     */
    class DependencyState(
        val dependencies: ImmutableList<LocalOriginDependencyMetadata>,
        val files: ImmutableSet<LocalFileDependencyMetadata>,
        val excludes: ImmutableList<ExcludeMetadata>
    )
}
