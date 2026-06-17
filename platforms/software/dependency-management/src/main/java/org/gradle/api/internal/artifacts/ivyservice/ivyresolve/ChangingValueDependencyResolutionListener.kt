/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Notified of the use of changing values during dependency resolution, so this can be noted in the configuration cache inputs.
 * These events are not sent through `ListenerManager`.
 */
@ServiceScope(Scope.Build::class)
interface ChangingValueDependencyResolutionListener {
    /**
     * Called when a dynamic version is selected using the set of candidate versions queried from a repository.
     */
    fun onDynamicVersionSelection(requested: ModuleComponentSelector?, expiry: CacheExpirationControl.Expiry?, versions: MutableSet<ModuleVersionIdentifier?>?)

    /**
     * Called when a changing artifact is resolved using the artifact state queried from a repository.
     */
    fun onChangingModuleResolve(moduleId: ModuleComponentIdentifier?, expiry: CacheExpirationControl.Expiry?)

    companion object {
        val NO_OP: ChangingValueDependencyResolutionListener = object : ChangingValueDependencyResolutionListener {
            override fun onDynamicVersionSelection(requested: ModuleComponentSelector?, expiry: CacheExpirationControl.Expiry?, versions: MutableSet<ModuleVersionIdentifier?>?) {
            }

            override fun onChangingModuleResolve(moduleId: ModuleComponentIdentifier?, expiry: CacheExpirationControl.Expiry?) {
            }
        }
    }
}
