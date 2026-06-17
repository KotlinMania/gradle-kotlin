/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.plugin.use.resolve.service.internal

import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptClassPathResolver
import org.gradle.api.internal.plugins.DefaultPluginRegistry
import org.gradle.api.internal.plugins.PluginImplementation
import org.gradle.api.internal.plugins.PluginInspector
import org.gradle.api.internal.plugins.PluginManagerInternal
import org.gradle.api.internal.plugins.PluginRegistry
import org.gradle.internal.Factory
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.lazy.Lazy
import org.gradle.internal.lazy.Lazy.Companion.locking
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.plugin.use.PluginId
import org.gradle.plugin.use.resolve.internal.PluginResolution
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult
import org.gradle.plugin.use.resolve.internal.PluginResolutionVisitor
import org.gradle.plugin.use.resolve.internal.PluginResolver
import java.io.File
import java.util.function.Supplier
import java.util.stream.Collectors

class DefaultInjectedClasspathPluginResolver(
    private val parentScope: ClassLoaderScope,
    private val scriptClassPathResolver: ScriptClassPathResolver,
    private val fileCollectionFactory: FileCollectionFactory,
    private val pluginInspector: PluginInspector,
    private val injectedClasspath: ClassPath,
    instrumentationStrategy: InjectedClasspathInstrumentationStrategy,
    dependencyResolutionServicesFactory: Factory<DependencyResolutionServices?>
) : ClientInjectedClasspathPluginResolver, PluginResolver {
    private val pluginRegistry: Lazy<PluginRegistry?>

    init {
        this.pluginRegistry = createPluginRegistry(instrumentationStrategy, dependencyResolutionServicesFactory)
    }

    private fun createPluginRegistry(
        instrumentationStrategy: InjectedClasspathInstrumentationStrategy,
        dependencyResolutionServicesFactory: Factory<DependencyResolutionServices?>
    ): Lazy<PluginRegistry?> {
        // One wanted side effect of calling InstrumentationStrategy.getTransform() is also to report
        // a configuration cache problem if third-party agent is used with TestKit with configuration cache,
        // see ConfigurationCacheInjectedClasspathInstrumentationStrategy implementation.

        val transform = instrumentationStrategy.getTransform()
        when (transform) {
            InjectedClasspathInstrumentationStrategy.TransformMode.NONE -> return locking().of<PluginRegistry?>(Supplier { this.createUninstrumentedPluginRegistry() })
            InjectedClasspathInstrumentationStrategy.TransformMode.BUILD_LOGIC -> return locking().of<PluginRegistry?>(Supplier { createInstrumentedPluginRegistry(dependencyResolutionServicesFactory) })
            else -> throw IllegalArgumentException("Unknown instrumentation strategy: " + transform)
        }
    }

    private fun createUninstrumentedPluginRegistry(): PluginRegistry {
        return newPluginRegistryOf(injectedClasspath)
    }

    private fun createInstrumentedPluginRegistry(dependencyResolutionServicesFactory: Factory<DependencyResolutionServices?>): PluginRegistry {
        val dependencyResolutionServices = dependencyResolutionServicesFactory.create()
        val dependencies = dependencyResolutionServices!!.getDependencyHandler()
        val configurations = dependencyResolutionServices.getConfigurationContainer()
        val injectedClasspathDependency = dependencies.create(fileCollectionFactory.fixed(injectedClasspath.getAsFiles()))
        val configuration = configurations.detachedConfiguration(injectedClasspathDependency)
        val resolutionContext = scriptClassPathResolver.prepareDependencyHandler(dependencies)
        scriptClassPathResolver.prepareClassPath(configuration, resolutionContext)
        val instrumentedClassPath = scriptClassPathResolver.resolveClassPath(configuration, resolutionContext)
        return newPluginRegistryOf(instrumentedClassPath)
    }

    private fun newPluginRegistryOf(classPath: ClassPath): PluginRegistry {
        return DefaultPluginRegistry(
            pluginInspector,
            parentScope.createChild("injected-plugin", null)
                .local(classPath)
                .lock()
        )
    }

    override fun collectResolversInto(dest: MutableCollection<in PluginResolver>) {
        dest.add(this)
    }

    override fun resolve(pluginRequest: PluginRequestInternal): PluginResolutionResult {
        val plugin = pluginRegistry.get()!!.lookup(pluginRequest.getId())
        if (plugin == null) {
            val classpathStr = injectedClasspath.getAsFiles().stream().map<String> { obj: File? -> obj!!.getAbsolutePath() }.collect(Collectors.joining(File.pathSeparator))
            return PluginResolutionResult.Companion.notFound(this.description, "classpath: " + classpathStr)
        } else {
            return PluginResolutionResult.Companion.found(InjectedClasspathPluginResolution(plugin))
        }
    }

    val description: String
        get() =// It's true right now that this is always coming from the TestKit, but might not be in the future.
            "Gradle TestKit"

    private class InjectedClasspathPluginResolution(private val plugin: PluginImplementation<*>) : PluginResolution {
        override fun getPluginId(): PluginId {
            return plugin.getPluginId()!!
        }

        override fun accept(visitor: PluginResolutionVisitor) {
            visitor.visitClassLoader(plugin.asClass().getClassLoader())
        }

        override fun applyTo(pluginManager: PluginManagerInternal) {
            pluginManager.apply(plugin)
        }
    }
}
