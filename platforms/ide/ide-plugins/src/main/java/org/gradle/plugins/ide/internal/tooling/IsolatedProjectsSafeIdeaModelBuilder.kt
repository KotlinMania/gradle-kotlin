/*
 * Copyright 2023 the original author or authors.
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
import com.google.common.collect.Streams
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.IncludedBuildState
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.internal.IdeaModuleSupport
import org.gradle.plugins.ide.idea.internal.IdeaProjectInternal
import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaContentRoot
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaJavaLanguageSettings
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaLanguageLevel
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaModule
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaProject
import org.gradle.plugins.ide.internal.tooling.idea.IsolatedIdeaModuleInternal
import org.gradle.plugins.ide.internal.tooling.java.DefaultInstalledJdk
import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder
import org.gradle.tooling.provider.model.internal.IntermediateToolingModelProvider
import org.jspecify.annotations.NullMarked
import java.util.Objects
import java.util.function.BiFunction
import java.util.function.Function
import java.util.stream.Collectors

/**
 * Builds the [IdeaProject] model in Isolated Projects-compatible way.
 */
@NullMarked
class IsolatedProjectsSafeIdeaModelBuilder(private val intermediateToolingModelProvider: IntermediateToolingModelProvider, private val gradleProjectBuilder: GradleProjectBuilderInternal) :
    IdeaModelBuilderInternal, ParameterizedToolingModelBuilder<IdeaModelParameter> {
    override fun canBuild(modelName: String): Boolean {
        return modelName == MODEL_NAME
    }

    override fun getParameterType(): Class<IdeaModelParameter> {
        return IdeaModelParameter::class.java
    }

    override fun buildAll(modelName: String, parameter: IdeaModelParameter, project: Project): Any {
        return buildForRoot(project, parameter.getOfflineDependencyResolution())
    }

    override fun buildAll(modelName: String, project: Project): DefaultIdeaProject {
        return buildForRoot(project, false)
    }

    override fun buildForRoot(project: Project, offlineDependencyResolution: Boolean): DefaultIdeaProject {
        requireRootProject(project)

        // Ensure unique module names for dependencies substituted from included builds
        val rootProjectState = (project as ProjectInternal).getOwner()
        val owningBuild = rootProjectState.getOwner()
        applyIdeaPluginToBuildTree(owningBuild)

        val parameter: IdeaModelParameter = createParameter(offlineDependencyResolution)
        return createIdeaProjectTreeForRootProject(project, parameter)
    }

    private fun applyIdeaPluginToBuildTree(build: BuildState) {
        applyIdeaPluginRecursively(build, ArrayList<BuildState>())
    }

    private fun applyIdeaPluginRecursively(build: BuildState, alreadyProcessed: MutableList<BuildState>) {
        val rootProject = build.getProjects().getRootProject()
        intermediateToolingModelProvider.applyPlugin<IdeaPlugin>(rootProject, getProjectsInBuild(build), IdeaPlugin::class.java)

        for (reference in build.getMutableModel().includedBuilds()) {
            val childBuild = reference.getTarget()
            if (childBuild is IncludedBuildState) {
                if (!alreadyProcessed.contains(childBuild)) {
                    alreadyProcessed.add(childBuild)
                    applyIdeaPluginRecursively(childBuild, alreadyProcessed)
                }
            }
        }
    }

    private fun createIdeaProjectTreeForRootProject(rootProject: Project, parameter: IdeaModelParameter): DefaultIdeaProject {
        // Currently, applying the plugin here is redundant due to `applyIdeaPluginToBuildTree`.
        // However, the latter should go away in the future, while the application here is inherent to the builder
        rootProject.getPluginManager().apply(IdeaPlugin::class.java)
        val ideaModelExt = rootProject.getPlugins().getPlugin<IdeaPlugin>(IdeaPlugin::class.java).getModel()
        val ideaProjectExt = ideaModelExt.getProject() as IdeaProjectInternal

        val rootProjectState = (rootProject as ProjectInternal).getOwner()
        val projectsInBuild: MutableList<ProjectState> = getProjectsInBuild(rootProjectState.getOwner())
        val allIsolatedIdeaModules = getIsolatedIdeaModules(rootProjectState, projectsInBuild, parameter)

        val languageLevel: IdeaLanguageLevel = resolveRootLanguageLevel(ideaProjectExt, allIsolatedIdeaModules)
        val targetBytecodeVersion: JavaVersion = resolveRootTargetBytecodeVersion(ideaProjectExt, allIsolatedIdeaModules)

        val out: DefaultIdeaProject = buildWithoutChildren(ideaProjectExt, languageLevel, targetBytecodeVersion)

        // Important to build GradleProject after the IsolatedIdeaModuleInternal requests,
        // to make sure IdeaPlugin is applied to each project and its tasks are registered
        val rootGradleProject = gradleProjectBuilder.buildForRoot(rootProject)
        val ideaModuleBuilder = IsolatedProjectsSafeIdeaModelBuilder.IdeaModuleBuilder(rootGradleProject!!, languageLevel, targetBytecodeVersion)

        val ideaModules = Streams.zip<ProjectState, IsolatedIdeaModuleInternal, DefaultIdeaModule>(
            projectsInBuild.stream(),
            allIsolatedIdeaModules.stream(),
            BiFunction { project: ProjectState, isolatedIdeaModule: IsolatedIdeaModuleInternal ->
                val ideaModuleForProject = ideaModuleBuilder.buildWithoutParent(project, isolatedIdeaModule)
                ideaModuleForProject.setParent(out)
                ideaModuleForProject
            }).collect(Collectors.toList())
        out.setChildren(ideaModules)

        return out
    }

    private fun getIsolatedIdeaModules(rootProject: ProjectState, projectsInBuild: MutableList<ProjectState>, parameter: IdeaModelParameter): MutableList<IsolatedIdeaModuleInternal> {
        return intermediateToolingModelProvider
            .getModels<IsolatedIdeaModuleInternal>(rootProject, projectsInBuild, IsolatedIdeaModuleInternal::class.java, parameter)
    }

    private class IdeaModuleBuilder(
        private val rootGradleProject: DefaultGradleProject,
        private val ideaProjectLanguageLevel: IdeaLanguageLevel,
        private val ideaProjectTargetBytecodeVersion: JavaVersion
    ) {
        fun buildWithoutParent(project: ProjectState, isolatedIdeaModule: IsolatedIdeaModuleInternal): DefaultIdeaModule {
            val model = DefaultIdeaModule()
                .setName(isolatedIdeaModule.name)
                .setGradleProject(rootGradleProject.findByPath(project.getIdentity().getProjectPath().asString())!!)
                .setContentRoots(mutableListOf<DefaultIdeaContentRoot?>(isolatedIdeaModule.getContentRoot()))
                .setJdkName(isolatedIdeaModule.jdkName)
                .setCompilerOutput(isolatedIdeaModule.compilerOutput)

            val javaExtensionAvailableOnModule = isolatedIdeaModule.javaSourceCompatibility != null
                    || isolatedIdeaModule.javaTargetCompatibility != null
            if (javaExtensionAvailableOnModule) {
                val languageLevel: IdeaLanguageLevel = resolveLanguageLevel(isolatedIdeaModule)
                val moduleLanguageLevelOverride: IdeaLanguageLevel? = takeIfDifferent<IdeaLanguageLevel>(ideaProjectLanguageLevel, languageLevel)
                val targetBytecodeVersion: JavaVersion? = resolveTargetBytecodeVersion(isolatedIdeaModule)
                val moduleTargetBytecodeVersionOverride: JavaVersion? = takeIfDifferent<JavaVersion>(ideaProjectTargetBytecodeVersion, targetBytecodeVersion)
                model.setJavaLanguageSettings(
                    DefaultIdeaJavaLanguageSettings()
                        .setSourceLanguageLevel(IdeaModuleBuilderSupport.convertToJavaVersion(moduleLanguageLevelOverride))
                        .setTargetBytecodeVersion(moduleTargetBytecodeVersionOverride)
                )
            }

            model.setDependencies(isolatedIdeaModule.dependencies)

            return model
        }

        companion object {
            private fun resolveTargetBytecodeVersion(isolatedIdeaModule: IsolatedIdeaModuleInternal): JavaVersion? {
                val targetBytecodeVersionConvention = isolatedIdeaModule.javaTargetCompatibility
                val explicitTargetBytecodeVersion = isolatedIdeaModule.explicitTargetBytecodeVersion
                return getPropertyValue<JavaVersion>(explicitTargetBytecodeVersion, targetBytecodeVersionConvention)
            }

            private fun resolveLanguageLevel(isolatedIdeaModule: IsolatedIdeaModuleInternal): IdeaLanguageLevel {
                val languageLevelConvention = isolatedIdeaModule.javaSourceCompatibility
                val explicitLanguageLevel = isolatedIdeaModule.explicitSourceLanguageLevel
                return org.gradle.plugins.ide.internal.tooling.IsolatedProjectsSafeIdeaModelBuilder.IdeaModuleBuilder.Companion.getPropertyValue<IdeaLanguageLevel>(
                    explicitLanguageLevel,
                    org.gradle.plugins.ide.idea.model.IdeaLanguageLevel(languageLevelConvention)
                )!!
            }

            private fun <T> takeIfDifferent(commonValue: T?, value: T?): T? {
                return if (commonValue == value) null else value
            }

            private fun <T> getPropertyValue(value: T?, convention: T?): T? {
                return if (value != null) value else convention
            }
        }
    }

    companion object {
        private val MODEL_NAME: String = IdeaProject::class.java.getName()

        private fun requireRootProject(project: Project) {
            require(project == project.getRootProject()) { String.format("%s can only be requested on the root project, got %s", MODEL_NAME, project) }
        }

        private fun getProjectsInBuild(build: BuildState): MutableList<ProjectState> {
            return ImmutableList.copyOf<ProjectState>(build.getProjects().getAllProjects())
        }

        private fun buildWithoutChildren(ideaProjectExt: IdeaProjectInternal, languageLevel: IdeaLanguageLevel, targetBytecodeVersion: JavaVersion): DefaultIdeaProject {
            return DefaultIdeaProject()
                .setName(ideaProjectExt.getName())
                .setJdkName(ideaProjectExt.getJdkName())
                .setLanguageLevel(DefaultIdeaLanguageLevel(languageLevel.level))
                .setJavaLanguageSettings(
                    DefaultIdeaJavaLanguageSettings()
                        .setSourceLanguageLevel(IdeaModuleBuilderSupport.convertToJavaVersion(languageLevel))
                        .setTargetBytecodeVersion(targetBytecodeVersion)
                        .setJdk(DefaultInstalledJdk.current())
                )
        }

        // Simulates computation of the IdeaProject language level property in the IdeaPlugin
        private fun resolveRootLanguageLevel(ideaProjectExt: IdeaProjectInternal, isolatedModules: MutableList<IsolatedIdeaModuleInternal>): IdeaLanguageLevel {
            val explicitLanguageLevel = ideaProjectExt.getRawLanguageLevel()
            if (explicitLanguageLevel != null) {
                return explicitLanguageLevel
            }

            val maxCompatibility: JavaVersion = getMaxCompatibility(isolatedModules, IsolatedIdeaModuleInternal::javaSourceCompatibility)
            return IdeaLanguageLevel(maxCompatibility)
        }

        // Simulates computation of the IdeaProject target bytecode version property in the IdeaPlugin
        private fun resolveRootTargetBytecodeVersion(ideaProjectExt: IdeaProjectInternal, isolatedModules: MutableList<IsolatedIdeaModuleInternal>): JavaVersion {
            val explicitTargetBytecodeVersion = ideaProjectExt.getRawTargetBytecodeVersion()
            if (explicitTargetBytecodeVersion != null) {
                return explicitTargetBytecodeVersion
            }

            return getMaxCompatibility(isolatedModules, IsolatedIdeaModuleInternal::javaTargetCompatibility)
        }

        private fun getMaxCompatibility(isolatedIdeaModules: MutableList<IsolatedIdeaModuleInternal>, getCompatibilty: Function<IsolatedIdeaModuleInternal, JavaVersion>): JavaVersion {
            return isolatedIdeaModules.stream()
                .map<JavaVersion>(getCompatibilty)
                .filter { obj: JavaVersion? -> Objects.nonNull(obj) }
                .max(Comparator { obj: JavaVersion, o: JavaVersion -> obj.compareTo(o) })
                .orElse(IdeaModuleSupport.FALLBACK_MODULE_JAVA_COMPATIBILITY_VERSION)
        }

        private fun createParameter(offlineDependencyResolution: Boolean): IdeaModelParameter {
            return IdeaModelParameter { offlineDependencyResolution }
        }
    }
}
