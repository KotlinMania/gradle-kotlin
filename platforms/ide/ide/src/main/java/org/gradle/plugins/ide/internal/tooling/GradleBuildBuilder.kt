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
package org.gradle.plugins.ide.internal.tooling

import com.google.common.collect.ImmutableList
import org.gradle.api.GradleException
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.Factory.create
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.build.RootBuildState
import org.gradle.internal.composite.BuildIncludeListener
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.problems.failure.FailureFactory
import org.gradle.plugins.ide.internal.tooling.model.BasicGradleProject
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleBuild
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.provider.model.internal.BuildScopeModelBuilder
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderResultInternal
import org.jspecify.annotations.NullMarked
import java.util.Optional
import java.util.function.Consumer

@NullMarked
class GradleBuildBuilder(
    private val buildStateRegistry: BuildStateRegistry,
    private val failedIncludedBuildsRegistry: BuildIncludeListener,
    private val failureFactory: FailureFactory
) : BuildScopeModelBuilder {
    override fun canBuild(modelName: String): Boolean {
        return GRADLE_BUILD_MODEL_NAME == modelName
    }

    override fun create(target: BuildState): ToolingModelBuilderResultInternal {
        return GradleBuildBuilder.ResilientGradleBuildCreator(target).create()
    }

    private inner class ResilientGradleBuildCreator(private val target: BuildState) {
        private val all: MutableMap<BuildState, DefaultGradleBuild> = LinkedHashMap<BuildState, DefaultGradleBuild>()
        private val failures: MutableCollection<Failure> = LinkedHashSet<Failure>()

        fun create(): ToolingModelBuilderResultInternal {
            ensureProjectsLoaded(target)
            val gradleBuild = convert(target)
            val allFailures: MutableList<Failure> = failures.stream()
                .distinct()
                .collect(ImmutableList.toImmutableList<Failure>())
            return ToolingModelBuilderResultInternal.of(gradleBuild, allFailures)
        }

        fun addIncludedBuilds(gradle: GradleInternal, model: DefaultGradleBuild) {
            for (reference in gradle.includedBuilds()) {
                val target = reference.getTarget()
                if (target is IncludedBuildState || target is RootBuildState) {
                    model.addIncludedBuild(convert(target))
                } else {
                    throw IllegalStateException("Unknown build type: " + reference.javaClass.getName())
                }
            }
        }

        fun addAllImportableBuilds(targetBuild: BuildState, gradle: GradleInternal, model: DefaultGradleBuild) {
            if (gradle.getParent() == null) {
                val allBuilds: MutableList<DefaultGradleBuild> = ArrayList<DefaultGradleBuild>()
                buildStateRegistry.visitBuilds(Consumer { buildState: BuildState? ->
                    // Do not include the root build and only include builds that are intended to be imported into an IDE
                    if (buildState !== targetBuild && buildState!!.isImportableBuild()) {
                        allBuilds.add(convert(buildState))
                    }
                })
                model.addBuilds(allBuilds)
            }
        }

        fun ensureProjectsLoaded(target: BuildState) {
            try {
                target.ensureProjectsLoaded()
            } catch (e: GradleException) {
                failures.add(failureFactory.create(e))
            }
        }

        fun convert(targetBuild: BuildState): DefaultGradleBuild {
            var model = all.get(targetBuild)
            if (model != null) {
                return model
            }
            model = DefaultGradleBuild()
            all.put(targetBuild, model)

            ensureProjectsLoaded(targetBuild)

            val gradle = targetBuild.getMutableModel()
            addProjectsAndBuildIdentifier(targetBuild, model)
            try {
                addFailedBuilds(targetBuild, model)
                addIncludedBuilds(gradle, model)
            } catch (e: IllegalStateException) {
                //Ignore, happens when included builds are not accessible, but we need this for resiliency
            }
            addAllImportableBuilds(targetBuild, gradle, model)
            return model
        }

        fun addProjectsAndBuildIdentifier(targetBuild: BuildState, model: DefaultGradleBuild) {
            // If projects are loaded, just add them normally
            if (targetBuild.isProjectsLoaded()) {
                addProjects(targetBuild, model)
                return
            }

            // Else try to find a root project from the settings
            val brokenBuilds = failedIncludedBuildsRegistry.getBrokenBuilds()
            val brokenSettings = failedIncludedBuildsRegistry.getBrokenSettings()
            if (!brokenBuilds.contains(targetBuild) && !brokenSettings.isEmpty()) {
                val brokenSettingsInternal = findBrokenSettingsForBuild(targetBuild, brokenSettings)
                if (brokenSettingsInternal.isPresent()) {
                    val rootProject = brokenSettingsInternal.get().getRootProject()
                    val root = convertRoot(targetBuild, rootProject)
                    model.setRootProject(root)
                    model.addProject(root)
                }
            }

            // Build identifier is set via a root project,
            // so if a root project is not set, try to set build identifier differently
            if (model.getRootProject() == null && targetBuild is IncludedBuildState
                && targetBuild.getBuildDefinition().getBuildRootDir() != null
            ) {
                model.setBuildIdentifier(DefaultBuildIdentifier(targetBuild.getBuildDefinition().getBuildRootDir()))
            }
        }

        fun findBrokenSettingsForBuild(buildState: BuildState, brokenSettings: MutableSet<SettingsInternal>): Optional<SettingsInternal> {
            val buildRootDir = buildState.getBuildRootDir()
            return brokenSettings.stream()
                .filter { settings: SettingsInternal? -> settings!!.getRootDir() == buildRootDir }
                .findFirst()
        }

        fun convertRoot(owner: BuildState, project: ProjectDescriptor): BasicGradleProject {
            val id = DefaultProjectIdentifier(owner.getBuildRootDir(), project.getPath())
            return BasicGradleProject()
                .setName(project.getName())
                .setProjectIdentifier(id)
                .setBuildTreePath(project.getPath())
                .setProjectDirectory(project.getProjectDir())
        }

        fun addFailedBuilds(targetBuild: BuildState, model: DefaultGradleBuild) {
            for (entry in failedIncludedBuildsRegistry.getBrokenBuilds()) {
                val parent = entry.getParent()
                if (parent != null && parent == targetBuild) {
                    model.addIncludedBuild(convert(entry))
                }
            }
        }
    }

    companion object {
        val GRADLE_BUILD_MODEL_NAME: String = GradleBuild::class.java.getName()

        private fun convert(owner: BuildState, project: ProjectState, convertedProjects: MutableMap<ProjectState, BasicGradleProject>): BasicGradleProject {
            val id = DefaultProjectIdentifier(owner.getBuildRootDir(), project.getProjectPath().asString())
            val converted = BasicGradleProject()
                .setName(project.getName())
                .setProjectIdentifier(id)
                .setBuildTreePath(project.getIdentityPath().asString())
                .setProjectDirectory(project.getProjectDir())
            if (project.getParent() != null) {
                converted.setParent(convertedProjects.get(project.getParent()))
            }
            convertedProjects.put(project, converted)
            for (child in project.getChildProjects()) {
                converted.addChild(convert(owner, child, convertedProjects))
            }
            return converted
        }

        private fun addProjects(target: BuildState, model: DefaultGradleBuild) {
            val convertedProjects: MutableMap<ProjectState, BasicGradleProject> = LinkedHashMap<ProjectState, BasicGradleProject>()

            val rootProject = target.getProjects().getRootProject()
            val convertedRootProject: BasicGradleProject = convert(target, rootProject, convertedProjects)
            model.setRootProject(convertedRootProject)

            for (project in target.getProjects().getAllProjects()) {
                model.addProject(convertedProjects.get(project))
            }
        }
    }
}
