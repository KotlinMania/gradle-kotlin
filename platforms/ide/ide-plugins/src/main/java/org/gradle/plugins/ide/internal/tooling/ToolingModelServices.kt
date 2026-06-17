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
package org.gradle.plugins.ide.internal.tooling

import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.project.ProjectStateLookup
import org.gradle.api.internal.project.ProjectTaskLister
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.composite.BuildIncludeListener
import org.gradle.internal.problems.failure.FailureFactory
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.AbstractGradleModuleServices
import org.gradle.plugins.ide.internal.configurer.DefaultUniqueProjectNameProvider
import org.gradle.plugins.ide.internal.configurer.UniqueProjectNameProvider
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.internal.BuildScopeToolingModelBuilderRegistryAction
import org.gradle.tooling.provider.model.internal.IntermediateToolingModelProvider
import org.gradle.tooling.provider.model.internal.PluginApplyingBuilder

class ToolingModelServices : AbstractGradleModuleServices() {
    public override fun registerBuildServices(registration: ServiceRegistration) {
        registration.addProvider(BuildScopeToolingServices())
    }

    private class BuildScopeToolingServices : ServiceRegistrationProvider {
        @Provides
        protected fun createBuildProjectRegistry(projectStateLookup: ProjectStateLookup): UniqueProjectNameProvider {
            return DefaultUniqueProjectNameProvider(projectStateLookup)
        }

        @Provides
        protected fun createIdeBuildScopeToolingModelBuilderRegistryAction(
            taskLister: ProjectTaskLister,
            projectPublicationRegistry: ProjectPublicationRegistry,
            fileCollectionFactory: FileCollectionFactory?,
            buildStateRegistry: BuildStateRegistry,
            projectStateLookup: ProjectStateLookup?,
            buildModelParameters: BuildModelParameters,
            intermediateToolingModelProvider: IntermediateToolingModelProvider,
            failedIncludedBuildsRegistry: BuildIncludeListener,
            failureFactory: FailureFactory
        ): BuildScopeToolingModelBuilderRegistryAction {
            return object : BuildScopeToolingModelBuilderRegistryAction {
                override fun execute(registry: ToolingModelBuilderRegistry) {
                    val isolatedProjects = buildModelParameters.isIsolatedProjects()
                    val gradleProjectBuilder = createGradleProjectBuilder(isolatedProjects)
                    val ideaModelBuilder = createIdeaModelBuilder(isolatedProjects, gradleProjectBuilder)
                    registry.register(RunBuildDependenciesTaskBuilder())
                    registry.register(RunEclipseTasksBuilder())
                    registry.register(EclipseModelBuilder(gradleProjectBuilder, projectStateLookup))
                    registry.register(ideaModelBuilder)
                    registry.register(gradleProjectBuilder)
                    registry.register(GradleBuildBuilder(buildStateRegistry, failedIncludedBuildsRegistry, failureFactory))
                    registry.register(BasicIdeaModelBuilder(ideaModelBuilder))
                    registry.register(BuildInvocationsBuilder(taskLister))
                    registry.register(PublicationsBuilder(projectPublicationRegistry))
                    registry.register(BuildEnvironmentBuilder(fileCollectionFactory))
                    registry.register(HelpBuilder())
                    registry.register(IsolatedGradleProjectInternalBuilder())
                    registry.register(IsolatedIdeaModuleInternalBuilder())
                    registry.register(PluginApplyingBuilder())
                    registry.register(GradleDslBaseScriptModelBuilder())
                }

                fun createIdeaModelBuilder(isolatedProjects: Boolean, gradleProjectBuilder: GradleProjectBuilderInternal): IdeaModelBuilderInternal {
                    return if (isolatedProjects) IsolatedProjectsSafeIdeaModelBuilder(intermediateToolingModelProvider, gradleProjectBuilder) else IdeaModelBuilder(gradleProjectBuilder)
                }

                fun createGradleProjectBuilder(isolatedProjects: Boolean): GradleProjectBuilderInternal {
                    return if (isolatedProjects) IsolatedProjectsSafeGradleProjectBuilder(intermediateToolingModelProvider) else GradleProjectBuilder()
                }
            }
        }
    }
}
