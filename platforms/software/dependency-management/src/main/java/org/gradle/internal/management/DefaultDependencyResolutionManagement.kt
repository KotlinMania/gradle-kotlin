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
package org.gradle.internal.management

import com.google.common.collect.ImmutableList
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.ActionConfiguration
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.NamedDomainObjectList
import org.gradle.api.artifacts.ComponentMetadataDetails
import org.gradle.api.artifacts.ComponentMetadataRule
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.initialization.resolve.DependencyResolutionManagement
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.api.initialization.resolve.RulesMode
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.artifacts.DependencyManagementServices
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.dsl.ComponentMetadataHandlerInternal
import org.gradle.api.internal.initialization.StandaloneDomainObjectContext
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging.getLogger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.internal.Describables
import org.gradle.internal.DisplayName
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.lazy.Lazy
import org.gradle.internal.lazy.Lazy.Companion.locking
import org.jspecify.annotations.NullMarked
import java.util.function.Consumer
import java.util.function.Supplier
import javax.inject.Inject

@NullMarked
class DefaultDependencyResolutionManagement @Inject constructor(
    private val context: UserCodeApplicationContext,
    dependencyManagementServices: DependencyManagementServices,
    objects: ObjectFactory,
    collectionCallbackActionDecorator: CollectionCallbackActionDecorator
) : DependencyResolutionManagementInternal {
    private val componentMetadataRulesActions: MutableList<Action<in ComponentMetadataHandler>> = ArrayList<Action<in ComponentMetadataHandler>>()

    private val dependencyResolutionServices: Lazy<DependencyResolutionServices?>
    private val registar: ComponentMetadataHandler = DefaultDependencyResolutionManagement.ComponentMetadataRulesRegistar()
    private val repositoryMode: Property<RepositoriesMode>
    private val rulesMode: Property<RulesMode>
    private val librariesExtensionName: Property<String>
    private val projectsExtensionName: Property<String>
    private val versionCatalogs: DefaultVersionCatalogBuilderContainer

    private var mutable = true

    init {
        this.repositoryMode = objects.property<RepositoriesMode>(RepositoriesMode::class.java).convention(RepositoriesMode.PREFER_PROJECT)
        this.rulesMode = objects.property<RulesMode>(RulesMode::class.java).convention(RulesMode.PREFER_PROJECT)
        this.dependencyResolutionServices = locking().of<DependencyResolutionServices?>(Supplier { dependencyManagementServices.newDetachedResolver(StandaloneDomainObjectContext.ANONYMOUS) })
        this.librariesExtensionName = objects.property<String>(String::class.java).convention("libs")
        this.projectsExtensionName = objects.property<String>(String::class.java).convention("projects")
        this.versionCatalogs = objects.newInstance<DefaultVersionCatalogBuilderContainer>(
            DefaultVersionCatalogBuilderContainer::class.java, collectionCallbackActionDecorator, objects,
            context, dependencyResolutionServices
        )
    }

    override fun repositories(repositoryConfiguration: Action<in RepositoryHandler>) {
        repositoryConfiguration.execute(dependencyResolutionServices.get()!!.getResolveRepositoryHandler())
    }

    override fun components(registration: Action<in ComponentMetadataHandler>) {
        assertMutable()
        componentMetadataRulesActions.add(registration)
    }

    override fun getComponents(): ComponentMetadataHandler {
        return registar
    }

    override fun getRepositories(): RepositoryHandler {
        return dependencyResolutionServices.get()!!.getResolveRepositoryHandler()
    }

    override fun getRepositoriesMode(): Property<RepositoriesMode> {
        return repositoryMode
    }

    override fun getRulesMode(): Property<RulesMode> {
        return rulesMode
    }

    override fun getConfiguredRepositoriesMode(): DependencyResolutionManagementInternal.RepositoriesModeInternal {
        repositoryMode.finalizeValue()
        return DependencyResolutionManagementInternal.RepositoriesModeInternal.of(repositoryMode.get())
    }

    override fun versionCatalogs(spec: Action<in MutableVersionCatalogContainer>) {
        spec.execute(versionCatalogs)
    }

    override fun getVersionCatalogs(): MutableVersionCatalogContainer {
        return versionCatalogs
    }

    override fun getConfiguredRulesMode(): DependencyResolutionManagementInternal.RulesModeInternal {
        rulesMode.finalizeValue()
        return DependencyResolutionManagementInternal.RulesModeInternal.of(rulesMode.get())
    }

    override fun getDefaultProjectsExtensionName(): Property<String> {
        return projectsExtensionName
    }

    override fun getDefaultLibrariesExtensionName(): Property<String> {
        return librariesExtensionName
    }

    override fun getDependenciesModelBuilders(): MutableList<VersionCatalogBuilder> {
        return ImmutableList.copyOf<VersionCatalogBuilder>(versionCatalogs)
    }

    override fun configureProject(project: ProjectInternal) {
        if (!getConfiguredRepositoriesMode().useProjectRepositories()) {
            project.getRepositories().whenObjectAdded(Action { artifactRepository: ArtifactRepository? -> this.repoMutationDisallowedOnProject(artifactRepository!!) })
        }
        if (!getConfiguredRulesMode().useProjectRules()) {
            val components = project.getDependencies().getComponents() as ComponentMetadataHandlerInternal
            components.onAddRule(Consumer { ruleName: DisplayName? -> this.ruleMutationDisallowedOnProject(ruleName!!) })
        }
    }

    override fun preventFromFurtherMutation() {
        this.mutable = false
        val repositoryHandler: NamedDomainObjectList<ArtifactRepository> = getRepositories()
        repositoryHandler.whenObjectAdded(Action { artifactRepository: ArtifactRepository? -> this.mutationDisallowed(artifactRepository!!) })
        repositoryHandler.whenObjectRemoved(Action { artifactRepository: ArtifactRepository? -> this.mutationDisallowed(artifactRepository!!) })
    }

    private fun assertMutable() {
        if (!mutable) {
            throw InvalidUserCodeException("Mutation of dependency resolution management in settings is only allowed during settings evaluation")
        }
    }

    private fun mutationDisallowed(artifactRepository: ArtifactRepository) {
        throw InvalidUserCodeException("Mutation of repositories declared in settings is only allowed during settings evaluation")
    }

    private fun repoMutationDisallowedOnProject(artifactRepository: ArtifactRepository) {
        val current = context.current()
        var displayName = if (current == null) null else current.getSource().getDisplayName()
        if (displayName == null) {
            displayName = UNKNOWN_CODE
        }
        val message = "Build was configured to prefer settings repositories over project repositories but repository '" + artifactRepository.getName() + "' was added by " + displayName
        when (getConfiguredRepositoriesMode()) {
            DependencyResolutionManagementInternal.RepositoriesModeInternal.FAIL_ON_PROJECT_REPOS -> throw InvalidUserCodeException(message)
            DependencyResolutionManagementInternal.RepositoriesModeInternal.PREFER_SETTINGS -> LOGGER.warn(message)
            else -> {}
        }
    }

    private fun ruleMutationDisallowedOnProject(ruleName: DisplayName) {
        val current = context.current()
        var displayName = if (current == null) null else current.getSource().getDisplayName()
        if (displayName == null) {
            displayName = UNKNOWN_CODE
        }
        val message = "Build was configured to prefer settings component metadata rules over project rules but rule '" + ruleName + "' was added by " + displayName
        when (getConfiguredRulesMode()) {
            DependencyResolutionManagementInternal.RulesModeInternal.FAIL_ON_PROJECT_RULES -> throw InvalidUserCodeException(message)
            DependencyResolutionManagementInternal.RulesModeInternal.PREFER_SETTINGS -> LOGGER.warn(message)
            else -> {}
        }
    }

    override fun applyRules(target: ComponentMetadataHandler) {
        for (rule in componentMetadataRulesActions) {
            rule.execute(target)
        }
    }

    private inner class ComponentMetadataRulesRegistar : ComponentMetadataHandler {
        override fun all(rule: Action<in ComponentMetadataDetails>): ComponentMetadataHandler {
            components(Action { h: ComponentMetadataHandler -> h.all(rule) })
            return this
        }

        override fun all(rule: Closure<*>): ComponentMetadataHandler {
            components(Action { h: ComponentMetadataHandler -> h.all(rule) })
            return this
        }

        @Deprecated("")
        override fun all(ruleSource: Any): ComponentMetadataHandler {
            components(Action { h: ComponentMetadataHandler -> h.all(ruleSource) })
            return this
        }

        override fun all(rule: Class<out ComponentMetadataRule>): ComponentMetadataHandler {
            components(Action { h: ComponentMetadataHandler -> h.all(rule) })
            return this
        }

        override fun all(rule: Class<out ComponentMetadataRule>, configureAction: Action<in ActionConfiguration>): ComponentMetadataHandler {
            components(Action { h: ComponentMetadataHandler -> h.all(rule, configureAction) })
            return this
        }

        override fun withModule(id: Any, rule: Action<in ComponentMetadataDetails>): ComponentMetadataHandler {
            components(Action { h: ComponentMetadataHandler -> h.withModule(id, rule) })
            return this
        }

        override fun withModule(id: Any, rule: Closure<*>): ComponentMetadataHandler {
            components(Action { h: ComponentMetadataHandler -> h.withModule(id, rule) })
            return this
        }

        @Deprecated("")
        override fun withModule(id: Any, ruleSource: Any): ComponentMetadataHandler {
            components(Action { h: ComponentMetadataHandler -> h.withModule(id, ruleSource) })
            return this
        }

        override fun withModule(id: Any, rule: Class<out ComponentMetadataRule>): ComponentMetadataHandler {
            components(Action { h: ComponentMetadataHandler -> h.withModule(id, rule) })
            return this
        }

        override fun withModule(id: Any, rule: Class<out ComponentMetadataRule>, configureAction: Action<in ActionConfiguration>): ComponentMetadataHandler {
            components(Action { h: ComponentMetadataHandler -> h.withModule(id, rule, configureAction) })
            return this
        }
    }

    companion object {
        private val UNKNOWN_CODE = Describables.of("unknown code")
        private val LOGGER: Logger = getLogger(DependencyResolutionManagement::class.java)!!
    }
}
