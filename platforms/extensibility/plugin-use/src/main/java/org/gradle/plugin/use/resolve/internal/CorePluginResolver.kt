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
package org.gradle.plugin.use.resolve.internal

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.plugins.DefaultPluginManager
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.plugin.management.internal.InvalidPluginRequestException
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.plugin.use.PluginId

class CorePluginResolver(private val documentationRegistry: DocumentationRegistry, private val pluginRegistry: PluginRegistry) : PluginResolver {
    override fun resolve(pluginRequest: PluginRequestInternal): PluginResolutionResult {
        val id = pluginRequest.getId()
        if (!isCorePluginRequest(id)) {
            return PluginResolutionResult.Companion.notFound(description, String.format("plugin is not in '%s' namespace", DefaultPluginManager.CORE_PLUGIN_NAMESPACE))
        }

        val plugin = pluginRegistry.lookup(id)
        if (plugin == null) {
            return PluginResolutionResult.Companion.notFound(description, "not a core plugin. " + documentationRegistry.getDocumentationRecommendationFor("available plugins", "plugin_reference"))
        }

        validate(pluginRequest)
        return PluginResolutionResult.Companion.found(SimplePluginResolution(plugin))
    }

    companion object {
        private fun validate(pluginRequest: PluginRequestInternal) {
            if (pluginRequest.getVersion() != null) {
                throw InvalidPluginRequestException(
                    pluginRequest,
                    (getCorePluginClarification(pluginRequest) + "which cannot be specified with a version number. "
                            + "Such plugins are versioned as part of Gradle. Please remove the version number from the declaration.")
                )
            }
            if (pluginRequest.getSelector() != null) {
                throw InvalidPluginRequestException(
                    pluginRequest,
                    (getCorePluginClarification(pluginRequest) + "which cannot be specified with a custom implementation artifact. "
                            + "Such plugins are versioned as part of Gradle. Please remove the custom artifact from the request.")
                )
            }
            if (!pluginRequest.isApply()) {
                throw InvalidPluginRequestException(
                    pluginRequest,
                    (getCorePluginClarification(pluginRequest) + "which is already on the classpath. "
                            + "Requesting it with the 'apply false' option is a no-op.")
                )
            }
        }

        private fun getCorePluginClarification(pluginRequest: PluginRequestInternal): String {
            return "Plugin '" + pluginRequest.getId() + "' is a core Gradle plugin, "
        }

        private fun isCorePluginRequest(id: PluginId): Boolean {
            val namespace = id.getNamespace()
            return namespace == null || namespace == DefaultPluginManager.CORE_PLUGIN_NAMESPACE
        }

        val description: String
            get() = "Gradle Core Plugins"
    }
}
