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
package org.gradle.plugin.management.internal

import com.google.common.collect.ImmutableList
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.plugins.PluginContainer
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginRegistry
import org.gradle.plugin.use.resolve.internal.ArtifactRepositoriesPluginResolver
import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Collectors
import java.util.stream.StreamSupport

class DefaultPluginHandler(private val registry: AutoAppliedPluginRegistry) : PluginHandler {
    override fun getAutoAppliedPlugins(initialRequests: PluginRequests, pluginTarget: Any): PluginRequests {
        if (pluginTarget is Project) {
            val project = pluginTarget

            val autoAppliedPlugins = registry.getAutoAppliedPlugins(project)
            if (autoAppliedPlugins.isEmpty()) {
                return PluginRequests.EMPTY
            }
            return filterAlreadyAppliedOrRequested(autoAppliedPlugins, initialRequests, project.getPlugins(), project.getBuildscript())
        } else if (pluginTarget is Settings) {
            val settings = pluginTarget

            val autoAppliedPlugins = registry.getAutoAppliedPlugins(settings)
            if (autoAppliedPlugins.isEmpty()) {
                return PluginRequests.EMPTY
            }
            return filterAlreadyAppliedOrRequested(autoAppliedPlugins, initialRequests, settings.getPlugins(), settings.getBuildscript())
        } else {
            // No auto-applied plugins available
            return PluginRequests.EMPTY
        }
    }

    companion object {
        private fun filterAlreadyAppliedOrRequested(
            autoAppliedPlugins: PluginRequests,
            initialRequests: PluginRequests,
            pluginContainer: PluginContainer,
            scriptHandler: ScriptHandler
        ): PluginRequests {
            return PluginRequests.of(
                ImmutableList.copyOf<PluginRequestInternal?>(
                    StreamSupport.stream<PluginRequestInternal?>(autoAppliedPlugins.spliterator(), false)
                        .filter { autoAppliedPlugin: PluginRequestInternal? ->
                            !isAlreadyAppliedOrRequested(
                                PluginCoordinates.from(autoAppliedPlugin!!),
                                initialRequests,
                                pluginContainer,
                                scriptHandler
                            )
                        }
                        .filter { autoAppliedPlugin: PluginRequestInternal? ->
                            autoAppliedPlugin!!.getAlternativeCoordinates()
                                .map<Boolean?>(Function { it: PluginCoordinates? -> !Companion.isAlreadyAppliedOrRequested(it!!, initialRequests, pluginContainer, scriptHandler) })
                                .orElse(true)
                        }
                        .collect(Collectors.toList())
                ))
        }

        private fun isAlreadyAppliedOrRequested(autoAppliedPlugin: PluginCoordinates, requests: PluginRequests, pluginContainer: PluginContainer, scriptHandler: ScriptHandler): Boolean {
            return isAlreadyApplied(autoAppliedPlugin, pluginContainer) || isAlreadyRequestedInPluginsBlock(autoAppliedPlugin, requests) || isAlreadyRequestedInBuildScriptBlock(
                autoAppliedPlugin,
                scriptHandler
            )
        }

        private fun isAlreadyApplied(autoAppliedPlugin: PluginCoordinates, pluginContainer: PluginContainer): Boolean {
            return pluginContainer.hasPlugin(autoAppliedPlugin.getId().getId())
        }

        private fun isAlreadyRequestedInPluginsBlock(autoAppliedPlugin: PluginCoordinates, requests: PluginRequests): Boolean {
            for (request in requests) {
                if (autoAppliedPlugin.getId() == request.getId()) {
                    return true
                }
            }
            return false
        }

        private fun isAlreadyRequestedInBuildScriptBlock(autoAppliedPlugin: PluginCoordinates, scriptHandler: ScriptHandler): Boolean {
            val pluginId = autoAppliedPlugin.getId().getId()
            val pluginMarker = DefaultModuleIdentifier.newId(pluginId, pluginId + ArtifactRepositoriesPluginResolver.Companion.PLUGIN_MARKER_SUFFIX)
            var predicate = Predicate { dependency: Dependency? -> Companion.hasMatchingCoordinates(dependency!!, pluginMarker) }

            val selector = autoAppliedPlugin.getSelector()
            if (selector is ModuleComponentSelector) {
                val moduleSelector = selector
                predicate = predicate.or(Predicate { dependency: Dependency? -> Companion.hasMatchingCoordinates(dependency!!, moduleSelector.getModuleIdentifier()) })
            }

            val classpathConfiguration = scriptHandler.getConfigurations().getByName(ScriptHandler.CLASSPATH_CONFIGURATION)
            return classpathConfiguration.getDependencies().stream().anyMatch(predicate)
        }

        private fun hasMatchingCoordinates(dependency: Dependency, module: ModuleIdentifier): Boolean {
            return module.getGroup() == dependency.getGroup() && module.getName() == dependency.getName()
        }
    }
}
