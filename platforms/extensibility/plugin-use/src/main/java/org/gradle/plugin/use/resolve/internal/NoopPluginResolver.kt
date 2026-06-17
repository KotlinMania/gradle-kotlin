/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.plugin.use.resolve.internal

import org.gradle.api.Plugin
import org.gradle.api.internal.plugins.DefaultPotentialPluginWithId
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.plugin.use.PluginId
import org.gradle.plugin.use.internal.DefaultPluginId

// Used for testing the plugins DSL
class NoopPluginResolver(private val pluginRegistry: PluginRegistry) : PluginResolver {
    override fun resolve(pluginRequest: PluginRequestInternal): PluginResolutionResult {
        if (pluginRequest.getId() == NOOP_PLUGIN_ID) {
            return PluginResolutionResult.Companion.found(
                SimplePluginResolution(
                    DefaultPotentialPluginWithId.of<NoopPlugin?>(
                        NOOP_PLUGIN_ID,
                        pluginRegistry.inspect<NoopPlugin?>(NoopPlugin::class.java)
                    )
                )
            )
        }
        return PluginResolutionResult.Companion.notFound()
    }

    abstract class NoopPlugin : Plugin<Any?> {
        override fun apply(target: Any?) {
            // do nothing
        }
    }

    companion object {
        val NOOP_PLUGIN_ID: PluginId = DefaultPluginId.of("noop")
    }
}
