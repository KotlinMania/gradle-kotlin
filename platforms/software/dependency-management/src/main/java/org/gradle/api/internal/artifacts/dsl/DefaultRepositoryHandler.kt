/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.InvalidUserCodeException
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.ExclusiveContentRepository
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository
import org.gradle.api.artifacts.repositories.InclusiveRepositoryContentDescriptor
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.ConfigureByMapAction
import org.gradle.api.internal.artifacts.BaseRepositoryFactory
import org.gradle.api.internal.artifacts.DefaultArtifactRepositoryContainer
import org.gradle.internal.Actions
import org.gradle.internal.Factory
import org.gradle.internal.deprecation.DeprecationLogger.deprecateMethod
import org.gradle.internal.reflect.Instantiator
import org.gradle.util.internal.CollectionUtils.flattenCollections
import org.gradle.util.internal.ConfigureUtil
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.contains

class DefaultRepositoryHandler(private val repositoryFactory: BaseRepositoryFactory, private val instantiator: Instantiator, decorator: CollectionCallbackActionDecorator) :
    DefaultArtifactRepositoryContainer(
        instantiator, decorator
    ), RepositoryHandlerInternal {
    private var exclusiveContentInUse = false

    override fun flatDir(action: Action<in FlatDirectoryArtifactRepository>): FlatDirectoryArtifactRepository {
        return addRepository<FlatDirectoryArtifactRepository?>(
            repositoryFactory.createFlatDirRepository(),
            org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler.Companion.FLAT_DIR_DEFAULT_NAME,
            action
        )!!
    }

    override fun flatDir(configureClosure: Closure<*>): FlatDirectoryArtifactRepository {
        return flatDir(ConfigureUtil.configureUsing<FlatDirectoryArtifactRepository>(configureClosure))
    }

    @Deprecated("")
    override fun flatDir(args: MutableMap<String, *>): FlatDirectoryArtifactRepository {
        deprecateMethod(RepositoryHandler::class.java, "flatDir(Map)")
            .withAdvice("Use flatDir(Action) instead.")!!
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "deprecated_repository_handler_map_overloads")!!
            .nagUser()
        val modifiedArgs: MutableMap<String, Any> = HashMap<String, Any>(args)
        if (modifiedArgs.containsKey("dirs")) {
            modifiedArgs.put("dirs", flattenCollections(modifiedArgs.get("dirs")))
        }
        return flatDir(ConfigureByMapAction<FlatDirectoryArtifactRepository>(modifiedArgs))
    }

    override fun gradlePluginPortal(): ArtifactRepository {
        return addRepository<ArtifactRepository?>(
            repositoryFactory.createGradlePluginPortal(),
            org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler.Companion.GRADLE_PLUGIN_PORTAL_REPO_NAME
        )!!
    }

    override fun gradlePluginPortal(action: Action<in ArtifactRepository>): ArtifactRepository {
        return addRepository<ArtifactRepository?>(
            repositoryFactory.createGradlePluginPortal(),
            org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler.Companion.GRADLE_PLUGIN_PORTAL_REPO_NAME,
            action
        )!!
    }

    override fun mavenCentral(): MavenArtifactRepository {
        return addRepository<MavenArtifactRepository?>(
            repositoryFactory.createMavenCentralRepository(),
            org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME
        )!!
    }

    override fun mavenCentral(action: Action<in MavenArtifactRepository>): MavenArtifactRepository {
        return addRepository<MavenArtifactRepository?>(
            repositoryFactory.createMavenCentralRepository(),
            org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME,
            action
        )!!
    }

    @Deprecated("")
    override fun mavenCentral(args: MutableMap<String, *>): MavenArtifactRepository {
        deprecateMethod(RepositoryHandler::class.java, "mavenCentral(Map)")
            .withAdvice("Use mavenCentral(Action) instead.")!!
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "deprecated_repository_handler_map_overloads")!!
            .nagUser()
        val modifiedArgs: MutableMap<String, Any> = HashMap<String, Any>(args)
        return addRepository<MavenArtifactRepository?>(
            repositoryFactory.createMavenCentralRepository(),
            org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME,
            org.gradle.api.internal.ConfigureByMapAction<org.gradle.api.artifacts.repositories.MavenArtifactRepository>(modifiedArgs)
        )!!
    }

    override fun mavenLocal(): MavenArtifactRepository {
        return addRepository<MavenArtifactRepository?>(
            repositoryFactory.createMavenLocalRepository(),
            org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME
        )!!
    }

    override fun mavenLocal(action: Action<in MavenArtifactRepository>): MavenArtifactRepository {
        return addRepository<MavenArtifactRepository?>(
            repositoryFactory.createMavenLocalRepository(),
            org.gradle.api.artifacts.ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME,
            action
        )!!
    }

    override fun google(): MavenArtifactRepository {
        return addRepository<MavenArtifactRepository?>(
            repositoryFactory.createGoogleRepository(),
            org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler.Companion.GOOGLE_REPO_NAME
        )!!
    }

    override fun google(action: Action<in MavenArtifactRepository>): MavenArtifactRepository {
        return addRepository<MavenArtifactRepository?>(
            repositoryFactory.createGoogleRepository(),
            org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler.Companion.GOOGLE_REPO_NAME,
            action
        )!!
    }

    override fun maven(action: Action<in MavenArtifactRepository>): MavenArtifactRepository {
        return addRepository<MavenArtifactRepository?>(
            repositoryFactory.createMavenRepository(),
            org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler.Companion.MAVEN_REPO_DEFAULT_NAME,
            action
        )!!
    }

    override fun maven(closure: Closure<*>): MavenArtifactRepository {
        return maven(ConfigureUtil.configureUsing<MavenArtifactRepository>(closure))
    }

    override fun ivy(action: Action<in IvyArtifactRepository>): IvyArtifactRepository {
        return addRepository<IvyArtifactRepository?>(
            repositoryFactory.createIvyRepository(),
            org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler.Companion.IVY_REPO_DEFAULT_NAME,
            action
        )!!
    }

    override fun ivy(closure: Closure<*>): IvyArtifactRepository {
        return ivy(ConfigureUtil.configureUsing<IvyArtifactRepository>(closure))
    }

    override fun exclusiveContent(action: Action<in ExclusiveContentRepository>) {
        val spec: ExclusiveContentRepositorySpec = org.gradle.internal.Cast.uncheckedCast<ExclusiveContentRepositorySpec?>(
            instantiator.newInstance<org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler.ExclusiveContentRepositorySpec>(
                org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler.ExclusiveContentRepositorySpec::class.java,
                this
            )
        )!!
        spec.apply(action)
        exclusiveContentInUse = true
    }

    override fun isExclusiveContentInUse(): Boolean {
        return exclusiveContentInUse
    }

    class ExclusiveContentRepositorySpec(private val repositories: RepositoryHandler) : ExclusiveContentRepository {
        private val forRepositories: MutableList<Factory<out ArtifactRepository>> = ArrayList<Factory<out ArtifactRepository>>(2)
        private var filter: Action<in InclusiveRepositoryContentDescriptor>? = null

        override fun forRepository(repositoryProducer: Factory<out ArtifactRepository?>): ExclusiveContentRepository {
            forRepositories.add(repositoryProducer)
            return this
        }

        override fun forRepositories(vararg repositories: ArtifactRepository): ExclusiveContentRepository {
            Stream.of<ArtifactRepository>(*repositories).forEach { r: ArtifactRepository? -> forRepositories.add(org.gradle.internal.Factory { r }) }
            return this
        }

        override fun filter(config: Action<in InclusiveRepositoryContentDescriptor>): ExclusiveContentRepository {
            filter = if (filter == null) config else Actions.composite<InclusiveRepositoryContentDescriptor>(filter, config)
            return this
        }

        fun apply(action: Action<in ExclusiveContentRepository>) {
            action.execute(this)
            if (forRepositories.isEmpty()) {
                throw InvalidUserCodeException("You must declare the repository using forRepository { ... }")
            }
            if (filter == null) {
                throw InvalidUserCodeException("You must specify the filter for the repository using filter { ... }")
            }
            val targetRepositories: MutableSet<out ArtifactRepository> = forRepositories.stream().map { obj: Factory<*>? -> obj!!.create() }.collect(Collectors.toSet())
            val forExclusivity: Action<in RepositoryContentDescriptor> = Companion.transformForExclusivity(filter!!)
            this.repositories.all(Action { repo: ArtifactRepository? ->
                if (targetRepositories.contains(repo)) {
                    repo!!.content(filter!!)
                } else {
                    repo!!.content(forExclusivity)
                }
            })
        }
    }

    companion object {
        const val GRADLE_PLUGIN_PORTAL_REPO_NAME: String = "Gradle Central Plugin Repository"
        const val GOOGLE_REPO_NAME: String = "Google"

        const val FLAT_DIR_DEFAULT_NAME: String = "flatDir"
        private const val MAVEN_REPO_DEFAULT_NAME = "maven"
        private const val IVY_REPO_DEFAULT_NAME = "ivy"

        private fun transformForExclusivity(config: Action<in InclusiveRepositoryContentDescriptor>): Action<in RepositoryContentDescriptor> {
            return Action { desc: RepositoryContentDescriptor ->
                config.execute(object : InclusiveRepositoryContentDescriptor {
                    override fun includeGroup(group: String) {
                        desc.excludeGroup(group)
                    }

                    override fun includeGroupAndSubgroups(groupPrefix: String) {
                        desc.excludeGroupAndSubgroups(groupPrefix)
                    }

                    override fun includeGroupByRegex(groupRegex: String) {
                        desc.excludeGroupByRegex(groupRegex)
                    }

                    override fun includeModule(group: String, moduleName: String) {
                        desc.excludeModule(group, moduleName)
                    }

                    override fun includeModuleByRegex(groupRegex: String, moduleNameRegex: String) {
                        desc.excludeModuleByRegex(groupRegex, moduleNameRegex)
                    }

                    override fun includeVersion(group: String, moduleName: String, version: String) {
                        desc.excludeVersion(group, moduleName, version)
                    }

                    override fun includeVersionByRegex(groupRegex: String, moduleNameRegex: String, versionRegex: String) {
                        desc.excludeVersionByRegex(groupRegex, moduleNameRegex, versionRegex)
                    }
                })
            }
        }
    }
}
