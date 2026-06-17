/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugin.internal

import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.BuildDefinition
import org.gradle.api.internal.artifacts.DependencyManagementServices
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.initialization.ScriptClassPathResolver
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext
import org.gradle.api.internal.plugins.PluginInspector
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.properties.InspectionSchemeFactory
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.features.internal.binding.DefaultModelDefaultsApplicator
import org.gradle.features.internal.binding.DefaultProjectFeatureApplicator
import org.gradle.features.internal.binding.DefaultProjectFeatureDeclarations
import org.gradle.features.internal.binding.ModelDefaultsApplicator
import org.gradle.features.internal.binding.ModelDefaultsHandler
import org.gradle.features.internal.binding.ProjectFeatureApplicator
import org.gradle.features.internal.binding.ProjectFeatureDeclarations
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.internal.Factory
import org.gradle.internal.build.BuildIncluder
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.instantiation.InstantiatorFactory
import org.gradle.internal.properties.annotations.MissingPropertyAnnotationHandler
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.AbstractGradleModuleServices
import org.gradle.plugin.management.PluginManagementSpec
import org.gradle.plugin.management.internal.DefaultPluginHandler
import org.gradle.plugin.management.internal.DefaultPluginManagementSpec
import org.gradle.plugin.management.internal.DefaultPluginResolutionStrategy
import org.gradle.plugin.management.internal.PluginHandler
import org.gradle.plugin.management.internal.PluginResolutionStrategyInternal
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginRegistry
import org.gradle.plugin.management.internal.autoapply.CompositeAutoAppliedPluginRegistry
import org.gradle.plugin.management.internal.autoapply.InjectedAutoAppliedPluginRegistry
import org.gradle.plugin.use.internal.DefaultPluginRequestApplicator
import org.gradle.plugin.use.internal.InjectedPluginClasspath
import org.gradle.plugin.use.internal.PluginDependencyResolutionServices
import org.gradle.plugin.use.internal.PluginRepositoryHandlerProvider
import org.gradle.plugin.use.internal.PluginRequestApplicator
import org.gradle.plugin.use.internal.PluginResolverFactory
import org.gradle.plugin.use.resolve.service.internal.ClientInjectedClasspathPluginResolver
import org.gradle.plugin.use.resolve.service.internal.DefaultInjectedClasspathPluginResolver
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathInstrumentationStrategy
import org.gradle.plugin.use.tracker.internal.PluginVersionTracker
import org.jspecify.annotations.NullMarked

class PluginUseServices : AbstractGradleModuleServices() {
    public override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(BuildScopeServices())
    }

    public override fun registerSettingsServices(registration: ServiceRegistration) {
        registration.addProvider(SettingsScopeServices())
    }

    public override fun registerProjectServices(registration: ServiceRegistration) {
        registration.addProvider(ProjectScopeServices())
    }

    private class SettingsScopeServices : ServiceRegistrationProvider {
        @Provides
        protected fun createPluginManagementSpec(
            instantiator: Instantiator,
            pluginRepositoryHandlerProvider: PluginRepositoryHandlerProvider,
            internalPluginResolutionStrategy: PluginResolutionStrategyInternal?,
            fileResolver: FileResolver?,
            buildIncluder: BuildIncluder?
        ): PluginManagementSpec {
            return instantiator.newInstance<DefaultPluginManagementSpec>(
                DefaultPluginManagementSpec::class.java,
                pluginRepositoryHandlerProvider,
                internalPluginResolutionStrategy,
                fileResolver,
                buildIncluder
            )
        }
    }

    private class BuildScopeServices : ServiceRegistrationProvider {
        @Provides
        fun configure(registration: ServiceRegistration) {
            registration.add(PluginResolverFactory::class.java)
            registration.add<DefaultPluginRequestApplicator?>(PluginRequestApplicator::class.java, DefaultPluginRequestApplicator::class.java)
            registration.add(PluginVersionTracker::class.java)
        }

        @Provides
        fun configure(registration: ServiceRegistration, pluginScheme: PluginScheme, instantiatorFactory: InstantiatorFactory, problemsService: ProblemsInternal) {
            val projectFeatureRegistry = DefaultProjectFeatureDeclarations(pluginScheme.getInspectionScheme(), instantiatorFactory.injectScheme().instantiator(), problemsService.internalReporter)
            registration.add<ProjectFeatureDeclarations?>(ProjectFeatureDeclarations::class.java, projectFeatureRegistry)
        }

        @Provides
        fun createInjectedAutoAppliedPluginRegistry(buildDefinition: BuildDefinition?): AutoAppliedPluginRegistry {
            return InjectedAutoAppliedPluginRegistry(buildDefinition!!)
        }

        @Provides
        fun createPluginHandler(registries: MutableList<AutoAppliedPluginRegistry?>?): PluginHandler {
            return DefaultPluginHandler(CompositeAutoAppliedPluginRegistry(registries))
        }

        @Provides
        fun createPluginScheme(instantiatorFactory: InstantiatorFactory, inspectionSchemeFactory: InspectionSchemeFactory): PluginScheme {
            val instantiationScheme = instantiatorFactory.decorateScheme()
            val allPropertyTypes = ImmutableSet.builder<Class<out Annotation?>?>()
            val inspectionScheme = inspectionSchemeFactory.inspectionScheme(
                allPropertyTypes.build(),
                mutableSetOf<Class<out Annotation?>?>(),
                mutableListOf<Class<out Annotation?>?>(),
                instantiationScheme,
                MissingPropertyAnnotationHandler.DO_NOTHING
            )
            return PluginScheme(instantiationScheme, inspectionScheme)
        }

        @Provides
        fun createInjectedClassPathPluginResolver(
            dependencyManagementServices: DependencyManagementServices,
            classLoaderScopeRegistry: ClassLoaderScopeRegistry,
            pluginInspector: PluginInspector,
            injectedPluginClasspath: InjectedPluginClasspath,
            scriptClassPathResolver: ScriptClassPathResolver,
            fileCollectionFactory: FileCollectionFactory,
            instrumentationStrategy: InjectedClasspathInstrumentationStrategy
        ): ClientInjectedClasspathPluginResolver {
            if (injectedPluginClasspath.getClasspath().isEmpty()) {
                return ClientInjectedClasspathPluginResolver.Companion.EMPTY
            }

            val dependencyResolutionServicesFactory: Factory<DependencyResolutionServices?> =
                org.gradle.internal.Factory { dependencyManagementServices.newDetachedResolver(StandaloneDomainObjectContext.PLUGINS) }

            return DefaultInjectedClasspathPluginResolver(
                classLoaderScopeRegistry.getCoreAndPluginsScope(),
                scriptClassPathResolver,
                fileCollectionFactory,
                pluginInspector,
                injectedPluginClasspath.getClasspath(),
                instrumentationStrategy,
                dependencyResolutionServicesFactory
            )
        }

        @Provides
        fun createPluginResolutionStrategy(instantiator: Instantiator, listenerManager: ListenerManager): PluginResolutionStrategyInternal {
            return instantiator.newInstance<DefaultPluginResolutionStrategy>(DefaultPluginResolutionStrategy::class.java, listenerManager)
        }

        @Provides
        fun createPluginDependencyResolutionServices(
            dependencyManagementServices: DependencyManagementServices
        ): PluginDependencyResolutionServices {
            return PluginDependencyResolutionServices(org.gradle.internal.Factory { dependencyManagementServices.newDetachedResolver(StandaloneDomainObjectContext.PLUGINS) }
            )
        }
    }

    @NullMarked
    private class ProjectScopeServices : ServiceRegistrationProvider {
        @Provides
        fun createProjectFeatureApplicator(
            instantiatorFactory: InstantiatorFactory,
            project: ProjectInternal,
            problems: ProblemsInternal,
            services: ServiceRegistry
        ): ProjectFeatureApplicator {
            return instantiatorFactory.inject(services).newInstance<DefaultProjectFeatureApplicator>(
                DefaultProjectFeatureApplicator::class.java,
                project.getClassLoaderScope(),
                project.getObjects(),
                problems.internalReporter,
                services
            )
        }

        @Provides
        fun createModelDefaultsApplicator(modelDefaultsHandlers: MutableList<ModelDefaultsHandler>): ModelDefaultsApplicator {
            return DefaultModelDefaultsApplicator(modelDefaultsHandlers)
        }
    }
}
