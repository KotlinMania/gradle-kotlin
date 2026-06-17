/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.plugin.management.internal

import org.gradle.api.Action
import org.gradle.api.invocation.Gradle
import org.gradle.internal.InternalBuildAdapter
import org.gradle.internal.MutableActionSet
import org.gradle.internal.event.ListenerManager
import org.gradle.plugin.management.PluginResolveDetails
import org.gradle.plugin.use.PluginId

class DefaultPluginResolutionStrategy(listenerManager: ListenerManager) : PluginResolutionStrategyInternal {
    private val resolutionRules = MutableActionSet<PluginResolveDetails?>()
    private val pluginVersions: MutableMap<PluginId?, String?> = HashMap<PluginId?, String?>()
    private var locked = false

    init {
        listenerManager.addListener(object : InternalBuildAdapter() {
            override fun projectsLoaded(gradle: Gradle) {
                locked = true
            }
        })
    }

    override fun eachPlugin(rule: Action<in PluginResolveDetails?>) {
        check(!locked) { "Cannot change the plugin resolution strategy after projects have been loaded." }
        resolutionRules.add(rule)
    }

    override fun applyTo(pluginRequest: PluginRequestInternal): PluginRequestInternal {
        val details = DefaultPluginResolveDetails(pluginRequest)
        if (details.getRequested().getVersion() == null) {
            val version = pluginVersions.get(details.getRequested().getId())
            if (version != null) {
                details.useVersion(version)
            }
        }
        resolutionRules.execute(details)
        return details.getTarget()
    }

    override fun setDefaultPluginVersion(id: PluginId, version: String) {
        val existing = pluginVersions.get(id)
        require(!(existing != null && existing != version)) { "Cannot provide multiple default versions for the same plugin." }
        pluginVersions.put(id, version)
    }
}
