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
package org.gradle.plugin.use.tracker.internal

import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks plugin versions available at different [scopes][ClassLoaderScope].
 */
@ServiceScope(Scope.Build::class)
class PluginVersionTracker {
    val pluginVersionsPerScope: MutableMap<ClassLoaderScope?, MutableMap<String?, String?>> = ConcurrentHashMap<ClassLoaderScope?, MutableMap<String?, String?>>()

    fun setPluginVersionAt(scope: ClassLoaderScope?, pluginId: String?, pluginVersion: String?) {
        val pluginVersions = pluginVersionsPerScope.computeIfAbsent(scope) { ignored: ClassLoaderScope? -> ConcurrentHashMap<String?, String?>() }
        check(!pluginVersions.containsKey(pluginId)) { "Plugin version already set for " + pluginId }
        pluginVersions.put(pluginId, pluginVersion)
    }

    fun findPluginVersionAt(scope: ClassLoaderScope?, pluginId: String?): String? {
        var scope = scope
        while (scope != null) {
            val pluginVersion = pluginVersionsPerScope.getOrDefault(scope, mutableMapOf<String?, String?>()).get(pluginId)
            if (pluginVersion != null) {
                return pluginVersion
            }
            val parent = scope.getParent()
            if (scope === parent) {
                // See RootClassLoaderScope#getParent()
                break
            }
            scope = parent
        }
        return null
    }
}
