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
package org.gradle.plugin.use.internal

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ComponentModuleMetadataDetails
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.api.internal.plugins.ClassloaderBackedPluginDescriptorLocator
import org.gradle.api.internal.plugins.CorePluginRegistryProvider
import org.gradle.api.internal.plugins.PluginDescriptorLocator
import org.gradle.api.internal.plugins.PluginInspector
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.api.plugins.InvalidPluginException
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.plugin.management.internal.PluginRequests
import org.gradle.plugin.management.internal.PluginResolutionStrategyInternal
import org.gradle.plugin.use.PluginId
import org.gradle.plugin.use.resolve.internal.AlreadyOnClasspathPluginResolver
import org.gradle.plugin.use.resolve.internal.PluginArtifactRepositories
import org.gradle.plugin.use.resolve.internal.PluginArtifactRepositoriesProvider
import org.gradle.plugin.use.resolve.internal.PluginResolution
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult
import org.gradle.plugin.use.resolve.internal.PluginResolutionVisitor
import org.gradle.plugin.use.resolve.internal.PluginResolver
import org.gradle.plugin.use.tracker.internal.PluginVersionTracker
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.function.Consumer

class DefaultPluginRequestApplicator(
    corePluginRegistryProvider: CorePluginRegistryProvider,
    private val pluginResolverFactory: PluginResolverFactory,
    private val pluginRepositoriesProvider: PluginArtifactRepositoriesProvider,
    private val pluginResolutionStrategy: PluginResolutionStrategyInternal,
    private val pluginInspector: PluginInspector,
    private val pluginVersionTracker: PluginVersionTracker,
    listenerManager: ListenerManager
) : PluginRequestApplicator {
    private val corePluginRegistry: PluginRegistry
    private val pluginApplicationListenerBroadcaster: PluginRequestApplicator.PluginApplicationListener

    init {
        this.corePluginRegistry = corePluginRegistryProvider.getCorePluginRegistry()
        this.pluginApplicationListenerBroadcaster = listenerManager.getBroadcaster<PluginRequestApplicator.PluginApplicationListener?>(PluginRequestApplicator.PluginApplicationListener::class.java)!!
    }

    override fun applyPlugins(requests: PluginRequests, scriptHandler: ScriptHandlerInternal, target: PluginManagerInternal?, classLoaderScope: ClassLoaderScope) {
        if (target == null || requests.isEmpty()) {
            classLoaderScope.export(scriptHandler.getInstrumentedScriptClassPath())
            classLoaderScope.lock()
            return
        }

        val resolveContext = pluginRepositoriesProvider.createPluginResolveRepositories()
        resolveContext.applyRepositoriesTo(scriptHandler.getRepositories())

        val pluginApplyActions: MutableList<ApplyAction> = ArrayList<ApplyAction>()
        val pluginDependencies = CollectingPluginRequestResolutionVisitor()

        // Resolve the plugin requests
        val pluginResolver = wrapInAlreadyInClasspathResolver(classLoaderScope, resolveContext)
        for (originalRequest in requests) {
            val request = pluginResolutionStrategy.applyTo(originalRequest)
            val resolved: PluginResolution = resolvePluginRequest(pluginResolver, request)

            resolved.accept(pluginDependencies)

            if (request.isApply()) {
                pluginApplyActions.add(DefaultPluginRequestApplicator.ApplyAction(request, resolved))
            }

            val pluginVersion = resolved.getPluginVersion()
            if (pluginVersion != null) {
                pluginVersionTracker.setPluginVersionAt(
                    classLoaderScope,
                    resolved.getPluginId().getId(),
                    pluginVersion
                )
            }
        }

        // Configure the resolution
        pluginDependencies.getAdditionalDependencies().forEach(Consumer { dependency: Dependency? -> scriptHandler.addScriptClassPathDependency(dependency!!) })
        if (!pluginDependencies.getReplacements().isEmpty()) {
            val modules = scriptHandler.getDependencies().getModules()
            for (replacement in pluginDependencies.getReplacements()) {
                modules.module(replacement.original, Action { details: ComponentModuleMetadataDetails? -> details!!.replacedBy(replacement.replacement) })
            }
        }

        // Perform resolution & configure the classloader
        classLoaderScope.export(scriptHandler.getInstrumentedScriptClassPath())
        pluginDependencies.getAdditionalClassloaders().forEach(Consumer { classLoader: ClassLoader? -> classLoaderScope.export(classLoader!!) })
        classLoaderScope.lock()

        // Apply the plugins
        pluginApplyActions.forEach(Consumer { action: ApplyAction? -> action!!.apply(target) })
    }

    private fun wrapInAlreadyInClasspathResolver(classLoaderScope: ClassLoaderScope, resolveContext: PluginArtifactRepositories): PluginResolver {
        val parentLoaderScope = classLoaderScope.getParent()
        val scriptClasspathPluginDescriptorLocator: PluginDescriptorLocator = ClassloaderBackedPluginDescriptorLocator(parentLoaderScope.getExportClassLoader())
        val pluginResolver = pluginResolverFactory.create(resolveContext)
        return AlreadyOnClasspathPluginResolver(pluginResolver, corePluginRegistry, parentLoaderScope, scriptClasspathPluginDescriptorLocator, pluginInspector, pluginVersionTracker)
    }

    /**
     * The action that applies a plugin.
     */
    private inner class ApplyAction(private val request: PluginRequestInternal, private val resolved: PluginResolution) {
        fun apply(target: PluginManagerInternal) {
            try {
                try {
                    pluginApplicationListenerBroadcaster.pluginApplied(request)
                    resolved.applyTo(target)
                } catch (e: UnknownPluginException) {
                    throw couldNotApply(request, request.getId(), e)
                } catch (e: Exception) {
                    throw exceptionOccurred(request, e)
                }
            } catch (e: Exception) {
                throw LocationAwareException(e, request.getScriptDisplayName(), request.getLineNumber())
            }
        }
    }

    private class CollectingPluginRequestResolutionVisitor : PluginResolutionVisitor {
        private var additionalDependencies: MutableList<Dependency>? = null
        private var replacements: MutableList<ModuleReplacement>? = null
        private var additionalClassloaders: MutableList<ClassLoader>? = null

        internal class ModuleReplacement(private val original: ModuleIdentifier, private val replacement: ModuleIdentifier)

        override fun visitDependency(dependency: Dependency) {
            if (additionalDependencies == null) {
                additionalDependencies = ArrayList<Dependency>()
            }
            additionalDependencies!!.add(dependency)
        }

        override fun visitReplacement(original: ModuleIdentifier, replacement: ModuleIdentifier) {
            if (replacements == null) {
                replacements = ArrayList<ModuleReplacement>()
            }
            replacements!!.add(ModuleReplacement(original, replacement))
        }

        override fun visitClassLoader(classLoader: ClassLoader) {
            if (additionalClassloaders == null) {
                additionalClassloaders = ArrayList<ClassLoader>()
            }
            additionalClassloaders!!.add(classLoader)
        }

        fun getAdditionalDependencies(): MutableList<Dependency> {
            if (additionalDependencies == null) {
                return mutableListOf<Dependency>()
            }
            return additionalDependencies!!
        }

        fun getReplacements(): MutableList<ModuleReplacement> {
            if (replacements == null) {
                return mutableListOf<ModuleReplacement>()
            }
            return replacements!!
        }

        fun getAdditionalClassloaders(): MutableList<ClassLoader> {
            if (additionalClassloaders == null) {
                return mutableListOf<ClassLoader>()
            }
            return additionalClassloaders!!
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(DefaultPluginRequestApplicator::class.java)

        private fun couldNotApply(request: PluginRequestInternal, id: PluginId, cause: UnknownPluginException): InvalidPluginException {
            return InvalidPluginException(
                String.format(
                    ("Could not apply requested plugin %s as it does not provide a plugin with id '%s'."
                            + " This is caused by an incorrect plugin implementation."
                            + " Please contact the plugin author(s)."),
                    request.getDisplayName(), id
                ),
                cause
            )
        }

        private fun exceptionOccurred(request: PluginRequestInternal, e: Exception): InvalidPluginException {
            return InvalidPluginException(String.format("An exception occurred applying plugin request %s", request.getDisplayName()), e)
        }

        private fun resolvePluginRequest(resolver: PluginResolver, request: PluginRequestInternal): PluginResolution {
            val result: PluginResolutionResult
            try {
                result = resolver.resolve(request)
                LOGGER.info("Resolved plugin {}", request.getDisplayName())
            } catch (e: Exception) {
                throw LocationAwareException(
                    GradleException(String.format("Error resolving plugin %s", request.getDisplayName()), e),
                    request.getScriptDisplayName(), request.getLineNumber()
                )
            }

            return result.getFound(request)
        }
    }
}
