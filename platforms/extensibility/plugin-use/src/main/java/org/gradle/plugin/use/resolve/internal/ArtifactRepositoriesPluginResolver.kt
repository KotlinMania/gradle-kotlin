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
package org.gradle.plugin.use.resolve.internal

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Joiner
import com.google.common.base.Strings
import org.gradle.api.Action
import org.gradle.api.artifacts.ArtifactView
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.plugin.management.internal.PluginCoordinates
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.plugin.use.PluginId
import java.util.function.Consumer

class ArtifactRepositoriesPluginResolver(private val resolutionServices: DependencyResolutionServices) : PluginResolver {
    override fun resolve(pluginRequest: PluginRequestInternal): PluginResolutionResult {
        val markerDependency = getMarkerDependency(pluginRequest)
        val markerVersion = markerDependency.getVersion()
        if (Strings.isNullOrEmpty(markerVersion)) {
            return PluginResolutionResult.Companion.notFound(SOURCE_NAME, "plugin dependency must include a version number for this source")
        }

        val autoApplied = pluginRequest.getOrigin() == PluginRequestInternal.Origin.AUTO_APPLIED
        if (exists(markerDependency) || autoApplied) {
            // Even if we don't find the auto-applied plugin version, continue trying to resolve it with a preferred version,
            // in case the user provides an explicit or transitive required version.
            // The resolution will fail if there is no user-provided required version, however it avoids us failing here
            // if the weak version is not present but never selected.
            return PluginResolutionResult.Companion.found(ExternalPluginResolution(this.dependencyFactory, pluginRequest, autoApplied))
        } else {
            return handleNotFound("could not resolve plugin artifact '" + getNotation(markerDependency) + "'")
        }
    }

    internal class ExternalPluginResolution
    /**
     * @param dependencyFactory Creates dependency instances
     * @param pluginRequest The original plugin request.
     * @param useWeakVersion Whether a preferred version should be used for the plugin dependency.
     */(private val dependencyFactory: DependencyFactory, private val pluginRequest: PluginRequestInternal, private val useWeakVersion: Boolean) : PluginResolution {
        override fun getPluginId(): PluginId {
            return pluginRequest.getId()
        }

        override fun getPluginVersion(): String? {
            val selector = pluginRequest.getSelector()
            if (selector is ModuleComponentSelector) {
                return selector.getVersion()
            } else {
                return pluginRequest.getVersion()
            }
        }

        override fun accept(visitor: PluginResolutionVisitor) {
            val id = pluginRequest.getId().getId()

            val selector = pluginRequest.getSelector()
            val module = if (selector is ModuleComponentSelector)
                selector.getModuleIdentifier()
            else
                DefaultModuleIdentifier.newId(id, id + PLUGIN_MARKER_SUFFIX)

            visitDependency(visitor, module)
            pluginRequest.getAlternativeCoordinates().ifPresent(Consumer { altCoords: PluginCoordinates? -> Companion.visitModuleReplacements(visitor, altCoords!!, id, module) }
            )
        }

        private fun visitDependency(visitor: PluginResolutionVisitor, module: ModuleIdentifier) {
            val dependency = dependencyFactory.create(module.getGroup(), module.getName(), null)
            dependency.version(Action { version: MutableVersionConstraint? ->
                if (useWeakVersion) {
                    version!!.prefer(getPluginVersion()!!)
                } else {
                    version!!.require(getPluginVersion()!!)
                }
            })
            visitor.visitDependency(dependency)
        }

        override fun applyTo(pluginManager: PluginManagerInternal) {
            val altCoords = pluginRequest.getAlternativeCoordinates().orElse(null)
            if (altCoords != null && pluginManager.hasPlugin(altCoords.getId().getId())) {
                return
            }

            pluginManager.apply(pluginRequest.getId().getId())
        }

        companion object {
            private fun visitModuleReplacements(visitor: PluginResolutionVisitor, altCoords: PluginCoordinates, id: String?, module: ModuleIdentifier?) {
                val altId = altCoords.getId().getId()
                visitor.visitReplacement(
                    DefaultModuleIdentifier.newId(id, id + PLUGIN_MARKER_SUFFIX),
                    DefaultModuleIdentifier.newId(altId, altId + PLUGIN_MARKER_SUFFIX)
                )

                val selector = altCoords.getSelector()
                if (selector is ModuleComponentSelector) {
                    val moduleSelector = selector
                    visitor.visitReplacement(module, moduleSelector.getModuleIdentifier())
                }
            }
        }
    }

    private fun handleNotFound(message: String?): PluginResolutionResult {
        val detail = StringBuilder("Searched in the following repositories:\n")
        val it: MutableIterator<ArtifactRepository?> = resolutionServices.getResolveRepositoryHandler().iterator()
        while (it.hasNext()) {
            detail.append("  ").append((it.next() as ArtifactRepositoryInternal).getDisplayName())
            if (it.hasNext()) {
                detail.append("\n")
            }
        }
        return PluginResolutionResult.Companion.notFound(SOURCE_NAME, message, detail.toString())
    }

    /**
     * Checks whether the plugin marker artifact exists in the backing artifacts repositories.
     *
     * TODO: Performing resolution here is likely quite inefficient. This performs resolution
     * for each plugin request that is not already found on the classpath. Doing this allows
     * us to produce a better error message at the cost of performance. We should limit the
     * number of resolutions we perform in the buildscript context in order to avoid IO and
     * serial bottlenecks before the build can start configuration and thus perform actual work.
     */
    private fun exists(dependency: ModuleDependency): Boolean {
        val configurations = resolutionServices.getConfigurationContainer()
        val configuration = configurations.detachedConfiguration(dependency)
        configuration.setTransitive(false)
        val lenientView = configuration.getIncoming().artifactView(Action { view: ArtifactView.ViewConfiguration? ->
            view!!.setLenient(true)
        })
        return lenientView.getArtifacts().getFailures().isEmpty()
    }

    private fun getMarkerDependency(pluginRequest: PluginRequestInternal): ModuleDependency {
        val selector = pluginRequest.getSelector()
        if (selector !is ModuleComponentSelector) {
            val id = pluginRequest.getId().getId()
            return this.dependencyFactory.create(id, id + PLUGIN_MARKER_SUFFIX, pluginRequest.getVersion())
        } else {
            val moduleSelector = selector
            return this.dependencyFactory.create(moduleSelector.getGroup(), moduleSelector.getModule(), moduleSelector.getVersion())
        }
    }

    private fun getNotation(dependency: Dependency): String {
        return Joiner.on(':').join(dependency.getGroup(), dependency.getName(), dependency.getVersion())
    }

    private val dependencyFactory: DependencyFactory?
        get() = resolutionServices.getDependencyFactory()

    companion object {
        const val PLUGIN_MARKER_SUFFIX: String = ".gradle.plugin"

        @VisibleForTesting
        const val SOURCE_NAME: String = "Plugin Repositories"
    }
}
