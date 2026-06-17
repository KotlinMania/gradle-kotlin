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

import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Stores references to graph elements (components and variants) that are
 * referenced in serialized resolution results.
 */
@ServiceScope(Scope.BuildTree::class)
class ThisBuildTreeOnlyGraphElementStore {
    private val components: Long2ObjectMap<ComponentGraphResolveState> = Long2ObjectMaps.synchronize<ComponentGraphResolveState>(Long2ObjectOpenHashMap<ComponentGraphResolveState?>())
    private val variants: Long2ObjectMap<VariantGraphResolveState> = Long2ObjectMaps.synchronize<VariantGraphResolveState>(Long2ObjectOpenHashMap<VariantGraphResolveState?>())

    /**
     * Stores a reference to the given component if it does not already exist,
     * returning an ID that can be used to retrieve the component later.
     */
    fun storeComponentReference(component: ComponentGraphResolveState): Long {
        require(!component.isAdHoc()) { "Cannot store adhoc component in this build tree only store." }

        val instanceId = component.getInstanceId()
        components.putIfAbsent(instanceId, component)
        return instanceId
    }

    /**
     * Returns the component with the given ID originally stored by
     * [.storeComponentReference].
     *
     * @throws IllegalStateException if no component with the given ID was stored.
     */
    fun getComponent(componentReferenceId: Long): ComponentGraphResolveState {
        val componentGraphResolveState = components.get(componentReferenceId)
        checkNotNull(componentGraphResolveState) { "No component with id " + componentReferenceId + " found." }
        return componentGraphResolveState
    }

    /**
     * Stores a reference to the given variant if it does not already exist,
     * returning an ID that can be used to retrieve the variant later.
     */
    fun storeVariantReference(variant: VariantGraphResolveState): Long {
        val instanceId = variant.getInstanceId()
        variants.putIfAbsent(instanceId, variant)
        return instanceId
    }

    /**
     * Returns the variant with the given ID originally stored by
     * [.storeVariantReference].
     *
     * @throws IllegalStateException if no variant with the given ID was stored.
     */
    fun getVariant(variantReferenceId: Long): VariantGraphResolveState {
        val variantGraphResolveState = variants.get(variantReferenceId)
        checkNotNull(variantGraphResolveState) { "No variant with id " + variantReferenceId + " found." }
        return variantGraphResolveState
    }
}
